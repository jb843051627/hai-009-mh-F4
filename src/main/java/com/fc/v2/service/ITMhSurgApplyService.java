package com.fc.v2.service;

import java.util.Date;

import com.fc.v2.model.auto.TMhSurgApply;
import com.fc.v2.model.custom.surg.SurgCheckView;

/**
 * 术前三方并行核查会签 Service 接口。
 *
 * <p>死口径（实现必须逐条满足，详见 impl 类注释）：</p>
 * <ol>
 *   <li>护士/术者/麻醉三方并行签，互不排队；任一方未齐，单扣在未齐方，不放行、不占位。</li>
 *   <li>核查表部位与病历不一致即为异议，以核查表＋影像为准，但病历未按裁定更正前不得放行。</li>
 *   <li>抢救手术口径：允许先放行占位、事后补核（时限 24h），但放行当时必须有术者与麻醉
 *       两方授权人双签授权；择期手术一律齐了才开台。</li>
 *   <li>转床：标变更继续用，护士方身份/部位项重新核对，术者、麻醉签署随床转签留痕。</li>
 *   <li>换人（患者变了）：旧单直接作废，按新患者重建新版核查单，不得沿用。</li>
 *   <li>已签项不可涂改；要改只能整单作废重做，旧版留痕。</li>
 *   <li>放行与手术间排程占用同一事务原子成，uk_slot 唯一键兜底防重台，败者整笔回滚。</li>
 *   <li>倒查按单号隔离，多台并行不串台；无人核/核了未齐/不通过/异议/待补核分着说。</li>
 * </ol>
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITMhSurgApplyService {

    /**
     * 建单并铺三方核查项。核查表部位与病历部位不一致时自动置为异议态。
     *
     * @param applyNo        核查单编号
     * @param subjectNo      患者号
     * @param bedNo          床号
     * @param deptName       科室
     * @param procedureName  术式
     * @param siteFlag       核查表/现场标识部位 L/R/N
     * @param siteFlagRecord 病历记载部位 L/R/N（可空）
     * @param urgentFlag     是否抢救 0择期 1抢救
     * @param at             建单时刻
     * @return 单号
     */
    String createApply(String applyNo, String subjectNo, String bedNo, String deptName,
                       String procedureName, String siteFlag, String siteFlagRecord,
                       int urgentFlag, Date at);

    /**
     * 三方并行签署单个核查项。已签项（含不通过）不可再改，要改走作废重做。
     *
     * @param pass true通过 false不通过（不通过即扣在该方，只能作废重做）
     */
    void sign(String applyNo, String roleCode, String itemKey, boolean pass,
              String signer, Date at);

    /**
     * 择期放行：三方齐备、无驳回、无部位异议时，放行与手术间时段占用同一事务成。
     */
    void release(String applyNo, String roomNo, String timeSlot, String operator, Date at);

    /**
     * 抢救先放行：允许核查未齐开台，但术者授权人与麻醉授权人必须同时在场双授权，
     * 放行即挂 24h 事后补核待办，且同样原子占用手术间。
     */
    void emergencyRelease(String applyNo, String roomNo, String timeSlot, String operator,
                          String surgeonAuthorizer, String anesAuthorizer, Date at);

    /**
     * 事后补核：抢救放行后逐项补签，全部补齐且未超时限则清掉待办。
     */
    void recheck(String applyNo, String roleCode, String itemKey, boolean pass,
                 String signer, Date at);

    /**
     * 超时限补核的上级签字确认：全部补齐后仍超 24h 的，不自动核销，须上级医师签字才闭环。
     */
    void approveOverdueRecheck(String applyNo, String approver, String approvalNo, Date at);

    /**
     * 部位异议裁定：以核查表＋影像为准，凭病历更正流水号登记，更正完成后方可继续放行。
     */
    void resolveSiteDispute(String applyNo, String recordFixNo, String operator, Date at);

    /**
     * 抢救现场部位裁定：台上以影像为准由术者当场拍板，病历更正流水号允许补核时限内补登。
     * 仅抢救单可用，且必须登记影像号留证。
     */
    void emergencySiteVerdict(String applyNo, String imageNo, String operator, Date at);

    /**
     * 转床：标变更继续用，对象版本＋1；护士身份/部位项重新核对，术者麻醉签署随床转签。
     */
    void changeBed(String applyNo, String newBedNo, String operator, Date at);

    /**
     * 换人：旧单作废，按新患者重建新版核查单，返回新单号。
     */
    String changePatient(String oldApplyNo, String newSubjectNo, String newBedNo,
                         String operator, Date at);

    /**
     * 整单作废重做（已签项要改的唯一合法路径），旧单留痕，返回新单号。
     * 已放行占位的单必须先撤台 {@link #cancelRelease} 释放手术间。
     */
    String voidAndRedo(String applyNo, String reason, String operator, Date at);

    /**
     * 撤台：开台前撤销放行，同事务释放手术间时段（删占用行）并清掉放行/补核状态，
     * 单据退回核对中，已签项保留。已归档（手术已执行）不可撤。
     */
    void cancelRelease(String applyNo, String reason, String operator, Date at);

    /**
     * 归档：核查齐备（或抢救单补核已清）才可归档，状态与归档时刻一笔落库。
     */
    void archive(String applyNo, String operator, Date at);

    /**
     * 按单号倒查全链：核查项逐项结果、卡在谁手上、放行/占位/补核/变更事件一笔笔列清。
     * 单号不存在返回 null（由调用方与「无人核」区分表述）。
     */
    SurgCheckView traceChain(String applyNo);

    /** 取单据 */
    TMhSurgApply get(String applyNo);
}
