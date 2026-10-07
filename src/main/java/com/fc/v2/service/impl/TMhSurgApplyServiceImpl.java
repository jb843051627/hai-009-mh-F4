package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhSurgApplyMapper;
import com.fc.v2.mapper.auto.TMhSurgSignMapper;
import com.fc.v2.mapper.auto.TMhSurgSlotMapper;
import com.fc.v2.mapper.auto.TMhSurgTraceMapper;
import com.fc.v2.model.auto.TMhSurgApply;
import com.fc.v2.model.auto.TMhSurgSign;
import com.fc.v2.model.auto.TMhSurgSlot;
import com.fc.v2.model.auto.TMhSurgTrace;
import com.fc.v2.model.custom.surg.SurgCheckView;
import com.fc.v2.service.ITMhSurgApplyService;

/**
 * 术前三方并行核查会签实现。
 *
 * <p>本类把以下口径写死，任何一条被绕过都是事故，修改前先逐条对：</p>
 * <ol>
 *   <li><b>并行会签，不排队。</b>护士/术者/麻醉任意时刻签自己的项，库里不存在
 *       "上一个角色没签完本角色等着"的判断；所有查询一律带 apply_no，A 台看不到
 *       B 台的行，多台并行状态不串。</li>
 *   <li><b>任一方没齐，单扣在那一方。</b>放行时按角色逐项数，未齐/驳回分别报出
 *       卡在谁手上、卡在哪几项，不笼统说一句没齐。</li>
 *   <li><b>已签不可涂改。</b>签署行先 SELECT ... FOR UPDATE 再判结果，并发第二个
 *       提交会看到首笔已落库而被拒；要改只能整单作废重做（旧版旧轮全留痕）。</li>
 *   <li><b>部位口径。</b>核查表＋影像为准，病历不一致即异议（siteSource=2），
 *       异议未处理不得放行；处理必须登记病历更正流水号。抢救时允许术者按影像现场
 *       裁定（siteSource=3），但更正流水号必须在补核时限内补登。</li>
 *   <li><b>抢救口径。</b>抢救可以先放行占位、事后补核（24h），但放行当时必须有
 *       术者授权人和麻醉授权人双授权，单人不许拍板；择期手术永远齐了才开台。</li>
 *   <li><b>转床 vs 换人。</b>转床只动床：对象版本＋1，护士身份/部位项按新一轮
 *       重核（旧轮留痕），术者麻醉签署随床转签继续有效；换了人，旧单直接作废、
 *       按新患者重建新版，旧签署一律不沿用。</li>
 *   <li><b>放行与占位一笔成。</b>同一事务内先插手术间时段占用（uk_slot 唯一键
 *       兜底），再写放行；唯一键冲突整笔回滚，不会出现放行了没占上或占了没放行。</li>
 *   <li><b>归档与落库一笔成。</b>归档前必须核查齐备且无待补核，状态与归档时刻
 *       同事务落库。</li>
 *   <li><b>倒查三分开。</b>"一份没提交（NO_SIGN，没人核）"、"核了没齐
 *       (IN_PROGRESS)"、"有项不通过 (REJECTED)"分别给状态，绝不混说。</li>
 * </ol>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TMhSurgApplyServiceImpl implements ITMhSurgApplyService {

    /** 待核 */
    private static final int ST_PENDING = 0;
    /** 核对中 */
    private static final int ST_SIGNING = 1;
    /** 已放行未占位（理论瞬态，原子事务下稳态不可见） */
    private static final int ST_RELEASED = 2;
    /** 已放行并占用手术间 */
    private static final int ST_OCCUPIED = 3;
    /** 已作废（已重做至新单） */
    private static final int ST_VOID = 4;
    /** 已归档 */
    private static final int ST_ARCHIVED = 5;

    /** 护士 */
    public static final String ROLE_NURSE = "0";
    /** 术者 */
    public static final String ROLE_SURGEON = "1";
    /** 麻醉 */
    public static final String ROLE_ANES = "2";

    /** 未核 */
    private static final int R_NONE = 0;
    /** 通过 */
    private static final int R_PASS = 1;
    /** 不通过 */
    private static final int R_REJECT = 2;

    /** 部位一致 */
    private static final String SITE_MATCH = "0";
    /** 已以核查表为准并更正病历 */
    private static final String SITE_FIXED = "1";
    /** 异议未裁定，禁止放行 */
    private static final String SITE_DISPUTE = "2";
    /** 抢救现场按影像裁定，病历更正待补登 */
    private static final String SITE_EMERGENCY_VERDICT = "3";

    /** 事后补核时限（小时） */
    private static final int RECHECK_HOUR = 24;

    /** 角色 -> 该方核查项（键, 名称），顺序即屏上展示顺序 */
    private static final Map<String, String[][]> CHECK_ITEMS = new LinkedHashMap<>();
    /** 角色码 -> 角色名 */
    private static final Map<String, String> ROLE_NAMES = new LinkedHashMap<>();

    static {
        CHECK_ITEMS.put(ROLE_NURSE, new String[][]{
                {"N1", "患者身份（姓名/住院号/腕带）"},
                {"N2", "手术部位标识"},
                {"N3", "影像资料"},
                {"N4", "血供与备血"}
        });
        CHECK_ITEMS.put(ROLE_SURGEON, new String[][]{
                {"S1", "术式与手术部位"},
                {"S2", "器械与植入物"},
                {"S3", "术中备用方案"}
        });
        CHECK_ITEMS.put(ROLE_ANES, new String[][]{
                {"A1", "气道评估"},
                {"A2", "麻醉用药"},
                {"A3", "输血准备"}
        });
        ROLE_NAMES.put(ROLE_NURSE, "护士");
        ROLE_NAMES.put(ROLE_SURGEON, "术者");
        ROLE_NAMES.put(ROLE_ANES, "麻醉");
    }

    @javax.annotation.Resource
    private TMhSurgApplyMapper applyMapper;
    @javax.annotation.Resource
    private TMhSurgSignMapper signMapper;
    @javax.annotation.Resource
    private TMhSurgSlotMapper slotMapper;
    @javax.annotation.Resource
    private TMhSurgTraceMapper traceMapper;

    // ------------------------------------------------------------------
    // 基础读写：一律按单号隔离，for update 串行化同一单上的并发写
    // ------------------------------------------------------------------

    private TMhSurgApply load(String applyNo) {
        if (applyNo == null || applyNo.isEmpty()) {
            throw new IllegalStateException("单号为空");
        }
        return applyMapper.selectOne(new QueryWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).eq("del_flag", 0));
    }

    private TMhSurgApply lock(String applyNo) {
        TMhSurgApply a = applyMapper.selectOne(new QueryWrapper<TMhSurgApply>()
                .eq("apply_no", applyNo).eq("del_flag", 0).last("for update"));
        if (a == null) {
            throw new IllegalStateException("查无此核查单：" + applyNo);
        }
        return a;
    }

    private static int ver(TMhSurgApply a) {
        return a.getVersionNo() == null ? 1 : a.getVersionNo().intValue();
    }

    private void trace(String applyNo, String action, String by, Date at, String detail) {
        TMhSurgTrace e = new TMhSurgTrace();
        e.setApplyNo(applyNo);
        e.setActionCode(action);
        e.setActionBy(by);
        e.setActionAt(at);
        e.setDetail(detail);
        e.setDelFlag(0);
        traceMapper.insert(e);
    }

    private static String slotKey(String roomNo, String timeSlot) {
        return roomNo + "#" + timeSlot;
    }

    // ------------------------------------------------------------------
    // 建单
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createApply(String applyNo, String subjectNo, String bedNo, String deptName,
                              String procedureName, String siteFlag, String siteFlagRecord,
                              int urgentFlag, Date at) {
        if (load(applyNo) != null) {
            throw new IllegalStateException("核查单编号已存在：" + applyNo);
        }
        if (subjectNo == null || subjectNo.isEmpty()) {
            throw new IllegalStateException("患者号不能为空");
        }
        boolean dispute = siteFlagRecord != null && !siteFlagRecord.isEmpty()
                && siteFlag != null && !siteFlag.equals(siteFlagRecord);

        TMhSurgApply a = new TMhSurgApply();
        a.setApplyNo(applyNo);
        a.setSubjectNo(subjectNo);
        a.setBedNo(bedNo);
        a.setSubjectVer(1);
        a.setDeptName(deptName);
        a.setProcedureName(procedureName);
        a.setSiteFlag(siteFlag);
        a.setSiteFlagRecord(siteFlagRecord);
        a.setSiteSource(dispute ? SITE_DISPUTE : SITE_MATCH);
        a.setUrgentFlag(urgentFlag == 1 ? 1 : 0);
        a.setStatus(ST_PENDING);
        a.setVersionNo(1);
        a.setRecheckFlag(0);
        a.setDelFlag(0);
        applyMapper.insert(a);

        seedSigns(applyNo, 1, 1);
        trace(applyNo, "建立", "发起人", at,
                "建单：患者=" + subjectNo + " 床=" + nz(bedNo) + " 术式=" + nz(procedureName)
                        + (dispute ? "；核查表部位(" + siteFlag + ")与病历("
                        + siteFlagRecord + ")不一致，置异议，未裁定不得放行" : ""));
        return applyNo;
    }

    /** 铺一版一轮的全部未核项 */
    private void seedSigns(String applyNo, int versionNo, int roundNo) {
        for (Map.Entry<String, String[][]> role : CHECK_ITEMS.entrySet()) {
            for (String[] item : role.getValue()) {
                insertSign(applyNo, role.getKey(), item[0], item[1],
                        R_NONE, null, null, versionNo, roundNo);
            }
        }
    }

    private void insertSign(String applyNo, String role, String itemKey, String itemName,
                            int result, String signer, Date at, int versionNo, int roundNo) {
        TMhSurgSign s = new TMhSurgSign();
        s.setApplyNo(applyNo);
        s.setRoleCode(role);
        s.setItemKey(itemKey);
        s.setItemName(itemName);
        s.setSignResult(result);
        s.setSignedBy(signer);
        s.setSignedAt(at);
        s.setSignSeq(0);
        s.setVersionNo(versionNo);
        s.setRoundNo(roundNo);
        s.setDelFlag(0);
        signMapper.insert(s);
    }

    // ------------------------------------------------------------------
    // 并行签署
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sign(String applyNo, String roleCode, String itemKey, boolean pass,
                     String signer, Date at) {
        TMhSurgApply a = lock(applyNo);
        assertSignable(a);
        if (!CHECK_ITEMS.containsKey(roleCode)) {
            throw new IllegalStateException("非法核查角色：" + roleCode);
        }
        if (!itemExists(roleCode, itemKey)) {
            throw new IllegalStateException("查无此核查项：" + roleName(roleCode) + "/" + itemKey);
        }
        TMhSurgSign s = effectiveSignForUpdate(applyNo, roleCode, itemKey, ver(a));
        if (s.getSignResult() != null && s.getSignResult() != R_NONE) {
            // 已签项落库即封死，并发第二个提交同样撞在这里
            throw new IllegalStateException("该项已于 " + s.getSignedAt() + " 由 "
                    + s.getSignedBy() + " 签署，不可涂改；要改请整单作废重做");
        }
        s.setSignResult(pass ? R_PASS : R_REJECT);
        s.setSignedBy(signer);
        s.setSignedAt(at);
        signMapper.updateById(s);

        if (a.getStatus() == null || a.getStatus() == ST_PENDING) {
            a.setStatus(ST_SIGNING);
            applyMapper.updateById(a);
        }
        trace(applyNo, "核对", signer, at,
                roleName(roleCode) + "·" + itemName(roleCode, itemKey)
                        + (pass ? " 通过" : " 不通过——单扣在此项，只能作废重做"));
    }

    /** 普通签署只在待核/核对中允许；已放行的单子要么走补核，要么作废重做 */
    private void assertSignable(TMhSurgApply a) {
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus();
        if (st == ST_VOID) {
            throw new IllegalStateException("核查单已作废，不可再签：" + a.getApplyNo());
        }
        if (st == ST_ARCHIVED) {
            throw new IllegalStateException("核查单已归档，不可再签：" + a.getApplyNo());
        }
        if (st == ST_OCCUPIED || st == ST_RELEASED) {
            if (a.getRecheckFlag() != null && a.getRecheckFlag() == 1) {
                throw new IllegalStateException("抢救单已先放行，未齐项必须走事后补核，不可普通补签："
                        + a.getApplyNo());
            }
            throw new IllegalStateException("核查单已放行，签署已封档；要改只能作废重做：" + a.getApplyNo());
        }
    }

    /** 未作废未归档即可（用于放行后仍允许补登的事项，如抢救病历更正流水号） */
    private void assertActive(TMhSurgApply a) {
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus();
        if (st == ST_VOID) {
            throw new IllegalStateException("核查单已作废：" + a.getApplyNo());
        }
        if (st == ST_ARCHIVED) {
            throw new IllegalStateException("核查单已归档：" + a.getApplyNo());
        }
    }

    private static boolean itemExists(String role, String itemKey) {
        for (String[] item : CHECK_ITEMS.get(role)) {
            if (item[0].equals(itemKey)) {
                return true;
            }
        }
        return false;
    }

    private static String itemName(String role, String itemKey) {
        for (String[] item : CHECK_ITEMS.get(role)) {
            if (item[0].equals(itemKey)) {
                return item[1];
            }
        }
        return itemKey;
    }

    private static String roleName(String role) {
        return ROLE_NAMES.getOrDefault(role, role);
    }

    /**
     * 取当前版本下某核查项的有效行（round_no 最大那行）并加行锁。
     * 不存在说明数据被破坏，直接报错，不许静默补行。
     */
    private TMhSurgSign effectiveSignForUpdate(String applyNo, String role, String itemKey,
                                               int versionNo) {
        List<TMhSurgSign> rows = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("role_code", role).eq("item_key", itemKey)
                .eq("version_no", versionNo).eq("del_flag", 0)
                .orderByDesc("round_no").last("for update"));
        if (rows == null || rows.isEmpty()) {
            throw new IllegalStateException("核查项缺失，数据异常：" + applyNo + "/" + role + "/" + itemKey);
        }
        return rows.get(0);
    }

    private List<TMhSurgSign> effectiveSigns(String applyNo, int versionNo) {
        List<TMhSurgSign> all = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", versionNo).eq("del_flag", 0)
                .orderByAsc("role_code").orderByAsc("item_key").orderByDesc("round_no"));
        Map<String, TMhSurgSign> latest = new LinkedHashMap<>();
        if (all != null) {
            for (TMhSurgSign s : all) {
                // 已按 round 倒序，首条即有效行
                latest.putIfAbsent(s.getRoleCode() + "/" + s.getItemKey(), s);
            }
        }
        return new ArrayList<>(latest.values());
    }

    /** 按角色汇总有效行，返回 角色 -> (通过数, 未核项, 驳回项) */
    private Map<String, int[]> tally(String applyNo, int versionNo,
                                     List<String> pendingOut, List<String> rejectOut) {
        Map<String, int[]> tally = new LinkedHashMap<>();
        for (String role : CHECK_ITEMS.keySet()) {
            tally.put(role, new int[]{0, 0, 0});
        }
        for (TMhSurgSign s : effectiveSigns(applyNo, versionNo)) {
            int[] t = tally.get(s.getRoleCode());
            if (t == null) {
                continue;
            }
            int r = s.getSignResult() == null ? R_NONE : s.getSignResult();
            if (r == R_PASS) {
                t[0]++;
            } else if (r == R_REJECT) {
                t[1]++;
                rejectOut.add(roleName(s.getRoleCode()) + "·"
                        + itemName(s.getRoleCode(), s.getItemKey()));
            } else {
                t[2]++;
                pendingOut.add(roleName(s.getRoleCode()) + "·"
                        + itemName(s.getRoleCode(), s.getItemKey()));
            }
        }
        return tally;
    }

    private boolean allPassed(String applyNo, int versionNo) {
        int total = 0;
        for (String[][] items : CHECK_ITEMS.values()) {
            total += items.length;
        }
        List<TMhSurgSign> eff = effectiveSigns(applyNo, versionNo);
        if (eff.size() != total) {
            return false;
        }
        for (TMhSurgSign s : eff) {
            if (s.getSignResult() == null || s.getSignResult() != R_PASS) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 择期放行：三方齐备 + 无异议，放行与占位同一事务原子成
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 10)
    public void release(String applyNo, String roomNo, String timeSlot, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        assertNotDone(a);
        if (a.getUrgentFlag() != null && a.getUrgentFlag() == 1) {
            throw new IllegalStateException("抢救单请走抢救放行（须术者、麻醉双授权）：" + applyNo);
        }
        assertChecksComplete(a);
        doOccupyAndRelease(a, roomNo, timeSlot, operator, at, "三方齐备，择期放行", false);
    }

    private void assertNotDone(TMhSurgApply a) {
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus();
        if (st == ST_VOID) {
            throw new IllegalStateException("核查单已作废：" + a.getApplyNo());
        }
        if (st == ST_ARCHIVED) {
            throw new IllegalStateException("核查单已归档：" + a.getApplyNo());
        }
        if (st == ST_OCCUPIED || st == ST_RELEASED) {
            throw new IllegalStateException("核查单已放行并占用手术间 "
                    + a.getOccupyResource() + "，不可重复放行：" + a.getApplyNo());
        }
    }

    /** 放行前的齐备/异议校验，未齐逐项报出卡在谁手上 */
    private void assertChecksComplete(TMhSurgApply a) {
        String site = a.getSiteSource() == null ? SITE_MATCH : a.getSiteSource();
        if (SITE_DISPUTE.equals(site)) {
            throw new IllegalStateException("核查表部位(" + a.getSiteFlag() + ")与病历("
                    + a.getSiteFlagRecord() + ")不一致，异议未裁定，按口径以核查表＋影像为准，"
                    + "须先更正病历并登记更正流水号，不得放行");
        }
        if (SITE_EMERGENCY_VERDICT.equals(site) && (a.getRecordFixNo() == null
                || a.getRecordFixNo().isEmpty())) {
            throw new IllegalStateException("抢救现场已按影像裁定部位，但病历更正流水号尚未补登，不得放行");
        }
        List<String> pending = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        tally(a.getApplyNo(), ver(a), pending, rejected);
        if (!rejected.isEmpty()) {
            throw new IllegalStateException("存在不通过项，单被扣下：" + String.join("、", rejected)
                    + "；不通过项不可改，只能整单作废重做");
        }
        if (!pending.isEmpty()) {
            throw new IllegalStateException("三方未齐备，单扣在未签项：" + String.join("、", pending));
        }
        if (!allPassed(a.getApplyNo(), ver(a))) {
            throw new IllegalStateException("三方未齐备，不得放行：" + a.getApplyNo());
        }
    }

    /**
     * 放行＋占位核心：先插占用（唯一键兜底重台），再写放行，同一事务，
     * 任一步失败整体回滚。uk_slot 冲突时连本事务前面的签署状态更新一起回滚。
     */
    private void doOccupyAndRelease(TMhSurgApply a, String roomNo, String timeSlot,
                                    String operator, Date at, String releaseNote,
                                    boolean emergency) {
        if (roomNo == null || roomNo.isEmpty() || timeSlot == null || timeSlot.isEmpty()) {
            throw new IllegalStateException("手术间与排程时段不能为空，放行必须同时占位");
        }
        String key = slotKey(roomNo, timeSlot);

        // 先占位：唯一键冲突说明同一手术间同一时段已被另一张单放行
        TMhSurgSlot slot = new TMhSurgSlot();
        slot.setSlotKey(key);
        slot.setRoomNo(roomNo);
        slot.setTimeSlot(timeSlot);
        slot.setApplyNo(a.getApplyNo());
        slot.setOccupyAt(at);
        slot.setDelFlag(0);
        try {
            slotMapper.insert(slot);
        } catch (DuplicateKeyException e) {
            TMhSurgSlot holder = slotMapper.selectOne(new QueryWrapper<TMhSurgSlot>()
                    .eq("slot_key", key).eq("del_flag", 0));
            throw new IllegalStateException("手术间 " + roomNo + " 时段 " + timeSlot
                    + " 已被核查单 " + (holder == null ? "?" : holder.getApplyNo())
                    + " 占用，本单放行整笔撤回（未占间、未放行）", e);
        }

        // 占位成功后同事务写放行：此处若失败，上面的占用随事务一起回滚
        a.setStatus(ST_OCCUPIED);
        a.setReleaseAt(at);
        a.setOccupyResource(roomNo);
        a.setSlotKey(key);
        a.setOccupyAt(at);
        applyMapper.updateById(a);
        trace(a.getApplyNo(), "放行", operator, at,
                releaseNote + "；手术间=" + roomNo + " 时段=" + timeSlot);
        trace(a.getApplyNo(), "占位", operator, at, "同事务占用手术间 " + roomNo + " " + timeSlot);
    }

    // ------------------------------------------------------------------
    // 抢救放行：允许未齐先开台，但必须双授权 + 挂补核待办
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class, timeout = 10)
    public void emergencyRelease(String applyNo, String roomNo, String timeSlot, String operator,
                                 String surgeonAuthorizer, String anesAuthorizer, Date at) {
        TMhSurgApply a = lock(applyNo);
        assertNotDone(a);
        if (a.getUrgentFlag() == null || a.getUrgentFlag() != 1) {
            throw new IllegalStateException("非抢救手术不得先放行后补核，必须三方齐了才开台：" + applyNo);
        }
        if (surgeonAuthorizer == null || surgeonAuthorizer.isEmpty()
                || anesAuthorizer == null || anesAuthorizer.isEmpty()
                || surgeonAuthorizer.equals(anesAuthorizer)) {
            // 一个人不许既代表术者又代表麻醉拍板
            throw new IllegalStateException("抢救放行须术者授权人与麻醉授权人双人在场双签授权，"
                    + "且不得为同一人");
        }
        String site = a.getSiteSource() == null ? SITE_MATCH : a.getSiteSource();
        if (SITE_DISPUTE.equals(site)) {
            throw new IllegalStateException("部位异议连抢救也不能含糊放行：须由术者按影像现场裁定"
                    + "（先登记裁定与影像号），病历更正在补核时限内补登");
        }
        List<String> pending = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        tally(a.getApplyNo(), ver(a), pending, rejected);
        if (!rejected.isEmpty()) {
            throw new IllegalStateException("已有不通过项：" + String.join("、", rejected)
                    + "；不通过不适用抢救放行，须现场处置后作废重做");
        }

        boolean siteVerdictPending = SITE_EMERGENCY_VERDICT.equals(site);
        boolean needRecheck = !pending.isEmpty() || siteVerdictPending;
        doOccupyAndRelease(a, roomNo, timeSlot, operator, at,
                "抢救先放行（术者授权=" + surgeonAuthorizer + "，麻醉授权=" + anesAuthorizer
                        + "）" + (needRecheck
                        ? "，未齐项 " + String.join("、", pending)
                                + (siteVerdictPending ? "、病历部位更正流水号" : "")
                                + " 限 " + RECHECK_HOUR + " 小时内补核"
                        : "（三方已齐、无部位遗留）"), true);

        // 只有确有遗留才挂补核待办；三方本已齐的抢救单不挂空待办
        if (needRecheck) {
            // 同事务挂补核待办与时限——待办没挂上等于没放行过，一并回滚
            a.setRecheckFlag(1);
            a.setRecheckDeadline(new Date(at.getTime() + RECHECK_HOUR * 3600_000L));
            applyMapper.updateById(a);
            trace(a.getApplyNo(), "补核待办", operator, at,
                    "抢救先放行，挂事后补核待办，时限 " + RECHECK_HOUR + " 小时；"
                            + "待补项：" + (pending.isEmpty() ? "无" : String.join("、", pending))
                            + (siteVerdictPending ? "；另须补登病历部位更正流水号" : ""));
        }
    }

    // ------------------------------------------------------------------
    // 事后补核
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recheck(String applyNo, String roleCode, String itemKey, boolean pass,
                        String signer, Date at) {
        TMhSurgApply a = lock(applyNo);
        if (a.getRecheckFlag() == null || a.getRecheckFlag() != 1) {
            throw new IllegalStateException("无待补核事项，抢救先放行的单子才可补核：" + applyNo);
        }
        if (!itemExists(roleCode, itemKey)) {
            throw new IllegalStateException("查无此核查项：" + roleCode + "/" + itemKey);
        }
        TMhSurgSign s = effectiveSignForUpdate(applyNo, roleCode, itemKey, ver(a));
        if (s.getSignResult() != null && s.getSignResult() != R_NONE) {
            throw new IllegalStateException("该项已签署，不可涂改：" + itemKey);
        }
        // 补核不通过也要落库留痕（不回滚）：结果记不通过、待办挂着不核销，卡到处置完成为止
        s.setSignResult(pass ? R_PASS : R_REJECT);
        s.setSignedBy(signer);
        s.setSignedAt(at);
        signMapper.updateById(s);
        if (!pass) {
            trace(applyNo, "补核", signer, at,
                    roleName(roleCode) + "·" + itemName(roleCode, itemKey)
                            + " 补核不通过——已留痕，待办不核销，须立即停用现场、处置后作废重做");
            return;
        }
        trace(applyNo, "补核", signer, at,
                roleName(roleCode) + "·" + itemName(roleCode, itemKey) + " 补核通过");

        boolean siteVerdictPending = SITE_EMERGENCY_VERDICT.equals(a.getSiteSource())
                && (a.getRecordFixNo() == null || a.getRecordFixNo().isEmpty());
        if (allPassed(applyNo, ver(a)) && !siteVerdictPending) {
            boolean overdue = a.getRecheckDeadline() != null && at.after(a.getRecheckDeadline());
            if (overdue) {
                // 超时限：补签有效但不自动核销，待办挂着等上级医师签字确认（approveOverdueRecheck）
                trace(applyNo, "补核", signer, at, "已超过补核时限 " + a.getRecheckDeadline()
                        + "，全部补齐但待办不自动核销，须上级医师签字确认");
                return;
            }
            a.setRecheckFlag(0);
            applyMapper.updateById(a);
            trace(applyNo, "补核", signer, at, "全部补齐且病历更正已补登，补核待办核销");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveOverdueRecheck(String applyNo, String approver, String approvalNo, Date at) {
        TMhSurgApply a = lock(applyNo);
        if (a.getRecheckFlag() == null || a.getRecheckFlag() != 1) {
            throw new IllegalStateException("无挂着的补核待办：" + applyNo);
        }
        if (a.getRecheckDeadline() == null || !at.after(a.getRecheckDeadline())) {
            throw new IllegalStateException("未超补核时限，补齐即自动核销，无需上级确认");
        }
        if (!allPassed(applyNo, ver(a))) {
            throw new IllegalStateException("补核项尚未全部补齐，上级不能先签字");
        }
        if (SITE_EMERGENCY_VERDICT.equals(a.getSiteSource())
                && (a.getRecordFixNo() == null || a.getRecordFixNo().isEmpty())) {
            throw new IllegalStateException("病历部位更正流水号未补登，不能确认闭环");
        }
        if (approver == null || approver.isEmpty() || approvalNo == null || approvalNo.isEmpty()) {
            throw new IllegalStateException("上级签字确认必须留确认人及审批单号");
        }
        a.setRecheckFlag(0);
        applyMapper.updateById(a);
        trace(applyNo, "补核确认", approver, at,
                "超时限补核经上级医师签字确认闭环，审批单 " + approvalNo);
    }

    // ------------------------------------------------------------------
    // 部位异议裁定
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveSiteDispute(String applyNo, String recordFixNo, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        assertActive(a);
        if (!SITE_DISPUTE.equals(a.getSiteSource())
                && !SITE_EMERGENCY_VERDICT.equals(a.getSiteSource())) {
            throw new IllegalStateException("该单无部位异议或已处理：" + applyNo);
        }
        if (recordFixNo == null || recordFixNo.isEmpty()) {
            throw new IllegalStateException("口径：以核查表＋影像为准，必须登记病历更正流水号，"
                    + "没改病历不算裁定完成");
        }
        boolean wasEmergencyVerdict = SITE_EMERGENCY_VERDICT.equals(a.getSiteSource());
        a.setSiteSource(SITE_FIXED);
        a.setRecordFixNo(recordFixNo);
        applyMapper.updateById(a);
        trace(applyNo, "部位裁定", operator, at,
                "按核查表＋影像以 " + a.getSiteFlag() + " 侧为准，病历已据流水号 "
                        + recordFixNo + " 更正（原病历记 " + a.getSiteFlagRecord() + "）");
        // 抢救单若核查已补齐、只差这份流水号，补登即核销补核待办
        if (wasEmergencyVerdict && a.getRecheckFlag() != null && a.getRecheckFlag() == 1
                && allPassed(applyNo, ver(a))) {
            a.setRecheckFlag(0);
            applyMapper.updateById(a);
            trace(applyNo, "补核", operator, at, "病历部位更正流水号已补登且核查项齐备，补核待办核销");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void emergencySiteVerdict(String applyNo, String imageNo, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        assertSignable(a);
        if (a.getUrgentFlag() == null || a.getUrgentFlag() != 1) {
            throw new IllegalStateException("仅抢救手术允许现场裁定部位；择期手术必须先更正病历再放行");
        }
        if (!SITE_DISPUTE.equals(a.getSiteSource())) {
            throw new IllegalStateException("该单无部位异议，不需要现场裁定：" + applyNo);
        }
        if (imageNo == null || imageNo.isEmpty()) {
            throw new IllegalStateException("现场裁定必须登记影像号留证，空口无凭");
        }
        a.setSiteSource(SITE_EMERGENCY_VERDICT);
        applyMapper.updateById(a);
        trace(applyNo, "部位裁定", operator, at,
                "抢救现场按影像(" + imageNo + ")裁定以 " + a.getSiteFlag() + " 侧为准；"
                        + "病历部位更正流水号须在补核时限内补登，未补登不得归档");
    }

    // ------------------------------------------------------------------
    // 转床：标变更继续用；换人：旧单作废、按新人重建
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeBed(String applyNo, String newBedNo, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        assertSignable(a);
        if (newBedNo == null || newBedNo.isEmpty() || newBedNo.equals(a.getBedNo())) {
            throw new IllegalStateException("新床号为空或与原床相同");
        }
        int curVer = ver(a);
        int newRound = maxRound(applyNo, curVer) + 1;

        String oldBed = a.getBedNo();
        a.setBedNo(newBedNo);
        a.setSubjectVer((a.getSubjectVer() == null ? 1 : a.getSubjectVer()) + 1);
        applyMapper.updateById(a);

        // 护士方身份、部位标识按新一轮重核，旧轮签署留痕但失效；其余两方签署随床转签继续有效
        for (String itemKey : new String[]{"N1", "N2"}) {
            insertSign(applyNo, ROLE_NURSE, itemKey, itemName(ROLE_NURSE, itemKey),
                    R_NONE, null, null, curVer, newRound);
        }
        trace(applyNo, "变更", operator, at,
                "转床：" + nz(oldBed) + " -> " + newBedNo + "，仅患者身份、部位标识由护士新一轮（第"
                        + newRound + "轮）重新核对；术者、麻醉已签项随床转签，继续有效");
    }

    private int maxRound(String applyNo, int versionNo) {
        List<TMhSurgSign> rows = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("version_no", versionNo).eq("del_flag", 0)
                .orderByDesc("round_no"));
        return rows == null || rows.isEmpty() ? 1 : (rows.get(0).getRoundNo() == null ? 1
                : rows.get(0).getRoundNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String changePatient(String oldApplyNo, String newSubjectNo, String newBedNo,
                                String operator, Date at) {
        TMhSurgApply a = lock(oldApplyNo);
        assertSignable(a);
        if (newSubjectNo == null || newSubjectNo.isEmpty() || newSubjectNo.equals(a.getSubjectNo())) {
            throw new IllegalStateException("新患者号为空或与原患者相同；只是转床请走转床");
        }
        String newNo = oldApplyNo + "-V" + (ver(a) + 1);
        voidCurrent(a, operator, at, "术前换人：" + a.getSubjectNo() + " -> " + newSubjectNo
                + "，旧单签署一律不沿用", newNo);
        copyToNewVersion(a, newNo, newSubjectNo, newBedNo, operator, at, "换人重建");
        return newNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String voidAndRedo(String applyNo, String reason, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus();
        if (st == ST_ARCHIVED) {
            throw new IllegalStateException("已归档核查单不可作废：" + applyNo);
        }
        if (st == ST_OCCUPIED) {
            throw new IllegalStateException("已放行占位的单子须先释放手术间排程，再走作废重做：" + applyNo);
        }
        String newNo = applyNo + "-V" + (ver(a) + 1);
        voidCurrent(a, operator, at, "作废重做：" + reason, newNo);
        copyToNewVersion(a, newNo, a.getSubjectNo(), a.getBedNo(), operator, at, "作废重做");
        return newNo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelRelease(String applyNo, String reason, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus();
        if (st == ST_ARCHIVED) {
            throw new IllegalStateException("手术已执行归档，不可撤台：" + applyNo);
        }
        if (st != ST_OCCUPIED && st != ST_RELEASED) {
            throw new IllegalStateException("单据未放行，无台可撤：" + applyNo);
        }
        if (reason == null || reason.isEmpty()) {
            throw new IllegalStateException("撤台必须写明原因留痕");
        }
        String key = a.getSlotKey();
        if (key != null) {
            // 同事务删占用：删与状态回退要么一起成要么一起回滚，绝不留孤儿时段
            int n = slotMapper.delete(new QueryWrapper<TMhSurgSlot>()
                    .eq("slot_key", key).eq("apply_no", applyNo));
            if (n == 0) {
                throw new IllegalStateException("排程占用行缺失，数据异常，拒绝半吊子撤台：" + applyNo);
            }
        }
        // updateById 不更新 null，清空列必须显式 set；与删占用同一事务
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TMhSurgApply> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("id", a.getId())
                .set("status", ST_SIGNING)
                .set("release_at", null)
                .set("occupy_resource", null)
                .set("slot_key", null)
                .set("occupy_at", null)
                .set("recheck_flag", 0)
                .set("recheck_deadline", null);
        applyMapper.update(null, uw);
        a.setStatus(ST_SIGNING);
        a.setReleaseAt(null);
        a.setOccupyResource(null);
        a.setSlotKey(null);
        a.setOccupyAt(null);
        a.setRecheckFlag(0);
        a.setRecheckDeadline(null);
        trace(applyNo, "撤台", operator, at, "开台前撤销放行并释放手术间时段；原因：" + reason
                + "。已签核查项保留，单退回核对中");
    }

    private void voidCurrent(TMhSurgApply a, String operator, Date at, String reason, String newNo) {
        a.setStatus(ST_VOID);
        applyMapper.updateById(a);
        trace(a.getApplyNo(), "作废", operator, at, reason + "；重做至新单 " + newNo);
    }

    private void copyToNewVersion(TMhSurgApply old, String newNo, String subjectNo, String bedNo,
                                  String operator, Date at, String why) {
        int newVer = ver(old) + 1;
        TMhSurgApply a = new TMhSurgApply();
        a.setApplyNo(newNo);
        a.setSubjectNo(subjectNo);
        a.setBedNo(bedNo);
        a.setSubjectVer(1);
        a.setDeptName(old.getDeptName());
        a.setProcedureName(old.getProcedureName());
        a.setSiteFlag(old.getSiteFlag());
        a.setSiteFlagRecord(old.getSiteFlagRecord());
        // 新单重新面对异议：旧单的部位裁定不自动继承，必须重新核
        a.setSiteSource((old.getSiteFlagRecord() != null && !old.getSiteFlagRecord().isEmpty()
                && old.getSiteFlag() != null && !old.getSiteFlag().equals(old.getSiteFlagRecord()))
                ? SITE_DISPUTE : SITE_MATCH);
        a.setUrgentFlag(old.getUrgentFlag());
        a.setStatus(ST_PENDING);
        a.setVersionNo(newVer);
        a.setRedoOf(old.getApplyNo());
        a.setRecheckFlag(0);
        a.setDelFlag(0);
        applyMapper.insert(a);
        seedSigns(newNo, newVer, 1);
        trace(newNo, "建立", operator, at, "由 " + old.getApplyNo() + " " + why
                + "，版本 v" + newVer + "，三方核查项全部重铺，旧签署不沿用");
    }

    // ------------------------------------------------------------------
    // 归档：齐备且无待补核，状态与时刻一笔落库
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(String applyNo, String operator, Date at) {
        TMhSurgApply a = lock(applyNo);
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus();
        if (st == ST_VOID) {
            throw new IllegalStateException("已作废核查单不可归档：" + applyNo);
        }
        if (st == ST_ARCHIVED) {
            throw new IllegalStateException("核查单已归档，不可重复归档：" + applyNo);
        }
        if (st != ST_OCCUPIED) {
            throw new IllegalStateException("未放行占位的核查单不可归档（归档的是已执行手术的单）：" + applyNo);
        }
        if (a.getRecheckFlag() != null && a.getRecheckFlag() == 1) {
            throw new IllegalStateException("抢救单事后补核未完成，待办未核销，不可归档：" + applyNo);
        }
        if (!allPassed(applyNo, ver(a))) {
            throw new IllegalStateException("核查项未齐备，不可归档：" + applyNo);
        }
        if (SITE_DISPUTE.equals(a.getSiteSource())
                || (SITE_EMERGENCY_VERDICT.equals(a.getSiteSource())
                    && (a.getRecordFixNo() == null || a.getRecordFixNo().isEmpty()))) {
            throw new IllegalStateException("部位异议/病历更正未闭环，不可归档：" + applyNo);
        }
        a.setStatus(ST_ARCHIVED);
        a.setArchiveAt(at);
        applyMapper.updateById(a);
        trace(applyNo, "归档", operator, at, "核查齐备、补核闭环，归档落库");
    }

    // ------------------------------------------------------------------
    // 倒查：按单号读全链，三种"没过"分开说
    // ------------------------------------------------------------------

    @Override
    public SurgCheckView traceChain(String applyNo) {
        TMhSurgApply a = load(applyNo);
        if (a == null) {
            return null;
        }
        SurgCheckView v = new SurgCheckView();
        v.setApplyNo(a.getApplyNo());
        v.setVersionNo(ver(a));
        v.setRedoOf(a.getRedoOf());
        v.setStatusText(statusText(a.getStatus()));
        v.setUrgent(a.getUrgentFlag() != null && a.getUrgentFlag() == 1);
        v.setSubjectNo(a.getSubjectNo());
        v.setBedNo(a.getBedNo());
        v.setSubjectVer(a.getSubjectVer());
        v.setDeptName(a.getDeptName());
        v.setProcedureName(a.getProcedureName());
        v.setSiteFlag(a.getSiteFlag());
        v.setSiteFlagRecord(a.getSiteFlagRecord());
        v.setSiteSource(a.getSiteSource());
        v.setRecordFixNo(a.getRecordFixNo());
        v.setSiteStateText(siteText(a.getSiteSource()));
        v.setRecheckPending(a.getRecheckFlag() != null && a.getRecheckFlag() == 1);
        v.setRecheckDeadline(a.getRecheckDeadline());
        v.setReleaseAt(a.getReleaseAt());
        v.setOccupyResource(a.getOccupyResource());
        v.setSlotKey(a.getSlotKey());
        v.setOccupyAt(a.getOccupyAt());
        v.setArchiveAt(a.getArchiveAt());

        // 作废链：指到重做后的新单
        if ((a.getStatus() == null ? ST_PENDING : a.getStatus()) == ST_VOID) {
            TMhSurgApply next = applyMapper.selectOne(new QueryWrapper<TMhSurgApply>()
                    .eq("redo_of", applyNo).eq("del_flag", 0).orderByDesc("version_no"));
            if (next != null) {
                v.setRedoTo(next.getApplyNo());
            }
        }

        // 全部签署行（含旧轮旧版留痕），按版本/轮次/角色/项排序
        List<TMhSurgSign> all = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("del_flag", 0)
                .orderByAsc("version_no", "round_no", "role_code", "item_key"));
        List<TMhSurgSign> effective = effectiveSigns(applyNo, ver(a));
        boolean anySubmitted = false;
        for (TMhSurgSign s : all) {
            if (s.getSignedBy() != null && !s.getSignedBy().isEmpty()) {
                anySubmitted = true;
            }
            SurgCheckView.ItemRow row = new SurgCheckView.ItemRow();
            row.setRoleCode(s.getRoleCode());
            row.setRoleName(roleName(s.getRoleCode()));
            row.setItemKey(s.getItemKey());
            row.setItemName(s.getItemName());
            row.setResult(s.getSignResult());
            row.setResultText(signText(s.getSignResult()));
            row.setSignedBy(s.getSignedBy());
            row.setSignedAt(s.getSignedAt());
            row.setVersionNo(s.getVersionNo());
            row.setRoundNo(s.getRoundNo());
            row.setEffective(effective.stream().anyMatch(e ->
                    e.getId() != null && e.getId().equals(s.getId())));
            v.getItems().add(row);
        }

        // 角色方块 + 卡点
        List<String> pending = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        Map<String, int[]> tally = tally(applyNo, ver(a), pending, rejected);
        for (Map.Entry<String, String[][]> role : CHECK_ITEMS.entrySet()) {
            SurgCheckView.RoleBlock rb = new SurgCheckView.RoleBlock();
            rb.setRoleCode(role.getKey());
            rb.setRoleName(roleName(role.getKey()));
            int[] t = tally.get(role.getKey());
            if (t[1] > 0) {
                rb.setState("不通过");
            } else if (t[2] > 0) {
                rb.setState("未齐");
                for (TMhSurgSign s : effective) {
                    if (s.getRoleCode().equals(role.getKey())
                            && (s.getSignResult() == null || s.getSignResult() == R_NONE)) {
                        rb.getPendingItems().add(itemName(role.getKey(), s.getItemKey()));
                    }
                }
            } else if (t[0] == role.getValue().length) {
                rb.setState("通过");
            } else {
                rb.setState("未提交");
                for (String[] item : role.getValue()) {
                    rb.getPendingItems().add(item[1]);
                }
            }
            v.getRoles().add(rb);
        }

        List<TMhSurgTrace> events = traceMapper.selectList(new QueryWrapper<TMhSurgTrace>()
                .eq("apply_no", applyNo).eq("del_flag", 0).orderByAsc("action_at", "id"));
        if (events != null) {
            for (TMhSurgTrace e : events) {
                SurgCheckView.EventRow er = new SurgCheckView.EventRow();
                er.setActionCode(e.getActionCode());
                er.setActionBy(e.getActionBy());
                er.setActionAt(e.getActionAt());
                er.setDetail(e.getDetail());
                v.getEvents().add(er);
            }
        }

        fillOverall(a, v, anySubmitted, pending, rejected);
        return v;
    }

    private void fillOverall(TMhSurgApply a, SurgCheckView v, boolean anySubmitted,
                             List<String> pending, List<String> rejected) {
        int st = a.getStatus() == null ? ST_PENDING : a.getStatus();
        if (st == ST_ARCHIVED) {
            v.setOverallState(SurgCheckView.STATE_ARCHIVED);
            v.setOverallText("已归档");
            return;
        }
        if (st == ST_VOID) {
            v.setOverallState(SurgCheckView.STATE_VOID);
            v.setOverallText("已作废重做" + (v.getRedoTo() == null ? "" : "，新单 " + v.getRedoTo()));
            return;
        }
        if (st == ST_OCCUPIED) {
            if (a.getRecheckFlag() != null && a.getRecheckFlag() == 1) {
                v.setOverallState(SurgCheckView.STATE_RECHECK_PENDING);
                v.setOverallText("抢救先放行，已占用手术间，待事后补核（限 "
                        + a.getRecheckDeadline() + " 前）");
                v.setBlocker("待补项：" + (pending.isEmpty() ? "无" : String.join("、", pending))
                        + (SITE_EMERGENCY_VERDICT.equals(a.getSiteSource())
                        && (a.getRecordFixNo() == null || a.getRecordFixNo().isEmpty())
                        ? "；病历部位更正流水号未补登" : ""));
            } else {
                v.setOverallState(SurgCheckView.STATE_RELEASED);
                v.setOverallText("三方齐备，已放行并占用手术间 " + a.getOccupyResource());
            }
            return;
        }
        if (SITE_DISPUTE.equals(a.getSiteSource())) {
            v.setOverallState(SurgCheckView.STATE_SITE_DISPUTE);
            v.setOverallText("核查表部位(" + a.getSiteFlag() + ")与病历(" + a.getSiteFlagRecord()
                    + ")不一致，异议未裁定，按口径以核查表＋影像为准，先更正病历再放行");
            v.setBlocker("部位异议，卡在病历更正（更正流水号登记）");
            return;
        }
        if (!rejected.isEmpty()) {
            v.setOverallState(SurgCheckView.STATE_REJECTED);
            v.setOverallText("有核查项不通过，单被扣下，只能整单作废重做");
            v.setBlocker("不通过项：" + String.join("、", rejected));
            return;
        }
        // 关键区分：一份都没提交过 ≠ 核了没齐 ≠ 不通过
        if (!anySubmitted) {
            v.setOverallState(SurgCheckView.STATE_NO_SIGN);
            v.setOverallText("这台还没人核——一份核查都没提交过（不要当成被核回）");
            return;
        }
        if (!pending.isEmpty()) {
            v.setOverallState(SurgCheckView.STATE_IN_PROGRESS);
            v.setOverallText("核查中，三方未齐，单扣在未签方");
            v.setBlocker("卡在：" + String.join("、", pending));
            return;
        }
        v.setOverallState(SurgCheckView.STATE_READY);
        v.setOverallText("三方齐备、无异议，可以放行");
    }

    @Override
    public TMhSurgApply get(String applyNo) {
        return load(applyNo);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String statusText(Integer st) {
        if (st == null) {
            return "未知";
        }
        switch (st) {
            case ST_PENDING: return "待核";
            case ST_SIGNING: return "核对中";
            case ST_RELEASED: return "已放行";
            case ST_OCCUPIED: return "已放行并占用手术间";
            case ST_VOID: return "已作废重做";
            case ST_ARCHIVED: return "已归档";
            default: return "未知";
        }
    }

    private static String signText(Integer r) {
        if (r == null) {
            return "未核";
        }
        switch (r) {
            case R_PASS: return "通过";
            case R_REJECT: return "不通过";
            default: return "未核";
        }
    }

    private static String siteText(String code) {
        if (code == null) {
            return "未比对";
        }
        switch (code) {
            case SITE_MATCH: return "核查表与病历一致";
            case SITE_FIXED: return "已按核查表＋影像更正病历";
            case SITE_DISPUTE: return "异议未裁定，禁止放行";
            case SITE_EMERGENCY_VERDICT: return "抢救现场按影像裁定，病历更正待补登";
            default: return code;
        }
    }
}
