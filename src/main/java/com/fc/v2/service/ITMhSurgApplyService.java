package com.fc.v2.service;

import java.util.Date;

import com.fc.v2.model.auto.TMhSurgApply;

/**
 * 三方并行会签与放行占用 Service 接口。
 *
 * <p>口径（说死版，详见 doc/surg-signoff-policy.md）：</p>
 * <ul>
 * <li>三方角色并行会签，互不排队；任一方未签，整单扣在那一方，不得放行。</li>
 * <li>三方齐备后放行与占用资源位在同一事务里一起成，不允许只成一半。</li>
 * <li>紧急单允许「先放行、事后补核」：必须登记决策人与理由，留补核待办与 24 小时时限；
 *     但已落定「不通过」项或部位异议未处置的单，紧急放行同样不允许。</li>
 * <li>核查单部位与病历档案不一致：先标异议冻结放行，以更正后的病历档案为准；
 *     更正时已有签署的，已签项全部作废、整单重做。</li>
 * <li>术前换人（患者身份变更）：已签三方一律作废，整单重做；
 *     转床/换间（患者不变）：标变更继续用，已签有效。</li>
 * <li>已签项不可涂改，只能整单作废重做（版本号递增，旧版留痕）。</li>
 * <li>归档与落库同一事务；普通单须三方齐备，紧急单须补核待办已清。</li>
 * <li>多单并行按单据编号隔离，状态迁移走条件更新，不串台、不重复放行。</li>
 * <li>倒查逐环明写：一份未提交明说无人核对，不与「核对不通过」混为一谈。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-07
 */
public interface ITMhSurgApplyService {

    /** 建单并按目录铺好三方会签项（护理4项/术者4项/麻醉3项），三方同时可签 */
    String createApply(String applyNo, String subjectNo, String deptName,
                       String siteFlag, Date at);

    /** 三方并行会签：任一角色可在任意时刻提交自己那几项，不排先后；已签项不可涂改 */
    int sign(String applyNo, String roleCode, String itemKey, int result,
             String signer, Date at);

    /** 三方齐备后放行并占用资源位：两者同一事务一起成，不允许只成一半 */
    boolean release(String applyNo, String resource, String operator, Date at);

    /** 紧急单先放行：允许未齐备就放行，但必须登记决策理由，并留事后补核待办与时限 */
    boolean emergencyRelease(String applyNo, String resource, String reason,
                             String operator, Date at);

    /** 事后补核：补签待办项，补完（或补核不通过定性）清掉待办 */
    int recheck(String applyNo, String roleCode, String itemKey, int result,
                String signer, Date at);

    /** 已签项不可涂改：整单作废重做，版本号递增，旧版本留痕 */
    boolean voidAndRedo(String applyNo, String reason, String operator, Date at);

    /** 取消不重做：单据进终态作废留底（可归档）；已开台（已占位）的不可取消 */
    boolean cancelApply(String applyNo, String reason, String operator, Date at);

    /** 部位异议：核查单与病历档案不一致时标异议，冻结放行，待临床处置 */
    boolean reportSiteDispute(String applyNo, String operator, Date at);

    /** 部位异议处置：以更正后的病历档案为准；已有签署的，已签项全部作废、整单重做 */
    boolean resolveSiteDispute(String applyNo, String correctedSiteFlag,
                               String operator, Date at);

    /** 术前换人：患者身份变更，已签三方一律作废，整单重做 */
    boolean changeSubject(String applyNo, String newSubjectNo, String operator, Date at);

    /** 术前转床/换间：患者不变，标变更继续用，已签有效，留变更事件 */
    boolean markTransfer(String applyNo, String note, String operator, Date at);

    /** 归档：与落库一起成，做了一半不算；补核待办未清不可归档 */
    boolean archive(String applyNo, String operator, Date at);

    /** 按单据编号倒查全链；只读本单据，多单并行互不串台 */
    String traceChain(String applyNo);

    /** 取单据 */
    TMhSurgApply get(String applyNo);
}
