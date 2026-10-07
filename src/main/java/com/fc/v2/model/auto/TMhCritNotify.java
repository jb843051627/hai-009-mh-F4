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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 危急值通知处置记录 对象 t_mh_crit_notify
 *
 * @author fuce
 * @date 2026-09-14
 */
@TableName("t_mh_crit_notify")
@ApiModel(value = "TMhCritNotify", description = "危急值通知处置记录")
public class TMhCritNotify implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 合并通知批次号（同标本多危急合并成一条） */
    @TableField("batch_no")
    @ApiModelProperty(value = "合并通知批次号（同标本多危急合并成一条）")
    private String batchNo;

    /** 标本唯一编号 */
    @TableField("specimen_no")
    @ApiModelProperty(value = "标本唯一编号")
    private String specimenNo;

    /** 通知对象 */
    @TableField("target_user")
    @ApiModelProperty(value = "通知对象")
    private String targetUser;

    /** 通知路径 0护士站 1主管医生 */
    @TableField("notify_path")
    @ApiModelProperty(value = "通知路径 0护士站 1主管医生")
    private Integer notifyPath;

    /** 已重试次数 */
    @TableField("try_count")
    @ApiModelProperty(value = "已重试次数")
    private Integer tryCount;

    /** 是否接通 0未通 1已通 */
    @TableField("notified")
    @ApiModelProperty(value = "是否接通 0未通 1已通")
    private Integer notified;

    /** 首次通知时刻 */
    @TableField("first_notify_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "首次通知时刻")
    private Date firstNotifyAt;

    /** 最近一次通知时刻 */
    @TableField("last_notify_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最近一次通知时刻")
    private Date lastNotifyAt;

    /** 通知时限（判危急起算的硬时限） */
    @TableField("deadline_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "通知时限（判危急起算的硬时限）")
    private Date deadlineAt;

    /** 是否超时限 0否 1是 */
    @TableField("timeout_flag")
    @ApiModelProperty(value = "是否超时限 0否 1是")
    private Integer timeoutFlag;

    /** 超时限升级给谁 */
    @TableField("upgrade_user")
    @ApiModelProperty(value = "超时限升级给谁")
    private String upgradeUser;

    /** 升级时刻 */
    @TableField("upgrade_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "升级时刻")
    private Date upgradeAt;

    /** 临床处置回填 */
    @TableField("deal_result")
    @ApiModelProperty(value = "临床处置回填")
    private String dealResult;

    /** 处置人 */
    @TableField("deal_by")
    @ApiModelProperty(value = "处置人")
    private String dealBy;

    /** 处置时刻 */
    @TableField("deal_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "处置时刻")
    private Date dealAt;

    /** 闭环 0未闭环 1已闭环 */
    @TableField("closed")
    @ApiModelProperty(value = "闭环 0未闭环 1已闭环")
    private Integer closed;

    /** 是否误报推翻 0否 1是 */
    @TableField("false_flag")
    @ApiModelProperty(value = "是否误报推翻 0否 1是")
    private Integer falseFlag;

    /** 记录情形 0在通知 1已闭环 2已降级 */
    @TableField("status")
    @ApiModelProperty(value = "记录情形 0在通知 1已闭环 2已降级")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getSpecimenNo() {
        return specimenNo;
    }

    public void setSpecimenNo(String specimenNo) {
        this.specimenNo = specimenNo;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public Integer getNotifyPath() {
        return notifyPath;
    }

    public void setNotifyPath(Integer notifyPath) {
        this.notifyPath = notifyPath;
    }

    public Integer getTryCount() {
        return tryCount;
    }

    public void setTryCount(Integer tryCount) {
        this.tryCount = tryCount;
    }

    public Integer getNotified() {
        return notified;
    }

    public void setNotified(Integer notified) {
        this.notified = notified;
    }

    public Date getFirstNotifyAt() {
        return firstNotifyAt;
    }

    public void setFirstNotifyAt(Date firstNotifyAt) {
        this.firstNotifyAt = firstNotifyAt;
    }

    public Date getLastNotifyAt() {
        return lastNotifyAt;
    }

    public void setLastNotifyAt(Date lastNotifyAt) {
        this.lastNotifyAt = lastNotifyAt;
    }

    public Date getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Date deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public Integer getTimeoutFlag() {
        return timeoutFlag;
    }

    public void setTimeoutFlag(Integer timeoutFlag) {
        this.timeoutFlag = timeoutFlag;
    }

    public String getUpgradeUser() {
        return upgradeUser;
    }

    public void setUpgradeUser(String upgradeUser) {
        this.upgradeUser = upgradeUser;
    }

    public Date getUpgradeAt() {
        return upgradeAt;
    }

    public void setUpgradeAt(Date upgradeAt) {
        this.upgradeAt = upgradeAt;
    }

    public String getDealResult() {
        return dealResult;
    }

    public void setDealResult(String dealResult) {
        this.dealResult = dealResult;
    }

    public String getDealBy() {
        return dealBy;
    }

    public void setDealBy(String dealBy) {
        this.dealBy = dealBy;
    }

    public Date getDealAt() {
        return dealAt;
    }

    public void setDealAt(Date dealAt) {
        this.dealAt = dealAt;
    }

    public Integer getClosed() {
        return closed;
    }

    public void setClosed(Integer closed) {
        this.closed = closed;
    }

    public Integer getFalseFlag() {
        return falseFlag;
    }

    public void setFalseFlag(Integer falseFlag) {
        this.falseFlag = falseFlag;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}
