package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhTriaRuleMapper;
import com.fc.v2.model.auto.TMhTriaRule;

/**
 * TMhTriaRule 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhTriaRuleService {

    /** selectTMhTriaRuleById */
    TMhTriaRule selectTMhTriaRuleById(Long id);

    /** listAvailable */
    List<TMhTriaRule> listAvailable(Date at);

    /** evaluate */
    int evaluate(String ruleCode, BigDecimal input, Date at);

    /** evaluateTop */
    int evaluateTop(BigDecimal input, Date at);

    /** usable */
    boolean usable(Long id, Date at);

    /** countAvailable */
    int countAvailable(Date at);

}
