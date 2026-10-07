package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 单据动作事件表
 */
@TableName("t_mh_surg_trace")
@ApiModel(value = "TMhSurgTrace")
public class TMhSurgTrace implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;
    /** 单据编号 */
    @ApiModelProperty(value = "单据编号")
    private String applyNo;
    /** 动作码 建立/核对/放行/占位/补核/作废/归档 */
    @ApiModelProperty(value = "动作码 建立/核对/放行/占位/补核/作废/归档")
    private String actionCode;
    /** 操作人 */
    @ApiModelProperty(value = "操作人")
    private String actionBy;
    /** 动作时刻 */
    @ApiModelProperty(value = "动作时刻")
    private Date actionAt;
    /** 动作说明 */
    @ApiModelProperty(value = "动作说明")
    private String detail;
    /**  */
    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty(value = "create_by")
    private String createBy;
    /**  */
    @TableField(fill = FieldFill.INSERT)
    @ApiModelProperty(value = "create_time")
    private Date createTime;
    /**  */
    @TableField(fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "update_by")
    private String updateBy;
    /**  */
    @TableField(fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "update_time")
    private Date updateTime;
    /**  */
    @ApiModelProperty(value = "del_flag")
    private Integer delFlag;
    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getActionBy() {
        return actionBy;
    }

    public void setActionBy(String actionBy) {
        this.actionBy = actionBy;
    }

    public Date getActionAt() {
        return actionAt;
    }

    public void setActionAt(Date actionAt) {
        this.actionAt = actionAt;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }}
