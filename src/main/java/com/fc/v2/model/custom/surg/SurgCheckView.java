package com.fc.v2.model.custom.surg;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 一台手术核查单的倒查视图：屏上和库里同一份数，逐项可倒着看。
 *
 * <p>overallState 把容易混淆的三种情形分开给：</p>
 * <ul>
 *   <li>{@link #STATE_NO_SIGN} 一份都没提交过——这台还没人核（不是不通过）；</li>
 *   <li>{@link #STATE_IN_PROGRESS} 有人核了但没齐，blocker 写明卡在谁手上；</li>
 *   <li>{@link #STATE_REJECTED} 有核查项不通过。</li>
 * </ul>
 */
public class SurgCheckView {

    /** 一份都没提交过，这台还没人核 */
    public static final String STATE_NO_SIGN = "NO_SIGN";
    /** 核查中（已有人提交但未齐） */
    public static final String STATE_IN_PROGRESS = "IN_PROGRESS";
    /** 有项不通过，单被扣下 */
    public static final String STATE_REJECTED = "REJECTED";
    /** 核查表与病历部位不一致，异议未裁定 */
    public static final String STATE_SITE_DISPUTE = "SITE_DISPUTE";
    /** 三方齐备，待放行 */
    public static final String STATE_READY = "READY";
    /** 已放行并占用手术间 */
    public static final String STATE_RELEASED = "RELEASED";
    /** 抢救先放行，待事后补核 */
    public static final String STATE_RECHECK_PENDING = "RECHECK_PENDING";
    /** 已作废，已重做至另一张单 */
    public static final String STATE_VOID = "VOID";
    /** 已归档 */
    public static final String STATE_ARCHIVED = "ARCHIVED";

    private String applyNo;
    private Integer versionNo;
    private String redoOf;
    private String redoTo;
    private String statusText;
    private String overallState;
    private String overallText;
    /** 卡在哪一方/哪一项，人话描述；无卡点为空 */
    private String blocker;

    private boolean urgent;
    private String subjectNo;
    private String bedNo;
    private Integer subjectVer;
    private String deptName;
    private String procedureName;

    private String siteFlag;
    private String siteFlagRecord;
    /** 0一致 1已按核查表更正病历 2异议 */
    private String siteSource;
    private String siteStateText;
    private String recordFixNo;

    private boolean recheckPending;
    private Date recheckDeadline;

    private Date releaseAt;
    private String occupyResource;
    private String slotKey;
    private Date occupyAt;
    private Date archiveAt;

    private final List<RoleBlock> roles = new ArrayList<>();
    private final List<ItemRow> items = new ArrayList<>();
    private final List<EventRow> events = new ArrayList<>();

    public static class RoleBlock {
        private String roleCode;
        private String roleName;
        /** 未提交 / 未齐 / 通过 / 不通过 */
        private String state;
        private final List<String> pendingItems = new ArrayList<>();

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public List<String> getPendingItems() {
            return pendingItems;
        }
    }

    public static class ItemRow {
        private String roleCode;
        private String roleName;
        private String itemKey;
        private String itemName;
        /** 0未核 1通过 2不通过 3已随旧版/转床失效 */
        private Integer result;
        private String resultText;
        private String signedBy;
        private Date signedAt;
        private Integer versionNo;
        private Integer roundNo;
        /** 是否当前有效签署（转床/旧版留痕行标 false） */
        private boolean effective;

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
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

        public Integer getResult() {
            return result;
        }

        public void setResult(Integer result) {
            this.result = result;
        }

        public String getResultText() {
            return resultText;
        }

        public void setResultText(String resultText) {
            this.resultText = resultText;
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

        public boolean isEffective() {
            return effective;
        }

        public void setEffective(boolean effective) {
            this.effective = effective;
        }
    }

    public static class EventRow {
        private String actionCode;
        private String actionBy;
        private Date actionAt;
        private String detail;

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
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getRedoOf() {
        return redoOf;
    }

    public void setRedoOf(String redoOf) {
        this.redoOf = redoOf;
    }

    public String getRedoTo() {
        return redoTo;
    }

    public void setRedoTo(String redoTo) {
        this.redoTo = redoTo;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getOverallState() {
        return overallState;
    }

    public void setOverallState(String overallState) {
        this.overallState = overallState;
    }

    public String getOverallText() {
        return overallText;
    }

    public void setOverallText(String overallText) {
        this.overallText = overallText;
    }

    public String getBlocker() {
        return blocker;
    }

    public void setBlocker(String blocker) {
        this.blocker = blocker;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public String getSubjectNo() {
        return subjectNo;
    }

    public void setSubjectNo(String subjectNo) {
        this.subjectNo = subjectNo;
    }

    public String getBedNo() {
        return bedNo;
    }

    public void setBedNo(String bedNo) {
        this.bedNo = bedNo;
    }

    public Integer getSubjectVer() {
        return subjectVer;
    }

    public void setSubjectVer(Integer subjectVer) {
        this.subjectVer = subjectVer;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
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

    public String getSiteFlagRecord() {
        return siteFlagRecord;
    }

    public void setSiteFlagRecord(String siteFlagRecord) {
        this.siteFlagRecord = siteFlagRecord;
    }

    public String getSiteSource() {
        return siteSource;
    }

    public void setSiteSource(String siteSource) {
        this.siteSource = siteSource;
    }

    public String getSiteStateText() {
        return siteStateText;
    }

    public void setSiteStateText(String siteStateText) {
        this.siteStateText = siteStateText;
    }

    public String getRecordFixNo() {
        return recordFixNo;
    }

    public void setRecordFixNo(String recordFixNo) {
        this.recordFixNo = recordFixNo;
    }

    public boolean isRecheckPending() {
        return recheckPending;
    }

    public void setRecheckPending(boolean recheckPending) {
        this.recheckPending = recheckPending;
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

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
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

    public List<RoleBlock> getRoles() {
        return roles;
    }

    public List<ItemRow> getItems() {
        return items;
    }

    public List<EventRow> getEvents() {
        return events;
    }
}
