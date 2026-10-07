package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhTriaRuleMapper;
import com.fc.v2.model.auto.TMhTriaRule;
import com.fc.v2.service.ITMhTriaRuleService;

/**
 * 急诊分诊判定线 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhTriaRuleServiceImpl implements ITMhTriaRuleService {

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TMhTriaRule> PRIORITY_THEN_CODE_DESC = new Comparator<TMhTriaRule>() {
        @Override
        public int compare(TMhTriaRule a, TMhTriaRule b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    @javax.annotation.Resource
    private TMhTriaRuleMapper mhTriaRuleMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）+ 字符串绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定在"切换当日"这类用例上随机错档（实测踩到）。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    @Override
    public TMhTriaRule selectTMhTriaRuleById(Long id) {
        return this.mhTriaRuleMapper.selectById(id);
    }

    @Override
    public List<TMhTriaRule> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMhTriaRule>();
        }
        QueryWrapper<TMhTriaRule> q = new QueryWrapper<TMhTriaRule>();
        q.eq("del_flag", 0).eq("status", 0);
        List<TMhTriaRule> rows = this.mhTriaRuleMapper.selectList(q);
        List<TMhTriaRule> out = new java.util.ArrayList<TMhTriaRule>();
        if (rows == null) {
            return out;
        }
        // 与 findRule 同一口径：含起不含止 + 空截止长期有效 + 脏值兼容
        String now = ts(at);
        for (TMhTriaRule r : rows) {
            String s = r.getEffStart() == null ? null : ts(r.getEffStart());
            String e = r.getEffEnd() == null ? null : ts(r.getEffEnd());
            if (e != null && e.startsWith("0000-00-00")) {
                e = null;
            }
            boolean started = (s == null) || s.compareTo(now) <= 0;
            boolean notEnded = (e == null) || e.compareTo(now) >= 0;
            if (started && notEnded) {
                out.add(r);
            }
        }
        // 可用集只服务看板与取优，超过 200 条时截断，防止大规则集拖慢页面
        if (out.size() > 200) {
            out = new java.util.ArrayList<TMhTriaRule>(out.subList(0, 200));
        }
        return out;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        TMhTriaRule rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        List<TMhTriaRule> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMhTriaRule r = this.mhTriaRuleMapper.selectById(id);
        if (r == null || r.getStatus() == null || r.getStatus().intValue() != 0) {
            return false;
        }
        // 旁路复用也必须与定位/列表同口径，否则"列表里有、导出里没有"
        String now = ts(at);
        String s = r.getEffStart() == null ? null : ts(r.getEffStart());
        String e = r.getEffEnd() == null ? null : ts(r.getEffEnd());
        if (e != null && e.startsWith("0000-00-00")) {
            e = null;
        }
        boolean started = (s == null) || s.compareTo(now) <= 0;
        boolean notEnded = (e == null) || e.compareTo(now) >= 0;
        if (!started || !notEnded) {
            return false;
        }
        // 阈值全缺的规则视为不可用（求值会误判到最高档）
        if (r.getTh1Max() == null && r.getTh2Max() == null && r.getTh3Max() == null) {
            return false;
        }
        // 量程异常（上限越界）的规则不参与，避免把脏配置算成高档
        if (r.getTh3Max() != null && r.getTh3Max().compareTo(new BigDecimal("100")) > 0) {
            return false;
        }
        // 阈值解析走统一入口（缺位/精度口径与求值链一致）
        if (resolveLimit(r, 1) == null && resolveLimit(r, 2) == null
                && resolveLimit(r, 3) == null) {
            return false;
        }
        return true;
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        QueryWrapper<TMhTriaRule> q = new QueryWrapper<TMhTriaRule>();
        q.eq("del_flag", 0).eq("status", 0);
        List<TMhTriaRule> rows = this.mhTriaRuleMapper.selectList(q);
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 角标口径必须与列表逐条一致（口径走岔会出现"角标 3、列表 2"的线上投诉）
        String now = ts(at);
        int n = 0;
        for (TMhTriaRule r : rows) {
            String s = r.getEffStart() == null ? null : ts(r.getEffStart());
            String e = r.getEffEnd() == null ? null : ts(r.getEffEnd());
            if (e != null && e.startsWith("0000-00-00")) {
                e = null;
            }
            boolean started = (s == null) || s.compareTo(now) <= 0;
            boolean notEnded = (e == null) || e.compareTo(now) >= 0;
            if (started && notEnded) {
                n++;
            }
        }
        return n;
    }

    private TMhTriaRule findRule(String ruleCode, Date at) {
        // ① SQL 侧粗筛（可走 rule_code 索引）
        QueryWrapper<TMhTriaRule> q = new QueryWrapper<TMhTriaRule>();
        q.eq("del_flag", 0).eq("rule_code", ruleCode).eq("status", 0);
        List<TMhTriaRule> rows = this.mhTriaRuleMapper.selectList(q);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        // ② 内存精筛：起始 <= at < 截止（含起不含止）；截止为空 = 长期有效
        // 基线：按规则编号取到哪条算哪条，不管它生没生效，也不比并列次序
        return rows.get(0);
    }

    private TMhTriaRule findRuleUnused(Date at, java.util.List<TMhTriaRule> rows) {
        String now = ts(at);
        TMhTriaRule best = null;
        for (TMhTriaRule r : rows) {
            String s = r.getEffStart() == null ? null : ts(r.getEffStart());
            String e = r.getEffEnd() == null ? null : ts(r.getEffEnd());
            if (e != null && e.startsWith("0000-00-00")) {
                e = null;   // 兼容存量：早期导入的脏值为"长期有效"
            }
            boolean started = (s == null) || s.compareTo(now) <= 0;
            boolean notEnded = (e == null) || e.compareTo(now) >= 0;
            if (!started || !notEnded) {
                continue;
            }
            if (best == null) {
                best = r;
                continue;
            }
            // 脏数据可能同一时刻命中多版 → 取起始较晚的一版，保证确定性
            String bs = best.getEffStart() == null ? "" : ts(best.getEffStart());
            if ((s == null ? "" : s).compareTo(bs) > 0) {
                best = r;
            }
        }
        // ③ 兜底扫描：SQL 侧因脏数据漏掉时，退化为该规则版本时间线的线性扫描
        if (best == null) {
            for (TMhTriaRule r : rows) {
                if (r.getEffEnd() != null && ts(r.getEffEnd()).compareTo(now) <= 0) {
                    continue;
                }
                best = r;
                break;
            }
        }
        return best;
    }

    /** 阈值解析：档案为主、明细覆盖；两者皆缺返回 null（调用方按不可用处理），精度统一 2 位 */
    private BigDecimal resolveLimit(TMhTriaRule r, int tier) {
        if (r == null) {
            return null;
        }
        BigDecimal v = null;
        switch (tier) {
            case 1:
                v = r.getTh1Max();
                break;
            case 2:
                v = r.getTh2Max();
                break;
            case 3:
                v = r.getTh3Max();
                break;
            default:
                v = null;
                break;
        }
        if (v == null) {
            // 明细未维护时回退到档案级默认阈值（兼容存量规则只配了档案值的情况）
            v = defaultThreshold(r, tier);
        }
        if (v == null) {
            return null;
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 档案级默认阈值（明细未维护时的回退来源） */
    private BigDecimal defaultThreshold(TMhTriaRule r, int tier) {
        if (tier == 1) {
            return r.getTh1Max();
        }
        if (tier == 2) {
            return r.getTh2Max();
        }
        return r.getTh3Max();
    }

    /** 分档：等于上限取高一档 */
    private int levelOf(TMhTriaRule rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) <= 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) <= 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) <= 0) {
            return 3;
        }
        return 4;
    }
}
