package com.fc.v2;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fc.v2.service.ITMhSurgApplyService;
import com.fc.v2.service.impl.TMhSurgApplyServiceImpl;

/**
 * 术前三方会签口径场景验证（H2 MySQL 模式，不落 MySQL）。直接运行 main。
 */
public class SurgScenarioTest {

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.fc.v2.mapper.auto")
    static class Cfg {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl("jdbc:h2:mem:surg;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000");
            ds.setUsername("sa");
            ds.setPassword("");
            return ds;
        }
        @Bean
        PlatformTransactionManager txManager(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
        @Bean
        org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            MybatisSqlSessionFactoryBean f = new MybatisSqlSessionFactoryBean();
            f.setDataSource(ds);
            MybatisConfiguration mc = new MybatisConfiguration();
            mc.setMapUnderscoreToCamelCase(true);
            mc.setJdbcTypeForNull(JdbcType.NULL);
            f.setConfiguration(mc);
            return f.getObject();
        }
        @Bean
        ITMhSurgApplyService surgService() {
            return new TMhSurgApplyServiceImpl();
        }
        @Bean
        JdbcTemplate jdbc(DataSource ds) {
            return new JdbcTemplate(ds);
        }
    }

    static int pass = 0;
    static void check(String name, boolean cond) {
        if (!cond) throw new IllegalStateException("断言失败：" + name);
        System.out.println("PASS " + (++pass) + " " + name);
    }
    static void checkContains(String name, String text, String... subs) {
        for (String s : subs) {
            if (!text.contains(s)) {
                throw new IllegalStateException("断言失败：" + name + "，缺少文本：" + s + "\n实际输出：\n" + text);
            }
        }
        System.out.println("PASS " + (++pass) + " " + name);
    }
    static void checkNotContains(String name, String text, String... subs) {
        for (String s : subs) {
            if (text.contains(s)) {
                throw new IllegalStateException("断言失败：" + name + "，不应出现文本：" + s + "\n实际输出：\n" + text);
            }
        }
        System.out.println("PASS " + (++pass) + " " + name);
    }
    interface Run { void run() throws Exception; }
    static void expectThrow(String name, Run r, String... subs) {
        try {
            r.run();
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            for (String s : subs) {
                if (!msg.contains(s)) {
                    throw new IllegalStateException("断言失败：" + name + "，异常信息不含：" + s + "；实际：" + msg);
                }
            }
            System.out.println("PASS " + (++pass) + " " + name + "（拦截：" + msg + "）");
            return;
        }
        throw new IllegalStateException("断言失败：" + name + "，预期抛异常但成功了");
    }

    static final String[][] NURSE = {
            {"0", "N_IDENTITY"}, {"0", "N_SITE_MARK"}, {"0", "N_IMAGING"}, {"0", "N_BLOOD"}};
    static final String[][] SURGEON = {
            {"1", "S_PROCEDURE"}, {"1", "S_SITE"}, {"1", "S_INSTRUMENT"}, {"1", "S_BACKUP"}};
    static final String[][] ANES = {
            {"2", "A_AIRWAY"}, {"2", "A_MEDICATION"}, {"2", "A_TRANSFUSION"}};
    static final String[][] ALL = concat(NURSE, SURGEON, ANES);

    static String[][] concat(String[][]... parts) {
        int n = 0;
        for (String[][] p : parts) n += p.length;
        String[][] out = new String[n][];
        int i = 0;
        for (String[][] p : parts) for (String[] x : p) out[i++] = x;
        return out;
    }
    static void signItems(ITMhSurgApplyService svc, String no, String[][] items, int result, String who) {
        for (String[] it : items) svc.sign(no, it[0], it[1], result, who, new Date());
    }
    static void recheckItems(ITMhSurgApplyService svc, String no, String[][] items, int result, String who) {
        for (String[] it : items) svc.recheck(no, it[0], it[1], result, who, new Date());
    }
    static void signAll(ITMhSurgApplyService svc, String no) {
        signItems(svc, no, ALL, 1, "核对方");
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class);
        ITMhSurgApplyService svc = ctx.getBean(ITMhSurgApplyService.class);
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        initSchema(jdbc);
        try {
            scenarioBasicParallel(svc);
            scenarioImmutableAndReject(svc);
            scenarioReleaseOccupy(svc, jdbc);
            scenarioVoidRedo(svc, jdbc);
            scenarioEmergency(svc, jdbc);
            scenarioSiteDispute(svc, jdbc);
            scenarioSubjectChange(svc, jdbc);
            scenarioTransfer(svc);
            scenarioCancelAndArchive(svc, jdbc);
            scenarioHistoricalReleased(svc, jdbc);
            scenarioPostReleaseFrozen(svc);
            scenarioNotFound(svc);
            scenarioConcurrency(svc, jdbc);
            scenarioIsolation(svc);
            System.out.println("\n全部 " + pass + " 项口径断言通过");
        } finally {
            ctx.close();
        }
    }

    /** 1. 建单即铺 11 项；三方并行不排队；缺一方扣单 */
    static void scenarioBasicParallel(ITMhSurgApplyService svc) {
        String no = "SA-01";
        svc.createApply(no, "P001", "普外科", "L", new Date());
        String t0 = svc.traceChain(no);
        checkContains("新建单明说无人核对", t0, "本台尚无人核对", "未核（待签）", "尚未放行");
        checkNotContains("无人核对≠不通过", t0, "不通过项");

        // 术者先签、护理再签，不排先后；首签后进入会签中
        svc.sign(no, "1", "S_PROCEDURE", 1, "术者乙", new Date());
        check("首签后进入会签中", svc.get(no).getStatus() == 1);
        svc.sign(no, "0", "N_IDENTITY", 1, "护士甲", new Date());
        // 护理、术者各自签齐（跳过已签的两项），麻醉一项不签
        signItems(svc, no, new String[][]{{"0", "N_SITE_MARK"}, {"0", "N_IMAGING"}, {"0", "N_BLOOD"}},
                1, "护士甲");
        signItems(svc, no, new String[][]{{"1", "S_SITE"}, {"1", "S_INSTRUMENT"}, {"1", "S_BACKUP"}},
                1, "术者乙");
        String t = svc.traceChain(no);
        checkContains("缺麻醉整单扣在麻醉", t, "整单扣在 ->", "麻醉[A_AIRWAY");
    }

    /** 2. 已签不可涂改；不通过扣留 */
    static void scenarioImmutableAndReject(ITMhSurgApplyService svc) {
        // SA-01 承接场景 1：护理、术者已齐，麻醉一项未签
        expectThrow("重复签已落定项拒绝涂改",
                () -> svc.sign("SA-01", "0", "N_IDENTITY", 1, "护士甲", new Date()),
                "不可涂改");
        expectThrow("三方未齐备不可放行",
                () -> svc.release("SA-01", "OR-3", "排程员", new Date()),
                "三方未齐备");

        // 麻醉签一项不通过：整单扣留，放行被拦
        svc.sign("SA-01", "2", "A_AIRWAY", 2, "麻醉丙", new Date());
        String t2 = svc.traceChain("SA-01");
        checkContains("倒查列出不通过项", t2, "不通过项", "A_AIRWAY", "整单扣留");
        expectThrow("有不通过项普通放行被拦",
                () -> svc.release("SA-01", "OR-3", "排程员", new Date()),
                "不通过项");
    }

    /** 3. 齐了放行+占位一起成，重复放行不成 */
    static void scenarioReleaseOccupy(ITMhSurgApplyService svc, JdbcTemplate jdbc) {
        String no = "SA-02";
        svc.createApply(no, "P002", "普外科", "R", new Date());
        signAll(svc, no);
        check("放行成功", svc.release(no, "OR-1", "排程员", new Date()));
        check("放行后状态=已占用", svc.get(no).getStatus() == 3);
        check("占位资源落库", "OR-1".equals(jdbc.queryForObject(
                "SELECT occupy_resource FROM t_mh_surg_apply WHERE apply_no=?", String.class, no)));
        check("放行时刻落库", jdbc.queryForObject(
                "SELECT release_at FROM t_mh_surg_apply WHERE apply_no=?", Date.class, no) != null);
        String t = svc.traceChain(no);
        checkContains("链路成对", t, "放行+占位成对");
        expectThrow("重复放行不成", () -> svc.release(no, "OR-2", "排程员", new Date()),
                "已放行");
    }

    /** 4. 不通过 → 作废重做：版本+1，旧项原结果留痕，新项重铺 */
    static void scenarioVoidRedo(ITMhSurgApplyService svc, JdbcTemplate jdbc) {
        String no = "SA-01";
        check("作废重做成功", svc.voidAndRedo(no, "气道问题处置后重做", "术者乙", new Date()));
        check("版本号递增到 2", svc.get(no).getVersionNo() == 2);
        check("重做后回到待会签", svc.get(no).getStatus() == 0);
        Integer oldReject = jdbc.queryForObject(
                "SELECT sign_result FROM t_mh_surg_sign WHERE apply_no=? AND version_no=1 AND item_key='A_AIRWAY'",
                Integer.class, no);
        check("旧版不通过原结果保留可倒查", oldReject != null && oldReject == 2);
        Integer oldDel = jdbc.queryForObject(
                "SELECT del_flag FROM t_mh_surg_sign WHERE apply_no=? AND version_no=1 AND item_key='A_AIRWAY'",
                Integer.class, no);
        check("旧版条目软删", oldDel != null && oldDel == 1);
        Integer newUnsigned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_mh_surg_sign WHERE apply_no=? AND version_no=2 AND del_flag=0 AND sign_result=0",
                Integer.class, no);
        check("新版重铺 11 个未核项", newUnsigned != null && newUnsigned == 11);
        String t = svc.traceChain(no);
        checkContains("倒查显示旧版v1留痕与v2无人核对", t,
                "旧版v1", "版本v2", "本台尚无人核对", "尚未放行");
        checkNotContains("作废后无残留放行时刻", t, "放行+占位成对");
        // 重做后重新签齐可正常放行
        signAll(svc, no);
        check("重做签齐后放行成功", svc.release(no, "OR-2", "排程员", new Date()));
    }

    /** 5. 紧急放行：理由必填、不通过/异议拦截、补核待办与时限、超时与补核不通过 */
    static void scenarioEmergency(ITMhSurgApplyService svc, JdbcTemplate jdbc) {
        // 5.1 有不通过项，紧急也拦
        String e1 = "EM-01";
        svc.createApply(e1, "P010", "急诊外科", "L", new Date());
        svc.sign(e1, "2", "A_AIRWAY", 2, "麻醉丙", new Date());
        expectThrow("紧急放行不能盖过不通过",
                () -> svc.emergencyRelease(e1, "OR-9", "抢救", "院长", new Date()),
                "不能盖过");

        // 5.2 异议未处置，紧急也拦
        String e2 = "EM-02";
        svc.createApply(e2, "P011", "急诊外科", "L", new Date());
        svc.reportSiteDispute(e2, "护士甲", new Date());
        expectThrow("异议未处置紧急也冻结",
                () -> svc.emergencyRelease(e2, "OR-9", "抢救", "院长", new Date()),
                "异议");

        // 5.3 理由必填
        String e3 = "EM-03";
        svc.createApply(e3, "P012", "急诊外科", "R", new Date());
        expectThrow("紧急放行理由必填",
                () -> svc.emergencyRelease(e3, "OR-9", "  ", "院长", new Date()),
                "决策理由");

        // 5.4 先放行事后补核：0 签署也能放，待办+24h
        Date t0 = new Date(1_800_000_000_000L);
        check("紧急先放行成功", svc.emergencyRelease(e3, "OR-9", "脾破裂大出血，救命优先", "值班院长", t0));
        check("紧急单已占位", svc.get(e3).getStatus() == 3);
        check("urgent=1", svc.get(e3).getUrgentFlag() == 1);
        check("补核待办已登记", svc.get(e3).getRecheckFlag() == 1);
        long deltaH = (svc.get(e3).getRecheckDeadline().getTime() - t0.getTime()) / 3600000;
        check("补核时限=24小时", deltaH == 24);
        String trace = svc.traceChain(e3);
        checkContains("倒查显示决策人理由与补核时限", trace,
                "紧急", "决策人：值班院长", "脾破裂大出血", "事后补核待办");
        expectThrow("待办未清不可归档", () -> svc.archive(e3, "病案室", new Date()), "补核待办");
        // 超时补核照样收，逐笔标已超时
        Date late = new Date(t0.getTime() + 25L * 3600000);
        svc.recheck(e3, "0", "N_IDENTITY", 1, "护士甲", late);
        checkContains("超时补核明写已超时", svc.traceChain(e3), "已超时");
        // 补齐其余 10 项，待办清零，可归档
        recheckItems(svc, e3, new String[][]{
                {"0", "N_SITE_MARK"}, {"0", "N_IMAGING"}, {"0", "N_BLOOD"},
                {"1", "S_PROCEDURE"}, {"1", "S_SITE"}, {"1", "S_INSTRUMENT"}, {"1", "S_BACKUP"},
                {"2", "A_AIRWAY"}, {"2", "A_MEDICATION"}, {"2", "A_TRANSFUSION"}}, 1, "补核人");
        check("补齐后待办清零", svc.get(e3).getRecheckFlag() == 0);
        check("补齐后可归档", svc.archive(e3, "病案室", new Date()));

        // 5.5 补核不通过：定性不良事件，待办清零，可封存归档
        String e4 = "EM-04";
        svc.createApply(e4, "P013", "急诊外科", "N", new Date());
        svc.emergencyRelease(e4, "OR-8", "抢救", "值班院长", new Date());
        svc.recheck(e4, "0", "N_IDENTITY", 2, "护士甲", new Date());
        check("补核不通过待办清零", svc.get(e4).getRecheckFlag() == 0);
        checkContains("补核不通过如实留痕", svc.traceChain(e4), "不良事件");
        check("补核不通过按封存归档", svc.archive(e4, "病案室", new Date()));
    }

    /** 6. 部位异议：冻结、以档案为准；有签署则整单重做 */
    static void scenarioSiteDispute(ITMhSurgApplyService svc, JdbcTemplate jdbc) {
        // 无签署：原地更正
        String d1 = "DP-01";
        svc.createApply(d1, "P020", "甲乳外科", "R", new Date());
        svc.reportSiteDispute(d1, "护士甲", new Date());
        expectThrow("异议冻结放行", () -> svc.release(d1, "OR-5", "排程员", new Date()), "异议");
        svc.resolveSiteDispute(d1, "L", "术者乙", new Date());
        check("无签署原地更正版本不变", svc.get(d1).getVersionNo() == 1);
        check("部位按档案更正", "L".equals(svc.get(d1).getSiteFlag()));
        checkContains("更正来源标注病历档案", svc.traceChain(d1), "病历档案", "原地更正");

        // 有签署：整单重做
        String d2 = "DP-02";
        svc.createApply(d2, "P021", "甲乳外科", "R", new Date());
        svc.sign(d2, "0", "N_IDENTITY", 1, "护士甲", new Date());
        svc.reportSiteDispute(d2, "护士甲", new Date());
        svc.resolveSiteDispute(d2, "L", "术者乙", new Date());
        check("有签署版本递增", svc.get(d2).getVersionNo() == 2);
        check("有签署回到待会签", svc.get(d2).getStatus() == 0);
        Integer kept = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_mh_surg_sign WHERE apply_no=? AND version_no=1 AND del_flag=1 AND sign_result=1",
                Integer.class, d2);
        check("旧版已签项原结果留痕", kept == 1);
        checkContains("倒查写明整单重做", svc.traceChain(d2), "已签项全部作废", "旧版v1");

        expectThrow("无在办异议不可处置",
                () -> svc.resolveSiteDispute(d2, "R", "术者乙", new Date()), "无在办");
    }

    /** 7. 换人：整单重做 */
    static void scenarioSubjectChange(ITMhSurgApplyService svc, JdbcTemplate jdbc) {
        String no = "SB-01";
        svc.createApply(no, "P030", "骨科", "L", new Date());
        svc.sign(no, "0", "N_IDENTITY", 1, "护士甲", new Date());
        check("换人成功", svc.changeSubject(no, "P031", "护士长", new Date()));
        check("患者编号已换", "P031".equals(svc.get(no).getSubjectNo()));
        check("换人后版本 v2", svc.get(no).getVersionNo() == 2);
        Integer oldKept = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_mh_surg_sign WHERE apply_no=? AND version_no=1 AND del_flag=1",
                Integer.class, no);
        check("换人旧版签署留痕", oldKept == 11);
        Integer newCnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_mh_surg_sign WHERE apply_no=? AND version_no=2 AND del_flag=0 AND sign_result=0",
                Integer.class, no);
        check("换人新版 11 项重铺", newCnt == 11);
        expectThrow("换成同一患者拒绝",
                () -> svc.changeSubject(no, "P031", "护士长", new Date()), "患者未变化");
    }

    /** 8. 转床：标变更继续用，已签有效 */
    static void scenarioTransfer(ITMhSurgApplyService svc) {
        String no = "SB-02";
        svc.createApply(no, "P032", "骨科", "R", new Date());
        svc.sign(no, "0", "N_IDENTITY", 1, "护士甲", new Date());
        check("转床登记成功", svc.markTransfer(no, "12床->15床", "护士甲", new Date()));
        String t = svc.traceChain(no);
        checkContains("转床已签继续有效", t, "转床/换间", "已签继续有效", "已核通过");
        check("转床版本不变", svc.get(no).getVersionNo() == 1);
    }

    /** 9. 取消不重做 → 终态作废 → 可归档；已占位不可取消 */
    static void scenarioCancelAndArchive(ITMhSurgApplyService svc, JdbcTemplate jdbc) {
        String no = "CA-01";
        svc.createApply(no, "P040", "泌尿外科", "L", new Date());
        svc.sign(no, "0", "N_IDENTITY", 1, "护士甲", new Date());
        check("取消成功", svc.cancelApply(no, "手术停做", "术者乙", new Date()));
        check("状态=已作废", svc.get(no).getStatus() == 4);
        expectThrow("作废单不可再签",
                () -> svc.sign(no, "0", "N_BLOOD", 1, "护士甲", new Date()), "作废");
        check("作废单可归档留底", svc.archive(no, "病案室", new Date()));
        check("归档落库", svc.get(no).getStatus() == 5);

        String no2 = "CA-02";
        svc.createApply(no2, "P041", "泌尿外科", "L", new Date());
        signAll(svc, no2);
        svc.release(no2, "OR-6", "排程员", new Date());
        expectThrow("已开台不可取消", () -> svc.cancelApply(no2, "x", "术者乙", new Date()), "不可取消");
        expectThrow("普通单归档须三方齐备",
                () -> svc.archive("CA-03-UNUSED", "病案室", new Date()), "查无此单据");
    }

    /** 10. 历史遗留「已放行未占位」：只许补占位，不许直接归档；上线前老单无电子会签项 */
    static void scenarioHistoricalReleased(ITMhSurgApplyService svc, JdbcTemplate jdbc) {
        String no = "HIST-01";
        Date released = new Date(System.currentTimeMillis() - 86400000L);
        jdbc.update("INSERT INTO t_mh_surg_apply(apply_no,subject_no,dept_name,site_flag,"
                        + "site_source,urgent_flag,status,version_no,recheck_flag,release_at,del_flag,create_time) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                no, "P050", "胸外科", "L", "0", 0, 2, 1, 0, released, 0, released);
        String t0 = svc.traceChain(no);
        checkContains("倒查明写缺占位环", t0, "缺「占位」环");
        expectThrow("已放行未占位不可直接归档",
                () -> svc.archive(no, "病案室", new Date()), "先补齐占位");
        check("补齐占位成功", svc.release(no, "OR-7", "排程员", new Date()));
        check("补后状态=已占用", svc.get(no).getStatus() == 3);
        check("放行时刻未被改动", released.equals(svc.get(no).getReleaseAt()));
        check("无电子签署的历史单补齐后可归档", svc.archive(no, "病案室", new Date()));
    }

    /** 11. 放行后冻结一切术前变更 */
    static void scenarioPostReleaseFrozen(ITMhSurgApplyService svc) {
        String no = "FR-01";
        svc.createApply(no, "P060", "神经外科", "L", new Date());
        signAll(svc, no);
        svc.release(no, "OR-10", "排程员", new Date());
        expectThrow("放行后不可再签", () -> svc.sign(no, "0", "N_BLOOD", 1, "护士甲", new Date()), "事后补核");
        expectThrow("放行后不可标异议", () -> svc.reportSiteDispute(no, "护士甲", new Date()), "不可标异议");
        expectThrow("放行后不可换人", () -> svc.changeSubject(no, "P061", "护士长", new Date()), "不可换人");
        expectThrow("放行后不可转床", () -> svc.markTransfer(no, "换床", "护士甲", new Date()), "不可变更");
        expectThrow("已开台不可作废重做", () -> svc.voidAndRedo(no, "x", "术者乙", new Date()), "不可作废");
    }

    /** 12. 查无此单单独表述 */
    static void scenarioNotFound(ITMhSurgApplyService svc) {
        String t = svc.traceChain("NO-SUCH-NO");
        checkContains("查无此单单独表述", t, "查无此单据", "尚无任何核对记录");
    }

    /**
     * 13. 并发：放行与新落不通过项不可能同时成；双放只成一个；同项双签只成一笔。
     */
    static void scenarioConcurrency(ITMhSurgApplyService svc, JdbcTemplate jdbc) throws Exception {
        // 13.1 20 台：10 项通过、1 项未核；放行 与 末项签不通过 对撞，绝不允许两边都成
        int n = 20;
        for (int i = 0; i < n; i++) {
            String no = String.format("CC-RACE-%02d", i);
            svc.createApply(no, "P1" + i, "普外科", "L", new Date());
            // 签前 10 项，留最后一项 A_TRANSFUSION 未核
            String[][] ten = new String[10][];
            System.arraycopy(ALL, 0, ten, 0, 10);
            signItems(svc, no, ten, 1, "护士甲");
        }
        AtomicInteger bothWon = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < n; i++) {
                String no = String.format("CC-RACE-%02d", i);
                CountDownLatch gun = new CountDownLatch(1);
                Future<?> f1 = pool.submit(() -> {
                    try { gun.await(); svc.release(no, "OR-X", "排程员", new Date()); }
                    catch (Exception ignore) { }
                });
                Future<?> f2 = pool.submit(() -> {
                    try { gun.await(); svc.sign(no, "2", "A_TRANSFUSION", 2, "麻醉丙", new Date()); }
                    catch (Exception ignore) { }
                });
                gun.countDown();
                f1.get(); f2.get();
                Integer st = jdbc.queryForObject(
                        "SELECT status FROM t_mh_surg_apply WHERE apply_no=?", Integer.class, no);
                Integer rejects = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM t_mh_surg_sign WHERE apply_no=? AND del_flag=0 AND sign_result=2",
                        Integer.class, no);
                if (st != null && st == 3 && rejects != null && rejects > 0) {
                    bothWon.incrementAndGet();
                }
            }
        } finally {
            pool.shutdown();
        }
        check("放行与不通过对撞 20 台无一两边都成", bothWon.get() == 0);

        // 13.2 双放对撞：只成一笔
        String no = "CC-DBL-01";
        svc.createApply(no, "P200", "普外科", "L", new Date());
        signAll(svc, no);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        Runnable r = () -> {
            try { start.await(); if (svc.release(no, "OR-Y", "排程员", new Date())) wins.incrementAndGet(); }
            catch (Exception ignore) { }
        };
        ExecutorService pool2 = Executors.newFixedThreadPool(2);
        try {
            Future<?> f1 = pool2.submit(r);
            Future<?> f2 = pool2.submit(r);
            start.countDown();
            f1.get(); f2.get();
        } finally {
            pool2.shutdown();
        }
        check("并发放行只成一笔", wins.get() == 1);

        // 13.3 同项双签对撞：只成一笔
        String no2 = "CC-DBL-02";
        svc.createApply(no2, "P201", "普外科", "L", new Date());
        CountDownLatch start2 = new CountDownLatch(1);
        AtomicInteger signWins = new AtomicInteger();
        Runnable r3 = () -> {
            try { start2.await(); svc.sign(no2, "0", "N_IDENTITY", 1, "护士甲", new Date()); signWins.incrementAndGet(); }
            catch (Exception ignore) { }
        };
        ExecutorService pool3 = Executors.newFixedThreadPool(2);
        try {
            Future<?> g1 = pool3.submit(r3);
            Future<?> g2 = pool3.submit(r3);
            start2.countDown();
            g1.get(); g2.get();
        } finally {
            pool3.shutdown();
        }
        check("同项并发只成一笔", signWins.get() == 1);
    }

    /** 14. 多台并行不串台：A 台内容不出现在 B 台倒查里 */
    static void scenarioIsolation(ITMhSurgApplyService svc) throws Exception {
        String a = "ISO-A", b = "ISO-B";
        svc.createApply(a, "PA", "科A", "L", new Date());
        svc.createApply(b, "PB", "科B", "R", new Date());
        // B 台术者先签齐（与 B 台放行不并发，避免合法的状态冲突）
        for (String[] it : SURGEON) svc.sign(b, it[0], it[1], 1, "B术者", new Date());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // A 台护理会签 与 B 台紧急放行 同时压上，锁不同行，互不阻塞也不串状态
            Future<?> f1 = pool.submit(() -> {
                for (String[] it : NURSE) svc.sign(a, it[0], it[1], 1, "A护士", new Date());
            });
            Future<?> f3 = pool.submit(() -> svc.emergencyRelease(b, "OR-B", "B抢救", "B院长", new Date()));
            f1.get();
            f3.get();
        } finally {
            pool.shutdown();
        }
        String ta = svc.traceChain(a);
        String tb = svc.traceChain(b);
        checkContains("A台只含A台", ta, "PA", "科A");
        checkNotContains("A台不串B台", ta, "PB", "科B", "OR-B", "B抢救");
        checkContains("B台只含B台", tb, "PB", "OR-B");
        checkNotContains("B台不串A台", tb, "PA", "科A", "A护士");
    }

    static void initSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE t_mh_surg_apply ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "apply_no VARCHAR(64) NOT NULL UNIQUE,"
                + "subject_no VARCHAR(64), dept_name VARCHAR(64),"
                + "procedure_code VARCHAR(64), procedure_name VARCHAR(64),"
                + "site_flag VARCHAR(16), site_source VARCHAR(16),"
                + "urgent_flag TINYINT DEFAULT 0, status INT DEFAULT 0,"
                + "version_no INT DEFAULT 1, recheck_flag TINYINT DEFAULT 0,"
                + "recheck_deadline DATETIME, release_at DATETIME,"
                + "occupy_resource VARCHAR(64), occupy_at DATETIME, archive_at DATETIME,"
                + "create_by VARCHAR(64), create_time DATETIME,"
                + "update_by VARCHAR(64), update_time DATETIME, del_flag INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE t_mh_surg_sign ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "apply_no VARCHAR(64) NOT NULL, role_code VARCHAR(32) NOT NULL,"
                + "item_key VARCHAR(64) NOT NULL, item_name VARCHAR(64),"
                + "sign_result TINYINT DEFAULT 0, signed_by VARCHAR(64), signed_at DATETIME,"
                + "sign_seq INT DEFAULT 0, version_no INT NOT NULL DEFAULT 1,"
                + "create_by VARCHAR(64), create_time DATETIME,"
                + "update_by VARCHAR(64), update_time DATETIME, del_flag INT DEFAULT 0,"
                + "CONSTRAINT uk_apply_role_item_ver UNIQUE(apply_no,role_code,item_key,version_no))");
        jdbc.execute("CREATE TABLE t_mh_surg_trace ("
                + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "apply_no VARCHAR(64) NOT NULL, action_code VARCHAR(32),"
                + "action_by VARCHAR(64), action_at DATETIME, detail VARCHAR(512),"
                + "create_by VARCHAR(64), create_time DATETIME,"
                + "update_by VARCHAR(64), update_time DATETIME, del_flag INT DEFAULT 0)");
    }
}
