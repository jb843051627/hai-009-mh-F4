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
 * 并行核对条目表
 */
@TableName("t_mh_surg_sign")
@ApiModel(value = "TMhSurgSign")
public class TMhSurgSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;
    /** 单据编号 */
    @ApiModelProperty(value = "单据编号")
    private String applyNo;
    /** 核对角色 0角色甲 1角色乙 2角色丙 */
    @ApiModelProperty(value = "核对角色 0角色甲 1角色乙 2角色丙")
    private String roleCode;
    /** 核对项键 */
    @ApiModelProperty(value = "核对项键")
    private String itemKey;
    /** 核对项名称 */
    @ApiModelProperty(value = "核对项名称")
    private String itemName;
    /** 核对结果 0未核 1通过 2不通过 3作废重做 */
    @ApiModelProperty(value = "核对结果 0未核 1通过 2不通过 3作废重做")
    private Integer signResult;
    /** 核对人 */
    @ApiModelProperty(value = "核对人")
    private String signedBy;
    /** 核对时刻 */
    @ApiModelProperty(value = "核对时刻")
    private Date signedAt;
    /** 提交批次号（并行应同为 0，无先后） */
    @ApiModelProperty(value = "提交批次号（并行应同为 0，无先后）")
    private Integer signSeq;
    /** 签署时的核查单版本，旧版签署随旧单留痕不改写 */
    @ApiModelProperty(value = "签署时的核查单版本，旧版签署随旧单留痕不改写")
    private Integer versionNo;
    /** 同一版本内的核对轮次，转床后护士重核递增、旧轮留痕 */
    @ApiModelProperty(value = "同一版本内的核对轮次，转床后护士重核递增、旧轮留痕")
    private Integer roundNo;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public Integer getSignResult() {
        return signResult;
    }

    public void setSignResult(Integer signResult) {
        this.signResult = signResult;
    }

    public String getSignedBy() {
        return signedBy;
    }

    public void setSignedBy(String signedBy) {
        this.signedBy = signedBy;
    }

    public Date getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(Date signedAt) {
        this.signedAt = signedAt;
    }

    public Integer getSignSeq() {
        return signSeq;
    }

    public void setSignSeq(Integer signSeq) {
        this.signSeq = signSeq;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
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
