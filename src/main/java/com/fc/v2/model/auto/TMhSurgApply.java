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
 * 三方并行核对单据主表
 */
@TableName("t_mh_surg_apply")
@ApiModel(value = "TMhSurgApply")
public class TMhSurgApply implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;
    /** 单据唯一编号 */
    @ApiModelProperty(value = "单据唯一编号")
    private String applyNo;
    /** 关联对象编号 */
    @ApiModelProperty(value = "关联对象编号")
    private String subjectNo;
    /** 归属科室 */
    @ApiModelProperty(value = "归属科室")
    private String deptName;
    /** 术式代号 */
    @ApiModelProperty(value = "术式代号")
    private String procedureCode;
    /** 术式名称 */
    @ApiModelProperty(value = "术式名称")
    private String procedureName;
    /** 部位标识 L/R/N */
    @ApiModelProperty(value = "部位标识 L/R/N")
    private String siteFlag;
    /** 部位取自哪份 0单据 1档案 2有异议 */
    @ApiModelProperty(value = "部位取自哪份 0单据 1档案 2有异议")
    private String siteSource;
    /** 是否紧急 0否 1是 */
    @ApiModelProperty(value = "是否紧急 0否 1是")
    private Integer urgentFlag;
    /** 单据情形 0待核 1核对中 2已通过 3已占位 4已作废 5已归档 */
    @ApiModelProperty(value = "单据情形 0待核 1核对中 2已通过 3已占位 4已作废 5已归档")
    private Integer status;
    /** 版本号，作废重做时递增 */
    @ApiModelProperty(value = "版本号，作废重做时递增")
    private Integer versionNo;
    /** 是否待事后补核 0否 1是 */
    @ApiModelProperty(value = "是否待事后补核 0否 1是")
    private Integer recheckFlag;
    /** 事后补核时限 */
    @ApiModelProperty(value = "事后补核时限")
    private Date recheckDeadline;
    /** 放行时刻 */
    @ApiModelProperty(value = "放行时刻")
    private Date releaseAt;
    /** 放行后占用的资源位 */
    @ApiModelProperty(value = "放行后占用的资源位")
    private String occupyResource;
    /** 占用时刻 */
    @ApiModelProperty(value = "占用时刻")
    private Date occupyAt;
    /** 归档时刻 */
    @ApiModelProperty(value = "归档时刻")
    private Date archiveAt;
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

    public String getSubjectNo() {
        return subjectNo;
    }

    public void setSubjectNo(String subjectNo) {
        this.subjectNo = subjectNo;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getProcedureCode() {
        return procedureCode;
    }

    public void setProcedureCode(String procedureCode) {
        this.procedureCode = procedureCode;
    }

    public String getProcedureName() {
        return procedureName;
    }

    public void setProcedureName(String procedureName) {
        this.procedureName = procedureName;
    }

    public String getSiteFlag() {
        return siteFlag;
    }

    public void setSiteFlag(String siteFlag) {
        this.siteFlag = siteFlag;
    }

    public String getSiteSource() {
        return siteSource;
    }

    public void setSiteSource(String siteSource) {
        this.siteSource = siteSource;
    }

    public Integer getUrgentFlag() {
        return urgentFlag;
    }

    public void setUrgentFlag(Integer urgentFlag) {
        this.urgentFlag = urgentFlag;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Integer getRecheckFlag() {
        return recheckFlag;
    }

    public void setRecheckFlag(Integer recheckFlag) {
        this.recheckFlag = recheckFlag;
    }

    public Date getRecheckDeadline() {
        return recheckDeadline;
    }

    public void setRecheckDeadline(Date recheckDeadline) {
        this.recheckDeadline = recheckDeadline;
    }

    public Date getReleaseAt() {
        return releaseAt;
    }

    public void setReleaseAt(Date releaseAt) {
        this.releaseAt = releaseAt;
    }

    public String getOccupyResource() {
        return occupyResource;
    }

    public void setOccupyResource(String occupyResource) {
        this.occupyResource = occupyResource;
    }

    public Date getOccupyAt() {
        return occupyAt;
    }

    public void setOccupyAt(Date occupyAt) {
        this.occupyAt = occupyAt;
    }

    public Date getArchiveAt() {
        return archiveAt;
    }

    public void setArchiveAt(Date archiveAt) {
        this.archiveAt = archiveAt;
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
