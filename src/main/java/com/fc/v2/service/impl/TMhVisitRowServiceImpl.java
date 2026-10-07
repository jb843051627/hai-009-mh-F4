package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TMhVisitRowMapper;
import com.fc.v2.model.auto.TMhVisitRow;
import com.fc.v2.service.ITMhVisitRowService;

/**
 * 门诊就诊记录导入核对行 Service 业务层处理（batch-process 形状）。
 * <p>本环节逻辑尚未实现，整批导入核收与错误报告导出待后续迭代补齐。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhVisitRowServiceImpl implements ITMhVisitRowService {

    @javax.annotation.Resource
    private TMhVisitRowMapper mhVisitRowMapper;

    @Override
    public TMhVisitRow selectTMhVisitRowById(Long id) {
        return this.mhVisitRowMapper.selectById(id);
    }

    @Override
    public int submitBatch(String batchNo, List<TMhVisitRow> rows) {
        // 基线：整批导入核收尚未实现
        return 0;
    }

    @Override
    public List<TMhVisitRow> listErrors(String batchNo) {
        // 基线：错误报告导出尚未实现
        return new java.util.ArrayList<TMhVisitRow>();
    }
}