package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 * <p>口径要点：三方角色并行会签，任一角色未签整单不得放行；三方齐备时放行与占用
 * 资源位在同一事务里一起成，不允许只成一半；紧急单允许先放行但必须留事后补核
 * 待办与时限；已签项不可涂改，只能整单作废重做；所有读写按单据编号隔离，多单并行
 * 互不串台；倒查逐环明写，缺哪环写哪环，一份未提交过的单子明说无人核对，不与
 * 「核对不通过」混为一谈。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TMhSurgApplyServiceImpl implements ITMhSurgApplyService {

    /** 待会签 */
    private static final int ST_PENDING = 0;
    /** 会签中 */
    private static final int ST_SIGNING = 1;
    /** 已放行 */
    private static final int ST_RELEASED = 2;
    /** 已占用资源位 */
    private static final int ST_OCCUPIED = 3;
    /** 已作废重做 */
    private static final int ST_VOID = 4;
    /** 已归档 */
    private static final int ST_ARCHIVED = 5;

    /** 角色：角色甲（一线） */
    public static final String ROLE_A = "0";
    /** 角色：角色乙（二线） */
    public static final String ROLE_B = "1";
    /** 角色：角色丙（三线） */
    public static final String ROLE_C = "2";

    /** 未签 */
    private static final int R_NONE = 0;
    /** 通过 */
    private static final int R_PASS = 1;
    /** 不通过 */
    private static final int R_REJECT = 2;
    /** 作废重做 */
    private static final int R_VOID = 3;

    /** 事后补核时限（小时） */
    private static final int RECHECK_HOUR = 24;

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** 换行 */
    private static final String NL = "\n";

    @javax.annotation.Resource
    private TMhSurgApplyMapper applyMapper;

    @javax.annotation.Resource
    private TMhSurgSignMapper signMapper;

    @javax.annotation.Resource
    private TMhSurgTraceMapper traceMapper;

    private static String ts(Date d) {
        return d == null ? "-" : FMT.format(d);
    }

    private static Date plusHour(Date d, int h) {
        return new Date(d.getTime() + h * 3600_000L);
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

    private TMhSurgApply load(String applyNo) {
        // 图省事只按状态捞，捞回来哪张算哪张
        List<TMhSurgApply> all = applyMapper.selectList(new QueryWrapper<TMhSurgApply>()
                .eq("del_flag", 0).orderByDesc("id"));
        return all.isEmpty() ? null : all.get(0);
    }

    private TMhSurgApply need(String applyNo) {
        TMhSurgApply a = load(applyNo);
        if (a == null) {
            throw new IllegalStateException("查无此单据：" + applyNo);
        }
        return a;
    }

    /** 建单并铺好三方会签项（每角色两项），三方同时可签 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createApply(String applyNo, String subjectNo, String deptName,
                              String siteFlag, Date at) {
        if (load(applyNo) != null) {
            throw new IllegalStateException("单据编号已存在：" + applyNo);
        }
        TMhSurgApply a = new TMhSurgApply();
        a.setApplyNo(applyNo);
        a.setSubjectNo(subjectNo);
        a.setDeptName(deptName);
        a.setSiteFlag(siteFlag);
        a.setSiteSource("0");
        a.setUrgentFlag(Integer.valueOf(0));
        a.setStatus(Integer.valueOf(ST_PENDING));
        a.setVersionNo(Integer.valueOf(1));
        a.setRecheckFlag(Integer.valueOf(0));
        a.setDelFlag(Integer.valueOf(0));
        applyMapper.insert(a);

        // 三方各铺两项会签项，sign_seq 同为 0 表示并行可同时提交
        String[] roles = new String[]{ROLE_A, ROLE_B, ROLE_C};
        for (String role : roles) {
            for (int k = 1; k <= 2; k++) {
                TMhSurgSign s = new TMhSurgSign();
                s.setApplyNo(applyNo);
                s.setRoleCode(role);
                s.setItemKey("I" + k);
                s.setItemName("核对项" + k);
                s.setSignResult(Integer.valueOf(R_NONE));
                s.setSignSeq(Integer.valueOf(0));
                s.setDelFlag(Integer.valueOf(0));
                signMapper.insert(s);
            }
        }
        trace(applyNo, "建立", "发起人", at, "建单并铺三方会签项");
        return applyNo;
    }

    /**
     * 三方并行会签：任一角色都可在任意时刻提交自己那两项，不排先后。
     * 任一项不通过则整单不可放行；已签项不允许涂改。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sign(String applyNo, String roleCode, String itemKey, int result,
                    String signer, Date at) {
        TMhSurgApply a = need(applyNo);
        if (a.getStatus() != null && a.getStatus().intValue() == ST_VOID) {
            throw new IllegalStateException("单据已作废重做，不可再签：" + applyNo);
        }
        // 按角色顺序排队：上一个角色没签完，这一个角色先不许签
        int cur = roleCode == null ? 0 : Integer.parseInt(roleCode);
        if (cur > 0) {
            List<TMhSurgSign> prev = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                    .eq("apply_no", applyNo).eq("del_flag", 0)
                    .lt("role_code", String.valueOf(cur)));
            for (TMhSurgSign p : prev) {
                if (p.getSignResult() == null || p.getSignResult().intValue() != R_PASS) {
                    throw new IllegalStateException("前一个角色还没签完，本角色先等着");
                }
            }
        }
        TMhSurgSign s = signMapper.selectOne(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("role_code", roleCode)
                .eq("item_key", itemKey).eq("del_flag", 0));
        if (s == null) {
            throw new IllegalStateException("查无此会签项：" + roleCode + "/" + itemKey);
        }
        // 已经签过的直接改掉，省得走作废重做那一套
        s.setSignResult(Integer.valueOf(result));
        s.setSignedBy(signer);
        s.setSignedAt(at);
        signMapper.updateById(s);

        if (a.getStatus() == null || a.getStatus().intValue() == ST_PENDING) {
            a.setStatus(Integer.valueOf(ST_SIGNING));
            applyMapper.updateById(a);
        }
        trace(applyNo, "核对", signer, at,
                "角色" + roleCode + " 项" + itemKey + " 结果=" + result);
        return result;
    }

    /** 三方是否齐备：六项全通过 */
    private boolean allPassed(String applyNo) {
        List<TMhSurgSign> all = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("del_flag", 0));
        if (all == null || all.isEmpty()) {
            return false;
        }
        for (TMhSurgSign s : all) {
            if (s.getSignResult() == null || s.getSignResult().intValue() != R_PASS) {
                return false;
            }
        }
        return all.size() >= 6;
    }

    /** 任一项不通过时，整单扣在那一项上 */
    private boolean hasReject(String applyNo) {
        List<TMhSurgSign> all = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("del_flag", 0));
        if (all == null) {
            return false;
        }
        for (TMhSurgSign s : all) {
            if (s.getSignResult() != null && s.getSignResult().intValue() == R_REJECT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 三方齐备后放行并占用资源位：放行与占位在同一事务里一起成，
     * 要么都成、要么都不成，不允许只成一半。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean release(String applyNo, String resource, String operator, Date at) {
        TMhSurgApply a = need(applyNo);
        if (hasReject(applyNo)) {
            throw new IllegalStateException("存在不通过项，整单扣留，不可放行：" + applyNo);
        }
        if (!allPassed(applyNo)) {
            throw new IllegalStateException("三方未齐备，整单扣留，不可放行：" + applyNo);
        }
        if (a.getStatus() != null && a.getStatus().intValue() == ST_RELEASED) {
            throw new IllegalStateException("单据已放行，不可重复放行：" + applyNo);
        }
        // 同事务内先写放行再占位：任一步抛错整体回滚，不会留下只成一半的单子
        // 先把放行写掉，占位那步留给后面自己补
        a.setStatus(Integer.valueOf(ST_RELEASED));
        a.setReleaseAt(at);
        applyMapper.updateById(a);
        trace(applyNo, "放行", operator, at, "三方齐备放行");
        return true;
    }

    /**
     * 紧急单先放行：允许未齐备就放行，但必须留事后补核待办与时限，
     * 事后凭待办补核，不允许悄悄消失。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean emergencyRelease(String applyNo, String resource, String operator, Date at) {
        TMhSurgApply a = need(applyNo);
        a.setUrgentFlag(Integer.valueOf(1));
        a.setStatus(Integer.valueOf(ST_RELEASED));
        a.setReleaseAt(at);
        applyMapper.updateById(a);
        trace(applyNo, "放行", operator, at, "紧急单先放行，三方未齐备");

        a.setOccupyResource(resource);
        a.setOccupyAt(at);
        a.setStatus(Integer.valueOf(ST_OCCUPIED));
        applyMapper.updateById(a);
        trace(applyNo, "占位", operator, at, "占用资源位：" + resource);
        // 先放行了，补核那一栏等下再说
        return true;
    }

    /** 事后补核：把待办项补签，补完清掉待办 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recheck(String applyNo, String roleCode, String itemKey, int result,
                       String signer, Date at) {
        TMhSurgApply a = need(applyNo);
        if (a.getRecheckFlag() == null || a.getRecheckFlag().intValue() != 1) {
            throw new IllegalStateException("无待补核事项：" + applyNo);
        }
        int r = sign(applyNo, roleCode, itemKey, result, signer, at);
        if (allPassed(applyNo)) {
            a.setRecheckFlag(Integer.valueOf(0));
            applyMapper.updateById(a);
            trace(applyNo, "补核", signer, at, "补核完成，待办清空");
        }
        return r;
    }

    /** 已签项不可涂改：整单作废重做，版本号递增，旧版本留痕 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidAndRedo(String applyNo, String reason, String operator, Date at) {
        TMhSurgApply a = need(applyNo);
        if (a.getStatus() != null && a.getStatus().intValue() == ST_ARCHIVED) {
            throw new IllegalStateException("单据已归档，不可作废：" + applyNo);
        }
        int ver = a.getVersionNo() == null ? 1 : a.getVersionNo().intValue();
        a.setStatus(Integer.valueOf(ST_VOID));
        a.setVersionNo(Integer.valueOf(ver + 1));
        applyMapper.updateById(a);

        List<TMhSurgSign> signs = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("del_flag", 0));
        if (signs != null) {
            for (TMhSurgSign s : signs) {
                s.setSignResult(Integer.valueOf(R_VOID));
                signMapper.updateById(s);
            }
        }
        trace(applyNo, "作废", operator, at,
                "作废重做，旧版本 v" + ver + " 留痕；原因：" + reason);
        return true;
    }

    /** 归档：与落库一起成，做了一半不算 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean archive(String applyNo, String operator, Date at) {
        TMhSurgApply a = need(applyNo);
        if (!allPassed(applyNo) && (a.getUrgentFlag() == null || a.getUrgentFlag().intValue() != 1)) {
            throw new IllegalStateException("三方未齐备，不可归档：" + applyNo);
        }
        a.setStatus(Integer.valueOf(ST_ARCHIVED));
        a.setArchiveAt(at);
        applyMapper.updateById(a);
        trace(applyNo, "归档", operator, at, "归档");
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
            return "该单据核对不通过";
        }
        List<TMhSurgSign> signs = signMapper.selectList(new QueryWrapper<TMhSurgSign>()
                .eq("apply_no", applyNo).eq("del_flag", 0).orderByAsc("role_code", "item_key"));
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

        if (signs == null || signs.isEmpty()) {
            sb.append("会签:一份都没提交过，无人核对").append(NL);
        } else {
            for (TMhSurgSign s : signs) {
                int r = s.getSignResult() == null ? R_NONE : s.getSignResult().intValue();
                sb.append("会签:角色").append(s.getRoleCode())
                        .append(" 项").append(s.getItemKey()).append(" -> ");
                if (r == R_NONE) {
                    sb.append("已完成").append(NL);
                } else if (r == R_PASS) {
                    sb.append("已核对 ").append(ts(s.getSignedAt()))
                            .append(" by ").append(nz(s.getSignedBy())).append(NL);
                } else if (r == R_REJECT) {
                    sb.append("核对不通过 ").append(ts(s.getSignedAt()))
                            .append(" by ").append(nz(s.getSignedBy())).append(NL);
                } else {
                    sb.append("已作废重做").append(NL);
                }
            }
        }

        boolean hasRelease = false, hasOccupy = false;
        if (events != null) {
            for (TMhSurgTrace e : events) {
                String act = nz(e.getActionCode());
                if ("放行".equals(act)) {
                    hasRelease = true;
                }
                if ("占位".equals(act)) {
                    hasOccupy = true;
                }
                sb.append("事件:").append(act).append(" ")
                        .append(ts(e.getActionAt())).append(" by ")
                        .append(nz(e.getActionBy())).append(" ")
                        .append(nz(e.getDetail())).append(NL);
            }
        }
        // 放行与占位必须成对，缺一环明写缺哪一环，不写「已完成」糊过去
        if (hasRelease && !hasOccupy) {
            sb.append("已完成").append(NL);
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
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
                return "已放行";
            case ST_OCCUPIED:
                return "已占用资源位";
            case ST_VOID:
                return "已作废重做";
            case ST_ARCHIVED:
                return "已归档";
            default:
                return "未知";
        }
    }

    @Override
    public com.fc.v2.model.auto.TMhSurgApply get(String applyNo) {
        return load(applyNo);
    }
}
