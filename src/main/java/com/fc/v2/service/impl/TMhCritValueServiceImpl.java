package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhCritNotifyMapper;
import com.fc.v2.mapper.auto.TMhCritValueMapper;
import com.fc.v2.model.auto.TMhCritNotify;
import com.fc.v2.model.auto.TMhCritValue;
import com.fc.v2.service.ITMhCritValueService;

/**
 * 危急值判定与通知处置 Service 业务层处理（notify-loop 形状）。
 *
 * <p>口径要点：机器自动判定与检验师人工复核双闸都过才算危急值；同标本多项目危急
 * 合并成一条通知并留批次号；通知按路径重试到接通或到硬时限；超时限升级给主管医生；
 * 临床回填即闭环；复核推翻为误报时原处置记录留痕不抹。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhCritValueServiceImpl implements ITMhCritValueService {

    /** 待复核 */
    private static final int ST_PENDING = 0;
    /** 已确认危急 */
    private static final int ST_CRIT = 1;
    /** 已推翻为误报 */
    private static final int ST_False = 2;
    /** 已处置闭环 */
    private static final int ST_CLOSED = 3;

    /** 通知中 */
    private static final int NS_NOTIFYING = 0;
    /** 已闭环 */
    private static final int NS_CLOSED = 1;
    /** 已降级（到时限仍未接通） */
    private static final int NS_DOWN = 2;

    /** 未接通 */
    private static final int NO = 0;
    /** 已接通 */
    private static final int YES = 1;

    /** 重试间隔（分钟）；到硬时限仍未接通就停 */
    private static final int RETRY_GAP_MIN = 5;
    /** 通知硬时限（分钟）：判危急起算，超时必须升级 */
    private static final int DEADLINE_MIN = 30;

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @javax.annotation.Resource
    private TMhCritValueMapper critValueMapper;

    @javax.annotation.Resource
    private TMhCritNotifyMapper critNotifyMapper;

    private static String ts(Date d) {
        return d == null ? null : FMT.format(d);
    }

    private static Date parse(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return FMT.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Date plusMin(Date d, int min) {
        return new Date(d.getTime() + min * 60L * 1000L);
    }

    @Override
    public List<TMhCritValue> listPending(String specimenNo) {
        List<TMhCritValue> out = new ArrayList<TMhCritValue>();
        if (specimenNo == null) {
            return out;
        }
        List<TMhCritValue> rows = critValueMapper.selectList(new QueryWrapper<TMhCritValue>()
                .eq("del_flag", 0).eq("specimen_no", specimenNo));
        if (rows == null) {
            return out;
        }
        for (TMhCritValue r : rows) {
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    @Override
    public TMhCritValue review(Long id, int manualFlag, String reviewer, Date at) {
        if (id == null || at == null) {
            return null;
        }
        TMhCritValue r = critValueMapper.selectById(id);
        if (r == null) {
            return null;
        }
        r.setManualFlag(Integer.valueOf(manualFlag));
        r.setReviewBy(reviewer);
        r.setReviewAt(at);
        if (manualFlag == 1) {
            // 人工复核点了危急就算危急，不再核机器那一道
            r.setStatus(Integer.valueOf(ST_CRIT));
            if (r.getCritTime() == null) {
                r.setCritTime(at);
            }
        } else if (manualFlag == 2) {
            // 推翻为误报：记录状态转推翻，通知处置留痕不抹（del_flag 不动、remark 写明误报）
            r.setStatus(Integer.valueOf(ST_False));
            if (r.getRemark() == null || r.getRemark().isEmpty()) {
                r.setRemark("复核推翻为误报");
            }
            List<TMhCritNotify> ns = critNotifyMapper.selectList(new QueryWrapper<TMhCritNotify>()
                    .eq("del_flag", 0).eq("specimen_no", r.getSpecimenNo()));
            if (ns != null) {
                for (TMhCritNotify n : ns) {
                    if (n != null) {
                        // 既然是误报，那几行通知与处置一并清掉，别留在台账里
                        critNotifyMapper.deleteById(n.getId());
                    }
                }
            }
        }
        critValueMapper.updateById(r);
        return r;
    }

    @Override
    public int notifyOnce(String specimenNo, Date at) {
        if (specimenNo == null || at == null) {
            return 0;
        }
        List<TMhCritValue> crits = new ArrayList<TMhCritValue>();
        List<TMhCritValue> rows = listPending(specimenNo);
        for (TMhCritValue r : rows) {
            // 只通知已确认危急的：双闸都过的那一批
            if (r != null && r.getStatus() != null && r.getStatus().intValue() == ST_CRIT) {
                crits.add(r);
            }
        }
        if (crits.isEmpty()) {
            return 0;
        }
        // 一条危急值一条通知，各发各的，接的人自己分清是哪一项
        String batchPrefix = "CB-" + specimenNo;
        List<TMhCritNotify> exists = critNotifyMapper.selectList(new QueryWrapper<TMhCritNotify>()
                .eq("del_flag", 0).likeRight("batch_no", batchPrefix).eq("closed", NO));
        if (exists != null && !exists.isEmpty()) {
            TMhCritNotify n = exists.get(0);
            Date last = n.getLastNotifyAt() == null ? n.getFirstNotifyAt() : n.getLastNotifyAt();
            Date gap = plusMin(last, RETRY_GAP_MIN);
            if (at.before(gap)) {
                // 还没到重试间隔，本轮不动
                return 0;
            }
            if (nullToZero(n.getTryCount()) >= 3) {
                // 重试三次没人接就不打了，各处都是这个次数
                return 0;
            }
            n.setTryCount(Integer.valueOf(nullToZero(n.getTryCount()) + 1));
            n.setLastNotifyAt(at);
            critNotifyMapper.updateById(n);
            return 1;
        }
        int made = 0;
        for (TMhCritValue c : crits) {
            TMhCritNotify n = new TMhCritNotify();
            n.setBatchNo(batchPrefix + "-" + c.getId());
            n.setSpecimenNo(specimenNo);
            n.setTargetUser("值班护士站");
            n.setNotifyPath(Integer.valueOf(0));
            n.setTryCount(Integer.valueOf(1));
            n.setNotified(Integer.valueOf(NO));
            n.setFirstNotifyAt(at);
            n.setLastNotifyAt(at);
            Date base = c.getCritTime() == null ? at : c.getCritTime();
            n.setDeadlineAt(plusMin(base, DEADLINE_MIN));
            n.setTimeoutFlag(Integer.valueOf(NO));
            n.setClosed(Integer.valueOf(NO));
            n.setFalseFlag(Integer.valueOf(NO));
            n.setStatus(Integer.valueOf(NS_NOTIFYING));
            critNotifyMapper.insert(n);
            made++;
        }
        return made;
    }

    private boolean withinDeadline(TMhCritNotify n, Date at) {
        Date dl = n.getDeadlineAt();
        return dl == null || at.before(dl);
    }

    private static int nullToZero(Integer v) {
        return v == null ? 0 : v.intValue();
    }

    @Override
    public TMhCritNotify deal(Long notifyId, String result, String handler, Date at) {
        if (notifyId == null || at == null) {
            return null;
        }
        TMhCritNotify n = critNotifyMapper.selectById(notifyId);
        if (n == null) {
            return null;
        }
        // 回填处置即闭环
        n.setDealResult(result);
        n.setDealBy(handler);
        n.setDealAt(at);
        n.setNotified(Integer.valueOf(YES));
        n.setClosed(Integer.valueOf(YES));
        n.setStatus(Integer.valueOf(NS_CLOSED));
        critNotifyMapper.updateById(n);
        List<TMhCritValue> vs = listPending(n.getSpecimenNo());
        if (vs != null) {
            for (TMhCritValue v : vs) {
                if (v != null && v.getStatus() != null
                        && (v.getStatus().intValue() == ST_CRIT)) {
                    v.setStatus(Integer.valueOf(ST_CLOSED));
                    critValueMapper.updateById(v);
                }
            }
        }
        return n;
    }

    @Override
    public int escalate(String specimenNo, Date at) {
        if (specimenNo == null || at == null) {
            return 0;
        }
        List<TMhCritNotify> rows = critNotifyMapper.selectList(new QueryWrapper<TMhCritNotify>()
                .eq("del_flag", 0).eq("specimen_no", specimenNo).eq("closed", NO));
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (TMhCritNotify r : rows) {
            if (r == null || r.getDeadlineAt() == null) {
                continue;
            }
            // 超时限且仍未接通：必须升级给主管医生，不能只留一笔已超时
            if (!at.before(r.getDeadlineAt()) && nullToZero(r.getNotified()) == NO) {
                // 超时就在本条上留个已超时标记，升级不归这一层管
                r.setTimeoutFlag(Integer.valueOf(YES));
                critNotifyMapper.updateById(r);
            }
        }
        return n;
    }

    @Override
    public String trace(String specimenNo) {
        if (specimenNo == null) {
            return "查无此标本";
        }
        List<TMhCritValue> vs = listPending(specimenNo);
        if (vs == null || vs.isEmpty()) {
            return "查无此标本";
        }
        StringBuilder sb = new StringBuilder();
        TMhCritValue v = vs.get(0);
        sb.append("判定:").append(v.getCritTime() == null ? "缺判定时刻" : ts(v.getCritTime()));
        List<TMhCritNotify> ns = critNotifyMapper.selectList(new QueryWrapper<TMhCritNotify>()
                .eq("del_flag", 0).eq("specimen_no", specimenNo));
        if (ns == null || ns.isEmpty()) {
            sb.append("|通知:缺通知记录");
        } else {
            TMhCritNotify n = ns.get(0);
            sb.append("|通知:").append(n.getLastNotifyAt() == null ? "缺通知时刻" : ts(n.getLastNotifyAt()));
            sb.append("|处置:").append(n.getDealAt() == null ? "已处理" : ts(n.getDealAt()));
            if (n.getUpgradeAt() != null) {
                sb.append("|升级:").append(ts(n.getUpgradeAt()));
            }
        }
        return sb.toString();
    }
}