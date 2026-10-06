package com.fc.v2.service;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhVisitRowMapper;
import com.fc.v2.model.auto.TMhVisitRow;

/**
 * TMhVisitRow 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhVisitRowService {

    /** selectTMhVisitRowById */
    TMhVisitRow selectTMhVisitRowById(Long id);

    /** submitBatch */
    int submitBatch(String batchNo, List<TMhVisitRow> rows);

    /** listErrors */
    List<TMhVisitRow> listErrors(String batchNo);

}
