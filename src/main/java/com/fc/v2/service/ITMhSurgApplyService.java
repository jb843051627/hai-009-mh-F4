package com.fc.v2.service;

import java.util.Date;

import com.fc.v2.model.auto.TMhSurgApply;

/**
 * 三方并行会签与放行占用 Service 接口
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITMhSurgApplyService {

    /** 建单并铺好三方会签项（每角色两项，三方同时可签） */
    String createApply(String applyNo, String subjectNo, String deptName,
                       String siteFlag, Date at);

    /** 三方并行会签：任一角色可在任意时刻提交自己那两项，不排先后；已签项不可涂改 */
    int sign(String applyNo, String roleCode, String itemKey, int result,
             String signer, Date at);

    /** 三方齐备后放行并占用资源位：两者同一事务一起成，不允许只成一半 */
    boolean release(String applyNo, String resource, String operator, Date at);

    /** 紧急单先放行：允许未齐备就放行，但必须留事后补核待办与时限 */
    boolean emergencyRelease(String applyNo, String resource, String operator, Date at);

    /** 事后补核：补签待办项，补完清掉待办 */
    int recheck(String applyNo, String roleCode, String itemKey, int result,
                String signer, Date at);

    /** 已签项不可涂改：整单作废重做，版本号递增，旧版本留痕 */
    boolean voidAndRedo(String applyNo, String reason, String operator, Date at);

    /** 归档：与落库一起成，做了一半不算 */
    boolean archive(String applyNo, String operator, Date at);

    /** 按单据编号倒查全链；只读本单据，多单并行互不串台 */
    String traceChain(String applyNo);

    /** 取单据 */
    TMhSurgApply get(String applyNo);
}