package com.fc.v2.service.surg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.fc.v2.model.auto.TMhSurgApply;
import com.fc.v2.model.custom.surg.SurgCheckView;
import com.fc.v2.service.ITMhSurgApplyService;

/**
 * 术前三方并行核查口径测试——H2 真 SQL、真事务、真唯一键。
 *
 * <p>角色：0 护士 N1..N4，1 术者 S1..S3，2 麻醉 A1..A3（共 10 项）。</p>
 */
class SurgCheckServiceTest {

    private static final String[] ALL_ITEMS = {
            "0:N1", "0:N2", "0:N3", "0:N4",
            "1:S1", "1:S2", "1:S3",
            "2:A1", "2:A2", "2:A3"
    };

    private static final AnnotationConfigApplicationContext CTX =
            new AnnotationConfigApplicationContext(SurgTestConfig.class);
    private final ITMhSurgApplyService svc = CTX.getBean(ITMhSurgApplyService.class);

    private static final Date T0 = new Date(1_780_000_000_000L);
    private static final Date T1 = new Date(T0.getTime() + 3_600_000L);
    private static final Date T25H = new Date(T0.getTime() + 25 * 3_600_000L);

    private void create(String no) {
        svc.createApply(no, "P" + no, "12床", "甲外科", "甲状腺部分切除",
                "L", null, 0, T0);
    }

    private void createDisputed(String no) {
        // 核查表/影像右叶，病历写左叶
        svc.createApply(no, "P" + no, "12床", "甲外科", "甲状腺部分切除",
                "R", "L", 0, T0);
    }

    private void createEmergency(String no) {
        svc.createApply(no, "P" + no, "抢救床", "急诊外科", "剖腹探查",
                "N", null, 1, T0);
    }

    /** 三方按给定前缀全部签通过 */
    private void signAll(String no, String who) {
        for (String ri : ALL_ITEMS) {
            String[] p = ri.split(":");
            svc.sign(no, p[0], p[1], true, who + "-" + p[1], T1);
        }
    }

    private int passCount(SurgCheckView v) {
        int n = 0;
        for (SurgCheckView.ItemRow r : v.getItems()) {
            if (r.isEffective() && r.getResult() != null && r.getResult() == 1) {
                n++;
            }
        }
        return n;
    }

    private IllegalStateException expectFail(Runnable r) {
        return assertThrows(IllegalStateException.class, r::run);
    }

    // ----------------------------------------------------------------
    // 1. 并行会签：三方乱序签互不排队
    // ----------------------------------------------------------------

    @Test
    void parallelSigning_noOrderingRequired() {
        create("A01");
        // 麻醉先签，护士还没动——旧实现会排队拦下
        svc.sign("A01", "2", "A1", true, "麻醉-张", T1);
        svc.sign("A01", "1", "S1", true, "术者-李", T1);
        svc.sign("A01", "0", "N1", true, "护士-王", T1);

        SurgCheckView v = svc.traceChain("A01");
        assertEquals(SurgCheckView.STATE_IN_PROGRESS, v.getOverallState());
        assertTrue(v.getBlocker().contains("护士"), "应报出还卡在护士方：" + v.getBlocker());
    }

    // ----------------------------------------------------------------
    // 2. 多台并行不串台：A 台的签署不能显示到 B 台
    // ----------------------------------------------------------------

    @Test
    void parallelTables_neverCrossTalk() throws Exception {
        create("B01");
        create("B02");
        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger fail = new AtomicInteger();
        // 3 个线程签 B01、3 个线程签 B02，每线程签自己单的若干项
        for (int t = 0; t < threads; t++) {
            final String no = t < 3 ? "B01" : "B02";
            final int threadIdx = t % 3;
            new Thread(() -> {
                try {
                    start.await();
                    // 每线程负责该单 10 项里的一段（三项左右），键不重叠
                    for (int i = threadIdx; i < ALL_ITEMS.length; i += 3) {
                        String[] p = ALL_ITEMS[i].split(":");
                        svc.sign(no, p[0], p[1], true, no + "-" + p[1], T1);
                    }
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        assertEquals(0, fail.get(), "并行签署不应有失败");

        SurgCheckView v1 = svc.traceChain("B01");
        SurgCheckView v2 = svc.traceChain("B02");
        assertEquals(10, passCount(v1), "B01 应恰好 10 项有效通过");
        assertEquals(10, passCount(v2), "B02 应恰好 10 项有效通过");
        // B01 的屏上不允许出现 B02 的签署人
        for (SurgCheckView.ItemRow r : v1.getItems()) {
            assertTrue(r.getSignedBy() == null || r.getSignedBy().startsWith("B01-"),
                    "串台了：B01 出现 " + r.getSignedBy());
        }
    }

    // ----------------------------------------------------------------
    // 3. 已签不可涂改：串行第二次拒、并发第二次也拒
    // ----------------------------------------------------------------

    @Test
    void signedItem_isImmutable_serially() {
        create("C01");
        svc.sign("C01", "0", "N1", true, "护士-王", T1);
        IllegalStateException e = expectFail(() ->
                svc.sign("C01", "0", "N1", false, "护士-王改", T1));
        assertTrue(e.getMessage().contains("不可涂改"), e.getMessage());
    }

    @Test
    void signedItem_concurrentDoubleSubmit_onlyOneWins() throws Exception {
        create("C02");
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger reject = new AtomicInteger();
        Runnable both = () -> {
            try {
                start.await();
                svc.sign("C02", "0", "N1", true, Thread.currentThread().getName(), T1);
                ok.incrementAndGet();
            } catch (IllegalStateException ex) {
                reject.incrementAndGet();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
        Thread t1 = new Thread(both, "护士-甲");
        Thread t2 = new Thread(both, "护士-乙");
        t1.start();
        t2.start();
        start.countDown();
        t1.join();
        t2.join();
        assertEquals(1, ok.get(), "同一项并发双提必须恰好一成");
        assertEquals(1, reject.get(), "同一项并发双提必须恰好一拒");
        assertEquals(1, passCount(svc.traceChain("C02")));
    }

    // ----------------------------------------------------------------
    // 4. 任一方未齐：单扣在那一方，不放行、不占位
    // ----------------------------------------------------------------

    @Test
    void release_blockedByMissingParty_withNoSlotRow() {
        create("D01");
        // 只签护士、术者，麻醉没动
        for (String ri : new String[]{"0:N1", "0:N2", "0:N3", "0:N4", "1:S1", "1:S2", "1:S3"}) {
            String[] p = ri.split(":");
            svc.sign("D01", p[0], p[1], true, "x-" + p[1], T1);
        }
        IllegalStateException e = expectFail(() -> svc.release("D01", "R1", "09:00", "排程", T1));
        assertTrue(e.getMessage().contains("扣在未签项"), e.getMessage());
        assertTrue(e.getMessage().contains("麻醉"), "必须点名卡在麻醉：" + e.getMessage());

        TMhSurgApply a = svc.get("D01");
        assertEquals(1, a.getStatus().intValue(), "未放行，状态仍核对中");
        assertNull(a.getReleaseAt());
        assertNull(a.getOccupyResource());
    }

    // ----------------------------------------------------------------
    // 5. 不通过即扣单且不可改，只能作废重做，旧版留痕
    // ----------------------------------------------------------------

    @Test
    void reject_holdsTheSheet_andOnlyVoidRedoCanChangeIt() {
        create("E01");
        svc.sign("E01", "0", "N2", false, "护士-王", T1); // 部位标识不通过
        IllegalStateException e1 = expectFail(() -> svc.release("E01", "R1", "09:00", "排程", T1));
        assertTrue(e1.getMessage().contains("不通过"));
        // 不能涂改
        expectFail(() -> svc.sign("E01", "0", "N2", true, "护士-王改", T1));

        SurgCheckView v = svc.traceChain("E01");
        assertEquals(SurgCheckView.STATE_REJECTED, v.getOverallState());
        assertTrue(v.getBlocker().contains("手术部位标识"));

        String newNo = svc.voidAndRedo("E01", "部位标识贴错，重贴后重核", "护士长", T1);
        assertEquals("E01-V2", newNo);
        SurgCheckView oldV = svc.traceChain("E01");
        assertEquals(SurgCheckView.STATE_VOID, oldV.getOverallState());
        assertEquals("E01-V2", oldV.getRedoTo());
        SurgCheckView newV = svc.traceChain(newNo);
        assertEquals(SurgCheckView.STATE_NO_SIGN, newV.getOverallState(), "新单必须全部重核");
        assertEquals(0, passCount(newV));
        assertEquals("E01", newV.getRedoOf());
    }

    // ----------------------------------------------------------------
    // 6. 择期放行＋占位原子成
    // ----------------------------------------------------------------

    @Test
    void release_andOccupyCommitAtomically() {
        create("F01");
        signAll("F01", "核");
        svc.release("F01", "手术间3", "2026-10-07 09:00", "排程员", T1);

        TMhSurgApply a = svc.get("F01");
        assertEquals(3, a.getStatus().intValue());
        assertNotNull(a.getReleaseAt());
        assertEquals("手术间3", a.getOccupyResource());
        assertEquals("手术间3#2026-10-07 09:00", a.getSlotKey());
        // 不允许重复放行
        expectFail(() -> svc.release("F01", "手术间3", "2026-10-07 09:00", "排程员", T1));
        assertEquals(SurgCheckView.STATE_RELEASED, svc.traceChain("F01").getOverallState());
    }

    // ----------------------------------------------------------------
    // 7. 重台并发：同一手术间同一时段两单抢，恰好一成一败，败者整笔回滚
    // ----------------------------------------------------------------

    @Test
    void doubleBooking_exactlyOneWins_loserRollsBackEverything() throws Exception {
        create("G01");
        create("G02");
        signAll("G01", "甲台");
        signAll("G02", "乙台");

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicReference<String> winner = new AtomicReference<>();
        AtomicReference<String> loserMsg = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);
        String[] nos = {"G01", "G02"};
        for (String no : nos) {
            new Thread(() -> {
                try {
                    start.await();
                    svc.release(no, "手术间5", "2026-10-07 10:00", "排程-" + no, T1);
                    ok.incrementAndGet();
                    winner.set(no);
                } catch (IllegalStateException ex) {
                    loserMsg.set(ex.getMessage());
                } catch (Exception ex) {
                    loserMsg.set(String.valueOf(ex));
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertEquals(1, ok.get(), "同一时段必须只能有一单占位成功");
        assertTrue(loserMsg.get().contains("已被核查单"), loserMsg.get());
        String loser = "G01".equals(winner.get()) ? "G02" : "G01";

        TMhSurgApply la = svc.get(loser);
        assertEquals(1, la.getStatus().intValue(), "败者必须整笔回滚到核对中，不许停在已放行");
        assertNull(la.getReleaseAt(), "败者不许有放行时刻");
        assertNull(la.getOccupyResource(), "败者不许占上手术间");
        assertEquals(10, passCount(svc.traceChain(loser)), "败者签署结果不受影响");

        // 库里只留一条占用，归属胜者
        SurgCheckView wv = svc.traceChain(winner.get());
        assertEquals(SurgCheckView.STATE_RELEASED, wv.getOverallState());
        assertEquals("手术间5", wv.getOccupyResource());
    }

    // ----------------------------------------------------------------
    // 8. 抢救口径：择期不许先放行；抢救必须双授权；放行即挂 24h 补核
    // ----------------------------------------------------------------

    @Test
    void emergencyPolicy_doubleAuthAndRecheckTodo() {
        // 择期未齐：不许走任何先放行
        create("H01");
        svc.sign("H01", "0", "N1", true, "护士", T1);
        expectFail(() -> svc.release("H01", "R1", "now", "排程", T1));
        expectFail(() -> svc.emergencyRelease("H01", "R1", "now", "排程",
                "主刀", "麻醉师", T1));

        // 抢救未齐但无双授权：拒
        createEmergency("H02");
        expectFail(() -> svc.emergencyRelease("H02", "R1", "now", "排程",
                "主刀", "主刀", T1)); // 同一人不许签两方
        expectFail(() -> svc.emergencyRelease("H02", "R1", "now", "排程",
                "", "麻醉师", T1));

        // 双授权：放行占位成功，挂补核待办 24h
        svc.emergencyRelease("H02", "抢救间1", "now", "排程", "主刀-赵", "麻醉师-钱", T0);
        TMhSurgApply a = svc.get("H02");
        assertEquals(3, a.getStatus().intValue());
        assertEquals(1, a.getRecheckFlag().intValue());
        assertEquals(new Date(T0.getTime() + 24 * 3600_000L), a.getRecheckDeadline());
        assertEquals(SurgCheckView.STATE_RECHECK_PENDING, svc.traceChain("H02").getOverallState());

        // 放行后不许普通补签，必须走补核
        expectFail(() -> svc.sign("H02", "0", "N1", true, "护士", T1));
        svc.recheck("H02", "0", "N1", true, "护士-王", T1);
        // 未补齐不可归档
        expectFail(() -> svc.archive("H02", "病案", T1));
        for (String ri : ALL_ITEMS) {
            String[] p = ri.split(":");
            if ("0".equals(p[0]) && "N1".equals(p[1])) {
                continue;
            }
            svc.recheck("H02", p[0], p[1], true, "补-" + p[1], T1);
        }
        assertEquals(0, svc.get("H02").getRecheckFlag().intValue(), "补齐后待办自动核销");
        svc.archive("H02", "病案", T1);
        assertEquals(5, svc.get("H02").getStatus().intValue());
    }

    // ----------------------------------------------------------------
    // 9. 抢救补核超 24h：补签有效但不自动核销，须上级签字
    // ----------------------------------------------------------------

    @Test
    void emergencyRecheck_overdue_needsSupervisorApproval() {
        createEmergency("I01");
        svc.emergencyRelease("I01", "R-I01", "now", "排程", "主刀", "麻醉师", T0);
        for (String ri : ALL_ITEMS) {
            String[] p = ri.split(":");
            svc.recheck("I01", p[0], p[1], true, "补-" + p[1], T25H);
        }
        assertEquals(1, svc.get("I01").getRecheckFlag().intValue(), "超时限不自动核销");
        expectFail(() -> svc.archive("I01", "病案", T25H));
        // 没留审批单不让确认
        expectFail(() -> svc.approveOverdueRecheck("I01", "主任", "", T25H));
        svc.approveOverdueRecheck("I01", "主任-孙", "AP-20261007-01", T25H);
        assertEquals(0, svc.get("I01").getRecheckFlag().intValue());
        svc.archive("I01", "病案", T25H);
        assertEquals(5, svc.get("I01").getStatus().intValue());
    }

    // ----------------------------------------------------------------
    // 10. 补核不通过：落库留痕、待办不核销
    // ----------------------------------------------------------------

    @Test
    void recheckReject_isRecordedAndTodoStaysOpen() {
        createEmergency("J01");
        svc.emergencyRelease("J01", "R-J01", "now", "排程", "主刀", "麻醉师", T0);
        svc.recheck("J01", "0", "N1", false, "护士-王", T1); // 不通过也提交
        assertEquals(1, svc.get("J01").getRecheckFlag().intValue());
        SurgCheckView v = svc.traceChain("J01");
        boolean rowFound = v.getItems().stream().anyMatch(r ->
                "N1".equals(r.getItemKey()) && r.isEffective() && r.getResult() == 2
                        && "护士-王".equals(r.getSignedBy()));
        assertTrue(rowFound, "补核不通过必须落库留痕");
        // 归档仍被拦下
        expectFail(() -> svc.archive("J01", "病案", T1));
    }

    // ----------------------------------------------------------------
    // 11. 部位异议：病历与核查表不一致，不裁定不放行；以核查表＋影像为准并改病历
    // ----------------------------------------------------------------

    @Test
    void siteDispute_blocksRelease_untilRecordCorrected() {
        createDisputed("K01");
        SurgCheckView v0 = svc.traceChain("K01");
        assertEquals(SurgCheckView.STATE_SITE_DISPUTE, v0.getOverallState());
        assertTrue(v0.getBlocker().contains("病历更正"));

        // 三方全签也没用，异议没裁定
        signAll("K01", "核");
        IllegalStateException e = expectFail(() -> svc.release("K01", "R-K01", "09:00", "排程", T1));
        assertTrue(e.getMessage().contains("异议"), e.getMessage());

        // 空口裁定不算，必须有病历更正流水号
        expectFail(() -> svc.resolveSiteDispute("K01", "", "医务", T1));
        svc.resolveSiteDispute("K01", "EMR-FIX-8899", "医务", T1);
        svc.release("K01", "R-K01", "09:00", "排程", T1);
        assertEquals(3, svc.get("K01").getStatus().intValue());
    }

    @Test
    void siteDispute_emergencyVerdict_thenFixNoMustBeBackfilled() {
        // 抢救 + 左右写反
        svc.createApply("K02", "PK02", "床", "外科", "探查", "R", "L", 1, T0);
        // 没裁定连抢救也不能放行
        expectFail(() -> svc.emergencyRelease("K02", "R-K02", "now", "排程", "主刀", "麻醉师", T0));
        // 现场按影像裁定，必须给影像号
        expectFail(() -> svc.emergencySiteVerdict("K02", "", "主刀", T0));
        svc.emergencySiteVerdict("K02", "CT-20261006-007", "主刀", T0);
        svc.emergencyRelease("K02", "R-K02", "now", "排程", "主刀-赵", "麻醉师-钱", T0);

        // 补齐 10 项但病历更正流水号没补登 → 待办不核销
        for (String ri : ALL_ITEMS) {
            String[] p = ri.split(":");
            svc.recheck("K02", p[0], p[1], true, "补-" + p[1], T1);
        }
        assertEquals(1, svc.get("K02").getRecheckFlag().intValue());
        svc.resolveSiteDispute("K02", "EMR-FIX-9001", "医务", T1);
        // 补登后再触发一次补核动作来核销（任意已签项不能重签，走全部已齐检测：
        // 用上级确认不合适——未超时限；正确口径是补登流水号时直接核销）
        assertEquals(0, svc.get("K02").getRecheckFlag().intValue(),
                "病历更正补登且核查已齐，补核待办应核销");
    }

    // ----------------------------------------------------------------
    // 12. 转床：标变更继续用，护士重核身份/部位，术者麻醉随床转签
    // ----------------------------------------------------------------

    @Test
    void changeBed_nurseRechecks_otherSigsRideAlong() {
        create("L01");
        signAll("L01", "核");
        svc.changeBed("L01", "36床", "护士站", T1);

        TMhSurgApply a = svc.get("L01");
        assertEquals("36床", a.getBedNo());
        assertEquals(2, a.getSubjectVer().intValue());

        SurgCheckView v = svc.traceChain("L01");
        // 护士身份/部位出现新一轮未核行；旧轮通过行留痕但已失效
        int nurseEffectivePending = 0;
        boolean oldRoundKept = false;
        for (SurgCheckView.ItemRow r : v.getItems()) {
            if ("0".equals(r.getRoleCode()) && ("N1".equals(r.getItemKey()) || "N2".equals(r.getItemKey()))) {
                if (r.isEffective() && r.getResult() == 0) {
                    nurseEffectivePending++;
                }
                if (!r.isEffective() && r.getResult() == 1) {
                    oldRoundKept = true;
                }
            }
        }
        assertEquals(2, nurseEffectivePending, "N1/N2 必须重新核");
        assertTrue(oldRoundKept, "旧轮护士签署必须留痕");
        // 术者 3 项、麻醉 3 项仍然有效
        assertEquals(6, v.getItems().stream()
                .filter(r -> r.isEffective() && r.getResult() == 1
                        && ("1".equals(r.getRoleCode()) || "2".equals(r.getRoleCode())))
                .count());

        // 护士没重核完，放行仍被扣
        IllegalStateException e = expectFail(() -> svc.release("L01", "R-L01", "09:00", "排程", T1));
        assertTrue(e.getMessage().contains("患者身份") || e.getMessage().contains("部位标识"));
        svc.sign("L01", "0", "N1", true, "护士-王(新床复核)", T1);
        svc.sign("L01", "0", "N2", true, "护士-王(新床复核)", T1);
        svc.release("L01", "R-L01", "09:00", "排程", T1);
        assertEquals(3, svc.get("L01").getStatus().intValue());
    }

    // ----------------------------------------------------------------
    // 13. 换人：旧单作废，按新患者重建新版，旧签署一律不沿用
    // ----------------------------------------------------------------

    @Test
    void changePatient_voidsAndRebuildsFromScratch() {
        create("M01");
        signAll("M01", "核");
        String newNo = svc.changePatient("M01", "PNEW-9", "36床", "护士站", T1);
        assertEquals("M01-V2", newNo);

        assertEquals(4, svc.get("M01").getStatus().intValue());
        TMhSurgApply n = svc.get(newNo);
        assertEquals("PNEW-9", n.getSubjectNo());
        assertEquals(2, n.getVersionNo().intValue());
        assertEquals("M01", n.getRedoOf());

        SurgCheckView nv = svc.traceChain(newNo);
        assertEquals(SurgCheckView.STATE_NO_SIGN, nv.getOverallState(), "换了人，三方必须重新核");
        assertEquals(0, passCount(nv));
        // 旧单倒查能点到新单
        assertEquals(newNo, svc.traceChain("M01").getRedoTo());
        // 只是转床不许走换人
        expectFail(() -> svc.changePatient(newNo, "PNEW-9", "37床", "护士站", T1));
    }

    // ----------------------------------------------------------------
    // 14. 倒查三分开：没人核 / 核了没齐 / 不通过 / 查无单
    // ----------------------------------------------------------------

    @Test
    void traceChain_distinguishesNeverSignedFromRejection() {
        create("N01");
        SurgCheckView never = svc.traceChain("N01");
        assertEquals(SurgCheckView.STATE_NO_SIGN, never.getOverallState());
        assertTrue(never.getOverallText().contains("没人核"));
        assertFalse(never.getOverallText().contains("不通过"));

        svc.sign("N01", "1", "S1", true, "术者", T1);
        SurgCheckView going = svc.traceChain("N01");
        assertEquals(SurgCheckView.STATE_IN_PROGRESS, going.getOverallState());
        assertNotNull(going.getBlocker());

        create("N02");
        svc.sign("N02", "2", "A2", false, "麻醉", T1);
        assertEquals(SurgCheckView.STATE_REJECTED, svc.traceChain("N02").getOverallState());

        assertNull(svc.traceChain("NOT-EXIST"), "查无单返回 null，由调用方表述，不许混成不通过");
    }

    // ----------------------------------------------------------------
    // 15. 归档守卫：未放行不许归档；归档后封档
    // ----------------------------------------------------------------

    @Test
    void archive_guardsAndLocks() {
        create("O01");
        expectFail(() -> svc.archive("O01", "病案", T1)); // 未放行
        signAll("O01", "核");
        expectFail(() -> svc.archive("O01", "病案", T1)); // 仍未放行占位
        svc.release("O01", "R-O01", "09:00", "排程", T1);
        svc.archive("O01", "病案", T1);
        assertEquals(5, svc.get("O01").getStatus().intValue());
        assertNotNull(svc.get("O01").getArchiveAt());
        expectFail(() -> svc.archive("O01", "病案", T1));
        expectFail(() -> svc.sign("O01", "0", "N1", true, "护士", T1));
    }

    // ----------------------------------------------------------------
    // 16. 撤台原子释放手术间；撤台后才能作废重做，释放的时段可再被别单占用
    // ----------------------------------------------------------------

    @Test
    void cancelRelease_freesSlotAtomically_thenVoidRedoAllowed() {
        create("P01");
        signAll("P01", "核");
        svc.release("P01", "R-P01", "09:00", "排程", T1);
        // 未写原因不许撤
        expectFail(() -> svc.cancelRelease("P01", "", "排程", T1));
        svc.cancelRelease("P01", "术间设备故障，暂停", "排程员", T1);

        TMhSurgApply a = svc.get("P01");
        assertEquals(1, a.getStatus().intValue(), "退回核对中");
        assertNull(a.getReleaseAt());
        assertNull(a.getSlotKey());
        // 释放的时段立刻可以被别单占用
        create("P02");
        signAll("P02", "核");
        svc.release("P02", "R-P01", "09:00", "排程", T1);

        // 撤台后的单子要改已签项：走作废重做
        String newNo = svc.voidAndRedo("P01", "需更换术式", "主任", T1);
        assertEquals("P01-V2", newNo);
        assertEquals(4, svc.get("P01").getStatus().intValue());
    }

    // ----------------------------------------------------------------
    // 17. 抢救但三方本已齐：放行不留空补核待办，可直接归档
    // ----------------------------------------------------------------

    @Test
    void emergencyFullyChecked_noEmptyRecheckTodo() {
        createEmergency("Q01");
        signAll("Q01", "核");
        svc.emergencyRelease("Q01", "R-Q01", "now", "排程", "主刀-赵", "麻醉师-钱", T0);
        TMhSurgApply a = svc.get("Q01");
        assertEquals(0, a.getRecheckFlag().intValue(), "核查本已齐，不应挂补核待办");
        assertNull(a.getRecheckDeadline());
        svc.archive("Q01", "病案", T1);
        assertEquals(5, svc.get("Q01").getStatus().intValue());
        // 没有待办时不许补核
        expectFail(() -> svc.recheck("Q01", "0", "N1", true, "护士", T1));
    }
}
