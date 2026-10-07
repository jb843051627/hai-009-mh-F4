package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TMhCritNotify;
import com.fc.v2.model.auto.TMhCritValue;

/**
 * 危急值判定与通知处置 Service 业务层接口。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhCritValueService {

    /** 按标本号取该标本上待复核的危急值候选 */
    List<TMhCritValue> listPending(String specimenNo);

    /** 检验师人工复核：1=确认危急 2=推翻为误报 */
    TMhCritValue review(Long id, int manualFlag, String reviewer, Date at);

    /**
     * 危急值通知一轮：同标本多条危急合并成一条通知，按路径重试到接通或到硬时限。
     * 返回本轮产生的通知记录条数（0=无待通知的危急值）。
     */
    int notifyOnce(String specimenNo, Date at);

    /** 临床处置回填；回填即闭环 */
    TMhCritNotify deal(Long notifyId, String result, String handler, Date at);

    /** 超时限升级告警：把该标本仍未接通的危急值升级给主管医生 */
    int escalate(String specimenNo, Date at);

    /** 倒查一条危急值：判定→通知→处置，缺哪一环明写缺哪一环 */
    String trace(String specimenNo);
}