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
 * 危急值判定记录 对象 t_mh_crit_value
 *
 * @author fuce
 * @date 2026-09-14
 */
@TableName("t_mh_crit_value")
@ApiModel(value = "TMhCritValue", description = "危急值判定记录")
public class TMhCritValue implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 标本唯一编号 */
    @TableField("specimen_no")
    @ApiModelProperty(value = "标本唯一编号")
    private String specimenNo;

    /** 检验项目代号 */
    @TableField("item_code")
    @ApiModelProperty(value = "检验项目代号")
    private String itemCode;

    /** 检验项目名 */
    @TableField("item_name")
    @ApiModelProperty(value = "检验项目名")
    private String itemName;

    /** 上机读数 */
    @TableField("raw_value")
    @ApiModelProperty(value = "上机读数")
    private BigDecimal rawValue;

    /** 参考区间上限 */
    @TableField("ref_high")
    @ApiModelProperty(value = "参考区间上限")
    private BigDecimal refHigh;

    /** 机器自动判定 0正常 1危急 */
    @TableField("auto_flag")
    @ApiModelProperty(value = "机器自动判定 0正常 1危急")
    private Integer autoFlag;

    /** 人工复核判定 0正常 1危急 2推翻为误报 */
    @TableField("manual_flag")
    @ApiModelProperty(value = "人工复核判定 0正常 1危急 2推翻为误报")
    private Integer manualFlag;

    /** 复核人 */
    @TableField("review_by")
    @ApiModelProperty(value = "复核人")
    private String reviewBy;

    /** 复核时刻 */
    @TableField("review_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "复核时刻")
    private Date reviewAt;

    /** 判为危急的时刻 */
    @TableField("crit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "判为危急的时刻")
    private Date critTime;

    /** 记录情形 0待复核 1已确认危急 2已推翻 3已处置闭环 */
    @TableField("status")
    @ApiModelProperty(value = "记录情形 0待复核 1已确认危急 2已推翻 3已处置闭环")
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

    public String getSpecimenNo() {
        return specimenNo;
    }

    public void setSpecimenNo(String specimenNo) {
        this.specimenNo = specimenNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public BigDecimal getRawValue() {
        return rawValue;
    }

    public void setRawValue(BigDecimal rawValue) {
        this.rawValue = rawValue;
    }

    public BigDecimal getRefHigh() {
        return refHigh;
    }

    public void setRefHigh(BigDecimal refHigh) {
        this.refHigh = refHigh;
    }

    public Integer getAutoFlag() {
        return autoFlag;
    }

    public void setAutoFlag(Integer autoFlag) {
        this.autoFlag = autoFlag;
    }

    public Integer getManualFlag() {
        return manualFlag;
    }

    public void setManualFlag(Integer manualFlag) {
        this.manualFlag = manualFlag;
    }

    public String getReviewBy() {
        return reviewBy;
    }

    public void setReviewBy(String reviewBy) {
        this.reviewBy = reviewBy;
    }

    public Date getReviewAt() {
        return reviewAt;
    }

    public void setReviewAt(Date reviewAt) {
        this.reviewAt = reviewAt;
    }

    public Date getCritTime() {
        return critTime;
    }

    public void setCritTime(Date critTime) {
        this.critTime = critTime;
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
