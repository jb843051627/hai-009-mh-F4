package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 手术间排程占用表：uk_slot 唯一键兜底，同一手术间同一时段只能被一张放行单占用。
 */
@TableName("t_mh_surg_slot")
@ApiModel(value = "TMhSurgSlot")
public class TMhSurgSlot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;
    /** 排程时段键 手术间+时段 */
    @ApiModelProperty(value = "排程时段键 手术间+时段")
    private String slotKey;
    /** 手术间 */
    @ApiModelProperty(value = "手术间")
    private String roomNo;
    /** 排程时段 */
    @ApiModelProperty(value = "排程时段")
    private String timeSlot;
    /** 占用该时段的放行单号 */
    @ApiModelProperty(value = "占用该时段的放行单号")
    private String applyNo;
    /** 占用时刻 */
    @ApiModelProperty(value = "占用时刻")
    private Date occupyAt;
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

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getRoomNo() {
        return roomNo;
    }

    public void setRoomNo(String roomNo) {
        this.roomNo = roomNo;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(String timeSlot) {
        this.timeSlot = timeSlot;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Date getOccupyAt() {
        return occupyAt;
    }

    public void setOccupyAt(Date occupyAt) {
        this.occupyAt = occupyAt;
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
    }
}
