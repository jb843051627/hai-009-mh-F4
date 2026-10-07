package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TMhSurgApplyMapper;
import com.fc.v2.mapper.auto.TMhSurgSignMapper;
import com.fc.v2.mapper.auto.TMhSurgTraceMapper;
import com.fc.v2.model.auto.TMhSurgApply;
import com.fc.v2.model.auto.TMhSurgSign;
import com.fc.v2.model.auto.TMhSurgTrace;
import com.fc.v2.service.ITMhSurgApplyService;

/**
 * 三方并行会签与放行占用 Service 业务层处理（sign-release 形状）。
 *
 * <p>口径要点（说死版，详见 doc/surg-signoff-policy.md）：三方角色并行会签，互不排队，
 * 任一角色未签整单不得放行；三方齐备时放行与占用资源位在同一事务里一起成，不允许只成一半；
 * 紧急单允许先放行，但必须登记决策人与理由、留事后补核待办与 24 小时时限，已落定不通过项
 * 或部位异议未处置的单紧急放行同样拦住；已签项不可涂改，只能整单作废重做（版本号递增，
 * 旧版软删留痕）；部位异议以更正后的病历档案为准；换人一律作废重做，转床标变更继续用；
 * 所有读写按单据编号隔离，状态迁移走条件更新，多单并行互不串台；倒查逐环明写，缺哪环写
 * 哪环，一份未提交过的单子明说无人核对，不与「核对不通过」混为一谈。</p>
 *
 * @author fuce
 * @date 2026-10-07
 */
@Service
public class TMhSurgApplyServiceImpl implements ITMhSurgApplyService {

    /** 待会签 */
    private static final int ST_PENDING = 0;
    /** 会签中 */
    private static final int ST_SIGNING = 1;
    /** 已放行（过渡态：放行与占位同事务完成，正常不停留此态；仅历史数据可见） */
    private static final int ST_RELEASED = 2;
    /** 已占用资源位 */
    private static final int ST_OCCUPIED = 3;
    /** 已作废（终态：取消不重做；「作废重做」在同一事务内重铺后回到待会签） */
    private static final int ST_VOID = 4;
    /** 已归档 */
    private static final int ST_ARCHIVED = 5;

    /** 角色：护理 */
    public static final String ROLE_NURSE = "0";
    /** 角色：术者 */
    public static final String ROLE_SURGEON = "1";
    /** 角色：麻醉 */
    public static final String ROLE_ANESTHESIA = "2";

    /** 未核 */
    private static final int R_NONE = 0;
    /** 通过 */
    private static final int R_PASS = 1;
    /** 不通过 */
    private static final int R_REJECT = 2;
    /** 作废重做 */
    private static final int R_VOID = 3;

    /** 部位取自：核查单 */
    private static final String SITE_SRC_APPLY = "0";
    /** 部位取自：病历档案（异议处置后） */
    private static final String SITE_SRC_RECORD = "1";
    /** 部位有异议（未处置，冻结放行） */
    private static final String SITE_SRC_DISPUTE = "2";

    /** 事后补核时限（小时） */
    private static final int RECHECK_HOUR = 24;

    /** 换行 */
    private static final String NL = "\n";

    /** 三方核对项目录：角色 -> 有序 [项键, 项名]；建单与作废重做时按此铺项 */
    private static final Map<String, List<String[]>> ITEM_CATALOG = new LinkedHashMap<>();

    static {
        List<String[]> nurse = new ArrayList<>();
        nurse.add(new String[]{"N_IDENTITY", "患者身份"});
        nurse.add(new String[]{"N_SITE_MARK", "部位标识"});
        nurse.add(new String[]{"N_IMAGING", "影像资料"});
        nurse.add(new String[]{"N_BLOOD", "血供准备"});
        ITEM_CATALOG.put(ROLE_NURSE, nurse);

        List<String[]> surgeon = new ArrayList<>();
        surgeon.add(new String[]{"S_PROCEDURE", "术式确认"});
        surgeon.add(new String[]{"S_SITE", "部位确认"});
        surgeon.add(new String[]{"S_INSTRUMENT", "器械准备"});
        surgeon.add(new String[]{"S_BACKUP", "备用方案"});
        ITEM_CATALOG.put(ROLE_SURGEON, surgeon);

        List<String[]> anesthesia = new ArrayList<>();
        anesthesia.add(new String[]{"A_AIRWAY", "气道评估"});
        anesthesia.add(new String[]{"A_MEDICATION", "用药核对"});
        anesthesia.add(new String[]{"A_TRANSFUSION", "输血准备"});
        ITEM_CATALOG.put(ROLE_ANESTHESIA, anesthesia);
    }

    @javax.annotation.Resource
    private TMhSurgApplyMapper applyMapper;

    @javax.annotation.Resource
    private TMhSurgSignMapper signMapper;

    @javax.annotation.Resource
    private TMhSurgTraceMapper traceMapper;

    private static String ts(Date d) {
        // SimpleDateFormat 非线程安全，多单并行倒查时每次新建，不共享实例
        return d == null ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    private static Date plusHour(Date d, int h) {
        return new Date(d.getTime() + h * 3600_000L);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Date orNow(Date at) {
        return at == null ? new Date() : at;
    }

    private void trace(String applyNo, String action, String by, Date at, String detail) {
        TMhSurgTrace e = new TMhSurgTrace();
        e.setApplyNo(applyNo);
        e.setActionCode(action);
        e.setActionBy(by);
        e.setActionAt(at);
        e.setDetail(detail);
        e.setDelFlag(Integer.valueOf(0));
        traceMapper.insert(e);
    }

    /** 按单据编号精确加载：多单并行时各管各的，绝不串台 */
    private TMhSurgApply load(String applyNo) {
        return applyMapper.selectOne(new QueryWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).eq("del_flag", 0));
    }

    /**
     * 写路径统一入口：对本单据主表行加行锁（FOR UPDATE），把同单的并发写串行化。
     * 不同单据锁不同行，多台手术并行互不阻塞；同单上「放行」与「新落不通过项」
     * 这类检查-更新竞态在锁内不可能交错，从根上杜绝带不通过项放行。
     */
    private TMhSurgApply lock(String applyNo) {
        TMhSurgApply a = applyMapper.selectOne(new QueryWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).eq("del_flag", 0).last("FOR UPDATE"));
        if (a == null) {
            throw new IllegalStateException("查无此单据：" + applyNo);
        }
        return a;
    }

    private static int verOf(TMhSurgApply a) {
        return a.getVersionNo() == null ? 1 : a.getVersionNo().intValue();
    }

    /** 按目录铺指定版本的会签项，sign_seq 同为 0：三方并行，互不排队 */
    private void insertItems(String applyNo, int versionNo) {
        for (Map.Entry<String, List<String[]>> e : ITEM_CATALOG.entrySet()) {
            for (String[] item : e.getValue()) {
                TMhSurgSign s = new TMhSurgSign();
                s.setApplyNo(applyNo);
                s.setRoleCode(e.getKey());
                s.setItemKey(item[0]);
                s.setItemName(item[1]);
                s.setSignResult(Integer.valueOf(R_NONE));
                s.setSignSeq(Integer.valueOf(0));
                s.setVersionNo(Integer.valueOf(versionNo));
                s.setDelFlag(Integer.valueOf(0));
                signMapper.insert(s);
            }
        }
    }

    /** 当前版本中结果等于指定值的会签项（version_no 随主表，del_flag=0 即当前版） */
    private List<TMhSurgSign> itemsOf(String applyNo, int versionNo, int result) {
        List<TMhSurgSign> list = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", versionNo)
                .eq("sign_result", result).eq("del_flag", 0)
                .orderByAsc("role_code", "item_key"));
        return list == null ? new ArrayList<TMhSurgSign>() : list;
    }

    /** 当前版本全部会签项 */
    private List<TMhSurgSign> currentItems(String applyNo, int versionNo) {
        List<TMhSurgSign> list = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", versionNo).eq("del_flag", 0)
                .orderByAsc("role_code", "item_key"));
        return list == null ? new ArrayList<TMhSurgSign>() : list;
    }

    /** 当前版本已落定（通过或不通过）的会签项数 */
    private int countSigned(String applyNo, int versionNo) {
        Integer c = signMapper.selectCount(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", versionNo).eq("del_flag", 0)
                .in("sign_result", R_PASS, R_REJECT));
        return c == null ? 0 : c.intValue();
    }

    private static String brief(List<TMhSurgSign> items) {
        List<String> parts = new ArrayList<>();
        for (TMhSurgSign s : items) {
            parts.add(roleName(s.getRoleCode()) + "/" + s.getItemKey() + " " + nz(s.getItemName()));
        }
        return String.join("；", parts);
    }

    /** 建单并铺好三方会签项（护理4/术者4/麻醉3），三方同时可签 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createApply(String applyNo, String subjectNo, String deptName,
                              String siteFlag, Date at) {
        if (isBlank(applyNo) || isBlank(subjectNo) || isBlank(siteFlag)) {
            throw new IllegalArgumentException("单据编号、患者编号、部位标识均不能为空");
        }
        // 编号唯一（含已软删的历史单：唯一键不区分删除标记）
        TMhSurgApply dup = applyMapper.selectOne(new QueryWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo));
        if (dup != null) {
            throw new IllegalStateException("单据编号已存在：" + applyNo);
        }
        TMhSurgApply a = new TMhSurgApply();
        a.setApplyNo(applyNo);
        a.setSubjectNo(subjectNo);
        a.setDeptName(deptName);
        a.setSiteFlag(siteFlag);
        a.setSiteSource(SITE_SRC_APPLY);
        a.setUrgentFlag(Integer.valueOf(0));
        a.setStatus(Integer.valueOf(ST_PENDING));
        a.setVersionNo(Integer.valueOf(1));
        a.setRecheckFlag(Integer.valueOf(0));
        a.setDelFlag(Integer.valueOf(0));
        applyMapper.insert(a);

        insertItems(applyNo, 1);
        trace(applyNo, "建立", "发起人", orNow(at), "建单并铺三方会签项（护理4/术者4/麻醉3）v1");
        return applyNo;
    }

    /**
     * 三方并行会签：任一角色都可在任意时刻提交自己那几项，不排先后。
     * 任一项不通过则整单扣留不可放行；已签项不允许涂改，要改只能整单作废重做。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sign(String applyNo, String roleCode, String itemKey, int result,
                    String signer, Date at) {
        // 行锁串行化同单写：与放行/作废/异议处置等不会交错，杜绝带不通过项放行
        TMhSurgApply a = lock(applyNo);
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus().intValue();
        if (st == ST_RELEASED || st == ST_OCCUPIED) {
            throw new IllegalStateException("单据已放行，未签项只能走事后补核：" + applyNo);
        }
        if (st == ST_VOID || st == ST_ARCHIVED) {
            throw new IllegalStateException("单据已作废/已归档，不可再签：" + applyNo);
        }
        Date now = orNow(at);
        int r = doSignItem(applyNo, verOf(a), roleCode, itemKey, result, signer, now);
        // 首签把单据推进到「会签中」；锁内首签只发生一次
        applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).eq("status", ST_PENDING).eq("del_flag", 0)
                .set("status", ST_SIGNING).set("update_by", signer).set("update_time", now));
        return r;
    }

    /** 落一项签署：条件更新带上「未核+版本」前提，已落定的一律拒绝涂改，并发下只有一笔能成 */
    private int doSignItem(String applyNo, int versionNo, String roleCode, String itemKey,
                           int result, String signer, Date at) {
        if (result != R_PASS && result != R_REJECT) {
            throw new IllegalArgumentException("核对结果只能是 1通过 或 2不通过：" + result);
        }
        if (!ITEM_CATALOG.containsKey(roleCode)) {
            throw new IllegalArgumentException("查无此核对角色：" + roleCode);
        }
        if (isBlank(signer)) {
            throw new IllegalArgumentException("核对人不能为空，签署必须留痕到人");
        }
        TMhSurgSign s = signMapper.selectOne(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", versionNo)
                .eq("role_code", roleCode).eq("item_key", itemKey).eq("del_flag", 0));
        if (s == null) {
            throw new IllegalStateException("查无此会签项：v" + versionNo + " " + roleCode + "/" + itemKey);
        }
        int rows = signMapper.update(null, new UpdateWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", versionNo)
                .eq("role_code", roleCode).eq("item_key", itemKey)
                .eq("sign_result", R_NONE).eq("del_flag", 0)
                .set("sign_result", result).set("signed_by", signer).set("signed_at", at)
                .set("update_by", signer).set("update_time", at));
        if (rows == 0) {
            throw new IllegalStateException(
                    "该项已核对落定，不可涂改；如需变更请整单作废重做：v" + versionNo
                            + " " + roleCode + "/" + itemKey);
        }
        trace(applyNo, "核对", signer, at,
                "v" + versionNo + " " + roleName(roleCode) + " " + itemKey + " " + nz(s.getItemName())
                        + " 结果=" + (result == R_PASS ? "通过" : "不通过"));
        return result;
    }

    /**
     * 三方齐备后放行并占用资源位：放行与占位在同一事务里一起成，
     * 要么都成、要么都不成，不允许只成一半。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean release(String applyNo, String resource, String operator, Date at) {
        if (isBlank(resource)) {
            throw new IllegalArgumentException("占用资源位（手术间/台次）不能为空");
        }
        // 行锁内完成「查项—判齐—放行+占位」，与并发达成的不通过项不可能交错
        TMhSurgApply a = lock(applyNo);
        int ver = verOf(a);
        int preSt = a.getStatus() == null ? ST_PENDING : a.getStatus().intValue();
        if (preSt == ST_OCCUPIED || preSt == ST_ARCHIVED) {
            throw new IllegalStateException("单据已放行并占用资源位，不可重复放行：" + applyNo);
        }
        if (preSt == ST_VOID) {
            throw new IllegalStateException("单据已作废，不可放行：" + applyNo);
        }
        if (SITE_SRC_DISPUTE.equals(a.getSiteSource())) {
            throw new IllegalStateException("部位异议未处置，冻结放行：" + applyNo);
        }
        Date now = orNow(at);
        if (preSt == ST_RELEASED) {
            // 历史遗留：放行早已发生，此处只补占位这一环，放行时刻不动，
            // 不再回头校验当时的签署齐备性；但不允许从此态直接归档
            int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                    .eq("apply_no", applyNo).eq("status", ST_RELEASED).eq("del_flag", 0)
                    .and(w -> w.isNull("site_source").or().ne("site_source", SITE_SRC_DISPUTE))
                    .set("status", ST_OCCUPIED).set("occupy_resource", resource)
                    .set("occupy_at", now).set("update_by", operator).set("update_time", now));
            if (rows == 0) {
                throw new IllegalStateException("单据状态已变化，请刷新后重试：" + applyNo);
            }
            trace(applyNo, "占位", operator, now, "补齐占位环节，占用资源位：" + resource);
            return true;
        }
        List<TMhSurgSign> rejected = itemsOf(applyNo, ver, R_REJECT);
        if (!rejected.isEmpty()) {
            throw new IllegalStateException("存在不通过项（" + brief(rejected)
                    + "），整单扣留，不可放行：" + applyNo);
        }
        List<TMhSurgSign> missing = itemsOf(applyNo, ver, R_NONE);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("三方未齐备（待签：" + brief(missing)
                    + "），整单扣留，不可放行：" + applyNo);
        }
        // 条件更新兜底：只有「待会签/会签中」能放行，锁外的并发状态改动仍会被挡下
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING).eq("del_flag", 0)
                .and(w -> w.isNull("site_source").or().ne("site_source", SITE_SRC_DISPUTE))
                .set("status", ST_OCCUPIED).set("release_at", now)
                .set("occupy_resource", resource).set("occupy_at", now)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("单据状态已变化（可能已被并发放行或作废），请刷新后重试：" + applyNo);
        }
        trace(applyNo, "放行", operator, now, "三方齐备放行");
        trace(applyNo, "占位", operator, now, "占用资源位：" + resource);
        return true;
    }

    /**
     * 紧急单先放行：允许未齐备就放行，但必须登记决策人与理由，
     * 留事后补核待办与 24 小时时限；已落定不通过项或部位异议未处置的，紧急也拦住。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean emergencyRelease(String applyNo, String resource, String reason,
                                    String operator, Date at) {
        if (isBlank(reason)) {
            throw new IllegalArgumentException("紧急放行必须填写决策理由（谁定的、为什么），留痕到人");
        }
        if (isBlank(resource)) {
            throw new IllegalArgumentException("占用资源位（手术间/台次）不能为空");
        }
        // 行锁：紧急放行与并发达成的不通过项/异议处置不交错
        TMhSurgApply a = lock(applyNo);
        int ver = verOf(a);
        int preSt = a.getStatus() == null ? ST_PENDING : a.getStatus().intValue();
        if (preSt == ST_OCCUPIED || preSt == ST_ARCHIVED) {
            throw new IllegalStateException("单据已放行并占用资源位，不可再紧急放行：" + applyNo);
        }
        if (preSt == ST_VOID) {
            throw new IllegalStateException("单据已作废，不可紧急放行：" + applyNo);
        }
        if (SITE_SRC_DISPUTE.equals(a.getSiteSource())) {
            throw new IllegalStateException("部位异议未处置，紧急放行同样冻结：" + applyNo);
        }
        List<TMhSurgSign> rejected = itemsOf(applyNo, ver, R_REJECT);
        if (!rejected.isEmpty()) {
            throw new IllegalStateException("存在已落定的不通过项（" + brief(rejected)
                    + "），紧急放行不能盖过，须先处置（作废重做）：" + applyNo);
        }
        // 先放行、事后补核：放行+占位+补核待办同一事务一起成
        Date now = orNow(at);
        Date deadline = plusHour(now, RECHECK_HOUR);
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING).eq("del_flag", 0)
                .and(w -> w.isNull("site_source").or().ne("site_source", SITE_SRC_DISPUTE))
                .set("urgent_flag", 1).set("status", ST_OCCUPIED)
                .set("release_at", now).set("occupy_resource", resource).set("occupy_at", now)
                .set("recheck_flag", 1).set("recheck_deadline", deadline)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("单据状态已变化（可能已被并发放行或作废），请刷新后重试：" + applyNo);
        }
        trace(applyNo, "放行", operator, now,
                "紧急先放行，三方未齐备；决策人：" + operator + "；理由：" + reason);
        trace(applyNo, "占位", operator, now, "占用资源位：" + resource);
        trace(applyNo, "补核", operator, now, "登记事后补核待办，时限 " + ts(deadline));
        return true;
    }

    /** 事后补核：把待办项补签，三方补齐（或不通过定性）后清掉待办 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recheck(String applyNo, String roleCode, String itemKey, int result,
                       String signer, Date at) {
        TMhSurgApply a = lock(applyNo);
        if (a.getRecheckFlag() == null || a.getRecheckFlag().intValue() != 1) {
            throw new IllegalStateException("无待补核事项：" + applyNo);
        }
        Date now = orNow(at);
        boolean late = a.getRecheckDeadline() != null && now.after(a.getRecheckDeadline());
        int r = doSignItem(applyNo, verOf(a), roleCode, itemKey, result, signer, now);
        if (late) {
            trace(applyNo, "补核", signer, now,
                    "该笔补核已超时（时限 " + ts(a.getRecheckDeadline()) + "）");
        }
        int ver = verOf(a);
        if (r == R_REJECT) {
            // 补核不通过：定性不良事件，待办清零，如实留痕后走归档封存
            clearRecheck(applyNo, signer, now);
            trace(applyNo, "补核", signer, now, "补核不通过，定性不良事件留痕，待办清零，按封存流程归档");
        } else if (itemsOf(applyNo, ver, R_NONE).isEmpty() && itemsOf(applyNo, ver, R_REJECT).isEmpty()) {
            clearRecheck(applyNo, signer, now);
            trace(applyNo, "补核", signer, now, "补核完成，三方齐备，待办清零");
        }
        return r;
    }

    private void clearRecheck(String applyNo, String operator, Date at) {
        applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).eq("recheck_flag", 1).eq("del_flag", 0)
                .set("recheck_flag", 0).set("update_by", operator).set("update_time", at));
    }

    /** 已签项不可涂改：整单作废重做，版本号递增，旧版本软删留痕 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidAndRedo(String applyNo, String reason, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        int ver = verOf(a);
        Date now = orNow(at);
        // 已占位（已开台）/已归档不可作废；条件更新挡住并发，后到请求直接失败
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING, ST_RELEASED)
                .eq("del_flag", 0)
                .set("status", ST_PENDING).set("version_no", ver + 1)
                .set("release_at", null).set("occupy_resource", null).set("occupy_at", null)
                .set("recheck_flag", 0).set("recheck_deadline", null)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("当前状态不可作废（已开台或已归档）：" + applyNo);
        }
        redoItems(applyNo, ver, ver + 1, operator, now);
        trace(applyNo, "作废", operator, now,
                "作废重做，旧版本 v" + ver + " 留痕，新版本 v" + (ver + 1)
                        + " 回到待会签；原因：" + nz(reason));
        return true;
    }

    /** 取消不重做：单据进终态「已作废」，留底可归档，已开台（已占位）的不可取消 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelApply(String applyNo, String reason, String operator, Date at) {
        lock(applyNo);
        Date now = orNow(at);
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING, ST_RELEASED)
                .eq("del_flag", 0)
                .set("status", ST_VOID)
                .set("release_at", null).set("occupy_resource", null).set("occupy_at", null)
                .set("recheck_flag", 0).set("recheck_deadline", null)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("当前状态不可取消（已开台或已归档）：" + applyNo);
        }
        trace(applyNo, "作废", operator, now, "取消不重做，单据终态作废留底；原因：" + nz(reason));
        return true;
    }

    /** 作废重做的落库部分：旧版条目软删留痕（原核对结果不动，库里还能倒查），按目录重铺新版本 */
    private void redoItems(String applyNo, int oldVersionNo, int newVersionNo,
                           String operator, Date at) {
        signMapper.update(null, new UpdateWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", oldVersionNo).eq("del_flag", 0)
                .set("del_flag", 1)
                .set("update_by", operator).set("update_time", at));
        insertItems(applyNo, newVersionNo);
    }

    /** 部位异议：核查单与病历档案不一致，标异议冻结放行，待临床处置 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reportSiteDispute(String applyNo, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        Date now = orNow(at);
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING).eq("del_flag", 0)
                .set("site_source", SITE_SRC_DISPUTE)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("当前状态不可标异议（已放行或已归档）：" + applyNo);
        }
        trace(applyNo, "异议", operator, now,
                "核查单部位（" + nz(a.getSiteFlag()) + "）与病历档案不一致，标异议冻结放行，待临床处置");
        return true;
    }

    /** 部位异议处置：以更正后的病历档案为准；已有签署的，已签项全部作废、整单重做 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resolveSiteDispute(String applyNo, String correctedSiteFlag,
                                      String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        if (!SITE_SRC_DISPUTE.equals(a.getSiteSource())) {
            throw new IllegalStateException("该单无在办部位异议：" + applyNo);
        }
        if (isBlank(correctedSiteFlag)) {
            throw new IllegalArgumentException("更正后的部位标识不能为空");
        }
        int ver = verOf(a);
        boolean signed = countSigned(applyNo, ver) > 0;
        Date now = orNow(at);
        UpdateWrapper<TMhSurgApply> uw = new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING).eq("del_flag", 0)
                .set("site_flag", correctedSiteFlag).set("site_source", SITE_SRC_RECORD)
                .set("update_by", operator).set("update_time", now);
        if (signed) {
            // 部位是关键字段：已有签署的，已签项全部作废，整单重做
            uw.set("status", ST_PENDING).set("version_no", ver + 1)
                    .set("release_at", null).set("occupy_resource", null).set("occupy_at", null)
                    .set("recheck_flag", 0).set("recheck_deadline", null);
        }
        int rows = applyMapper.update(null, uw);
        if (rows == 0) {
            throw new IllegalStateException("单据状态已变化，请刷新后重试：" + applyNo);
        }
        if (signed) {
            redoItems(applyNo, ver, ver + 1, operator, now);
            trace(applyNo, "更正", operator, now,
                    "以病历档案为准更正部位为 " + correctedSiteFlag
                            + "；已有签署，已签项全部作废，整单重做 v" + (ver + 1));
        } else {
            trace(applyNo, "更正", operator, now,
                    "以病历档案为准更正部位为 " + correctedSiteFlag + "；尚无签署，原地更正");
        }
        return true;
    }

    /** 术前换人：患者身份变更，已签三方一律作废，整单重做 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeSubject(String applyNo, String newSubjectNo, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        if (isBlank(newSubjectNo)) {
            throw new IllegalArgumentException("新患者编号不能为空");
        }
        if (newSubjectNo.equals(a.getSubjectNo())) {
            throw new IllegalStateException("患者未变化，无需换人：" + applyNo);
        }
        int ver = verOf(a);
        Date now = orNow(at);
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING).eq("del_flag", 0)
                .set("subject_no", newSubjectNo).set("status", ST_PENDING)
                .set("version_no", ver + 1)
                .set("release_at", null).set("occupy_resource", null).set("occupy_at", null)
                .set("recheck_flag", 0).set("recheck_deadline", null)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("当前状态不可换人（已放行或已归档）：" + applyNo);
        }
        redoItems(applyNo, ver, ver + 1, operator, now);
        trace(applyNo, "变更", operator, now,
                "患者变更 " + nz(a.getSubjectNo()) + " -> " + newSubjectNo
                        + "，已签三方一律作废，整单重做 v" + (ver + 1));
        return true;
    }

    /** 术前转床/换间：患者不变，标变更继续用，已签有效，留变更事件 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markTransfer(String applyNo, String note, String operator, Date at) {
        lock(applyNo);
        Date now = orNow(at);
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_PENDING, ST_SIGNING).eq("del_flag", 0)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("当前状态不可变更（已放行或已归档）：" + applyNo);
        }
        trace(applyNo, "变更", operator, now,
                "转床/换间：" + nz(note) + "；患者不变，已签继续有效");
        return true;
    }

    /** 归档：与落库一起成，做了一半不算；补核待办未清不可归档；作废单归档留底 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean archive(String applyNo, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        if (a.getRecheckFlag() != null && a.getRecheckFlag().intValue() == 1) {
            throw new IllegalStateException("事后补核待办未清，不可归档：" + applyNo);
        }
        int st = a.getStatus() == null ? -1 : a.getStatus().intValue();
        boolean urgent = a.getUrgentFlag() != null && a.getUrgentFlag().intValue() == 1;
        if (st == ST_RELEASED) {
            // 历史遗留「已放行未占位」只允许先补齐占位，不允许跳过占位直接归档
            throw new IllegalStateException("已放行但未占资源位，先补齐占位环节再归档：" + applyNo);
        }
        if (st != ST_VOID && !urgent) {
            int ver = verOf(a);
            // 当前版本一个会签项都没有的已占位单，只可能是「系统上线前已放行」的历史遗留单
            // （现行放行强制三方齐备，不可能产生零签署单）；其电子齐备性无从校验，不再拦归档，
            // 历史事实以补齐占位时的留痕为准
            if (!currentItems(applyNo, ver).isEmpty()) {
                if (!itemsOf(applyNo, ver, R_NONE).isEmpty()) {
                    throw new IllegalStateException("三方未齐备，不可归档：" + applyNo);
                }
                if (!itemsOf(applyNo, ver, R_REJECT).isEmpty()) {
                    throw new IllegalStateException("存在不通过项，不可归档：" + applyNo);
                }
            }
        }
        Date now = orNow(at);
        int rows = applyMapper.update(null, new UpdateWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).in("status", ST_OCCUPIED, ST_VOID)
                .eq("del_flag", 0)
                .set("status", ST_ARCHIVED).set("archive_at", now)
                .set("update_by", operator).set("update_time", now));
        if (rows == 0) {
            throw new IllegalStateException("当前状态不可归档（未占位或已归档）：" + applyNo);
        }
        trace(applyNo, "归档", operator, now, "归档封存");
        return true;
    }

    /**
     * 按单据编号倒查全链；只读本单据，多单并行互不串台。
     * 一份都没提交过的单子明说无人核对，不与「核对不通过」混为一谈。
     */
    @Override
    public String traceChain(String applyNo) {
        TMhSurgApply a = load(applyNo);
        if (a == null) {
            // 查无此单 ≠ 核对不通过：明说本台还没有任何核对记录
            return "查无此单据：" + applyNo + "（尚未建单或已删除），本台尚无任何核对记录";
        }
        int ver = a.getVersionNo() == null ? 1 : a.getVersionNo().intValue();
        List<TMhSurgSign> signs = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", ver).eq("del_flag", 0)
                .orderByAsc("role_code", "item_key"));
        List<TMhSurgSign> voided = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("del_flag", 1)
                .orderByAsc("version_no", "signed_at", "id"));
        List<TMhSurgTrace> events = traceMapper.selectList(new QueryWrapper<TMhSurgTrace>()
                .eq("apply_no", applyNo).eq("del_flag", 0).orderByAsc("action_at", "id"));

        StringBuilder sb = new StringBuilder();
        sb.append("单据 ").append(applyNo)
                .append(" 版本v").append(a.getVersionNo() == null ? 1 : a.getVersionNo())
                .append(" 状态=").append(statusText(a.getStatus()));
        if (a.getUrgentFlag() != null && a.getUrgentFlag().intValue() == 1) {
            sb.append(" 紧急");
        }
        sb.append(NL);
        sb.append("患者=").append(nz(a.getSubjectNo()))
                .append(" 科室=").append(nz(a.getDeptName()))
                .append(" 术式=").append(nz(a.getProcedureName()))
                .append(" 部位=").append(nz(a.getSiteFlag()))
                .append("（取自:").append(siteSourceText(a.getSiteSource())).append("）")
                .append(NL);
        if (a.getRecheckFlag() != null && a.getRecheckFlag().intValue() == 1) {
            sb.append("事后补核待办: 时限 ").append(ts(a.getRecheckDeadline()));
            if (a.getRecheckDeadline() != null && new Date().after(a.getRecheckDeadline())) {
                sb.append("（已超时）");
            }
            sb.append(NL);
        }

        // 当前版本会签项：建单即铺 11 个未核项，故「一份都没提交过」按已落定项数=0 判定，
        // 绝不能与「核对不通过」混写
        int settled = 0;
        Map<String, List<String>> pending = new LinkedHashMap<>();
        List<String> rejectedBrief = new ArrayList<>();
        for (TMhSurgSign s : signs) {
            int r = s.getSignResult() == null ? R_NONE : s.getSignResult().intValue();
            if (r == R_PASS || r == R_REJECT) {
                settled++;
            }
            sb.append("会签: ").append(roleName(s.getRoleCode()))
                    .append(" ").append(s.getItemKey())
                    .append(" ").append(nz(s.getItemName())).append(" -> ");
            if (r == R_NONE) {
                sb.append("未核（待签）");
            } else if (r == R_PASS) {
                sb.append("已核通过 ").append(ts(s.getSignedAt()))
                        .append(" by ").append(nz(s.getSignedBy()));
            } else if (r == R_REJECT) {
                sb.append("核对不通过 ").append(ts(s.getSignedAt()))
                        .append(" by ").append(nz(s.getSignedBy()));
            } else {
                sb.append("已作废");
            }
            sb.append(NL);
            if (r == R_NONE) {
                List<String> list = pending.get(s.getRoleCode());
                if (list == null) {
                    list = new ArrayList<>();
                    pending.put(s.getRoleCode(), list);
                }
                list.add(s.getItemKey() + " " + nz(s.getItemName()));
            } else if (r == R_REJECT) {
                rejectedBrief.add(roleName(s.getRoleCode()) + "/" + s.getItemKey());
            }
        }
        if (settled == 0 && !signs.isEmpty()) {
            // 一份都没提交过：明说本台尚无人核对，不是「不通过」
            // （无电子会签项的上线前历史单不在此列，下面卡点行单独表述）
            sb.append("核对状态: 一份都没提交过，本台尚无人核对").append(NL);
        }
        // 卡在谁手上：按方汇总待签项；无电子会签项（系统上线前历史单）不臆造卡点
        if (signs.isEmpty()) {
            sb.append("卡点: 无电子会签项（系统上线前历史单据）").append(NL);
        } else if (pending.isEmpty()) {
            sb.append("卡点: 三方已齐备，无待签").append(NL);
        } else {
            StringBuilder line = new StringBuilder("卡点: 整单扣在 -> ");
            for (Map.Entry<String, List<String>> e : pending.entrySet()) {
                line.append(roleName(e.getKey())).append("[")
                        .append(String.join(", ", e.getValue())).append("] ");
            }
            sb.append(line.toString().trim()).append(NL);
        }
        if (!rejectedBrief.isEmpty()) {
            sb.append("不通过项: ").append(String.join(", ", rejectedBrief))
                    .append("（整单扣留，须作废重做）").append(NL);
        }

        // 旧版作废项留痕：按版本号标注，作废重做几轮都能一笔一笔倒着看
        if (voided != null && !voided.isEmpty()) {
            sb.append("旧版作废项留痕:").append(NL);
            for (TMhSurgSign s : voided) {
                sb.append("  旧版v").append(s.getVersionNo() == null ? "?" : s.getVersionNo())
                        .append(" ").append(roleName(s.getRoleCode()))
                        .append(" ").append(s.getItemKey())
                        .append(" ").append(nz(s.getItemName()))
                        .append(" 原结果=").append(resultText(s.getSignResult()))
                        .append(" ").append(ts(s.getSignedAt()))
                        .append(" by ").append(nz(s.getSignedBy())).append(NL);
            }
        }

        if (events != null) {
            for (TMhSurgTrace e : events) {
                sb.append("事件: ").append(nz(e.getActionCode())).append(" ")
                        .append(ts(e.getActionAt())).append(" by ")
                        .append(nz(e.getActionBy())).append(" ")
                        .append(nz(e.getDetail())).append(NL);
            }
        }
        // 放行与占位必须成对：以主表时刻字段为准（事件文本跨作废版本会残留，不能拿来判链路），
        // 缺哪环明写哪环，不写「已完成」糊过去
        boolean hasRelease = a.getReleaseAt() != null;
        boolean hasOccupy = a.getOccupyAt() != null;
        if (hasRelease && hasOccupy) {
            sb.append("链路校验: 放行+占位成对（放行 ").append(ts(a.getReleaseAt()))
                    .append(" / 占位 ").append(ts(a.getOccupyAt())).append("）").append(NL);
        } else if (hasRelease) {
            sb.append("链路校验: 缺「占位」环——已放行但未占资源位，需补齐").append(NL);
        } else if (hasOccupy) {
            sb.append("链路校验: 缺「放行」环——已占资源位但无放行记录").append(NL);
        } else {
            sb.append("链路校验: 尚未放行").append(NL);
        }
        return sb.toString();
    }

    private static String resultText(Integer r) {
        if (r == null) {
            return "未核";
        }
        switch (r.intValue()) {
            case R_PASS:
                return "通过";
            case R_REJECT:
                return "不通过";
            case R_VOID:
                return "作废重做";
            default:
                return "未核";
        }
    }

    private static String roleName(String roleCode) {
        if (ROLE_NURSE.equals(roleCode)) {
            return "护理";
        }
        if (ROLE_SURGEON.equals(roleCode)) {
            return "术者";
        }
        if (ROLE_ANESTHESIA.equals(roleCode)) {
            return "麻醉";
        }
        return "角色" + nz(roleCode);
    }

    private static String siteSourceText(String s) {
        if (SITE_SRC_APPLY.equals(s)) {
            return "核查单";
        }
        if (SITE_SRC_RECORD.equals(s)) {
            return "病历档案";
        }
        if (SITE_SRC_DISPUTE.equals(s)) {
            return "有异议-未处置";
        }
        return "未知";
    }

    private static String statusText(Integer st) {
        if (st == null) {
            return "未知";
        }
        switch (st.intValue()) {
            case ST_PENDING:
                return "待会签";
            case ST_SIGNING:
                return "会签中";
            case ST_RELEASED:
                return "已放行(待占位)";
            case ST_OCCUPIED:
                return "已占用资源位";
            case ST_VOID:
                return "已作废";
            case ST_ARCHIVED:
                return "已归档";
            default:
                return "未知";
        }
    }

    @Override
    public TMhSurgApply get(String applyNo) {
        return load(applyNo);
    }
}
