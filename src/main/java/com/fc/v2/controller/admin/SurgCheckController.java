package com.fc.v2.controller.admin;

import java.util.Date;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.model.auto.TMhSurgApply;
import com.fc.v2.model.custom.surg.SurgCheckView;
import com.fc.v2.service.ITMhSurgApplyService;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

/**
 * 术前三方并行核查会签 controller。
 *
 * <p>三方角色码：0=护士（身份/部位标识/影像/血供），1=术者（术式部位/器械/备用方案），
 * 2=麻醉（气道/用药/输血准备）。核查项键：护士 N1..N4，术者 S1..S3，麻醉 A1..A3。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@RestController
@RequestMapping("/SurgCheckController")
@Api(value = "术前三方核查会签")
public class SurgCheckController {

    @org.springframework.beans.factory.annotation.Autowired
    private ITMhSurgApplyService surgApplyService;

    @ApiOperation(value = "建单", notes = "核查表部位与病历部位不一致自动置异议")
    @PostMapping("/create")
    public AjaxResult create(@RequestParam String applyNo, @RequestParam String subjectNo,
                             @RequestParam(required = false) String bedNo,
                             @RequestParam(required = false) String deptName,
                             @RequestParam(required = false) String procedureName,
                             @ApiParam("核查表/现场部位 L/R/N") @RequestParam String siteFlag,
                             @ApiParam("病历部位 L/R/N，可空") @RequestParam(required = false) String siteFlagRecord,
                             @ApiParam("0择期 1抢救") @RequestParam(defaultValue = "0") int urgentFlag) {
        surgApplyService.createApply(applyNo, subjectNo, bedNo, deptName, procedureName,
                siteFlag, siteFlagRecord, urgentFlag, new Date());
        return AjaxResult.success("建单成功：" + applyNo);
    }

    @ApiOperation(value = "三方并行签署", notes = "已签项不可涂改；不通过即扣单，只能作废重做")
    @PostMapping("/sign")
    public AjaxResult sign(@RequestParam String applyNo,
                           @ApiParam("0护士 1术者 2麻醉") @RequestParam String roleCode,
                           @ApiParam("N1..N4 / S1..S3 / A1..A3") @RequestParam String itemKey,
                           @RequestParam boolean pass, @RequestParam String signer) {
        surgApplyService.sign(applyNo, roleCode, itemKey, pass, signer, new Date());
        return AjaxResult.success("已签署");
    }

    @ApiOperation(value = "择期放行并占用手术间", notes = "三方齐备+无异议；放行与占位同一事务原子成")
    @PostMapping("/release")
    public AjaxResult release(@RequestParam String applyNo, @RequestParam String roomNo,
                              @RequestParam String timeSlot, @RequestParam String operator) {
        surgApplyService.release(applyNo, roomNo, timeSlot, operator, new Date());
        return AjaxResult.success("已放行并占用手术间 " + roomNo + " " + timeSlot);
    }

    @ApiOperation(value = "抢救先放行", notes = "术者与麻醉授权人双签授权，挂24h补核待办")
    @PostMapping("/emergencyRelease")
    public AjaxResult emergencyRelease(@RequestParam String applyNo, @RequestParam String roomNo,
                                       @RequestParam String timeSlot, @RequestParam String operator,
                                       @RequestParam String surgeonAuthorizer,
                                       @RequestParam String anesAuthorizer) {
        surgApplyService.emergencyRelease(applyNo, roomNo, timeSlot, operator,
                surgeonAuthorizer, anesAuthorizer, new Date());
        return AjaxResult.success("抢救先放行，已占位并挂补核待办");
    }

    @ApiOperation(value = "事后补核签署")
    @PostMapping("/recheck")
    public AjaxResult recheck(@RequestParam String applyNo, @RequestParam String roleCode,
                              @RequestParam String itemKey, @RequestParam boolean pass,
                              @RequestParam String signer) {
        surgApplyService.recheck(applyNo, roleCode, itemKey, pass, signer, new Date());
        return AjaxResult.success("补核已留痕");
    }

    @ApiOperation(value = "超时限补核上级确认", notes = "超24h补齐不自动核销，须上级医师签字")
    @PostMapping("/approveOverdueRecheck")
    public AjaxResult approveOverdueRecheck(@RequestParam String applyNo,
                                            @RequestParam String approver,
                                            @RequestParam String approvalNo) {
        surgApplyService.approveOverdueRecheck(applyNo, approver, approvalNo, new Date());
        return AjaxResult.success("超时限补核已确认闭环");
    }

    @ApiOperation(value = "部位异议裁定", notes = "以核查表＋影像为准，登记病历更正流水号")
    @PostMapping("/resolveSite")
    public AjaxResult resolveSite(@RequestParam String applyNo, @RequestParam String recordFixNo,
                                  @RequestParam String operator) {
        surgApplyService.resolveSiteDispute(applyNo, recordFixNo, operator, new Date());
        return AjaxResult.success("部位异议已裁定，病历更正已登记");
    }

    @ApiOperation(value = "抢救现场部位裁定", notes = "按影像号现场裁定，病历更正补核时限内补登")
    @PostMapping("/emergencySiteVerdict")
    public AjaxResult emergencySiteVerdict(@RequestParam String applyNo,
                                           @RequestParam String imageNo,
                                           @RequestParam String operator) {
        surgApplyService.emergencySiteVerdict(applyNo, imageNo, operator, new Date());
        return AjaxResult.success("抢救现场按影像裁定完成");
    }

    @ApiOperation(value = "转床", notes = "标变更继续用；护士身份/部位项新一轮重核，余签随床转签")
    @PostMapping("/changeBed")
    public AjaxResult changeBed(@RequestParam String applyNo, @RequestParam String newBedNo,
                                @RequestParam String operator) {
        surgApplyService.changeBed(applyNo, newBedNo, operator, new Date());
        return AjaxResult.success("已登记转床，护士方重新核对身份与部位");
    }

    @ApiOperation(value = "换人", notes = "旧单作废，按新患者重建新版核查单，返回新单号")
    @PostMapping("/changePatient")
    public AjaxResult changePatient(@RequestParam String applyNo, @RequestParam String newSubjectNo,
                                    @RequestParam(required = false) String newBedNo,
                                    @RequestParam String operator) {
        String newNo = surgApplyService.changePatient(applyNo, newSubjectNo, newBedNo,
                operator, new Date());
        return AjaxResult.success("已换人，旧单作废，重建新核查单：" + newNo);
    }

    @ApiOperation(value = "作废重做", notes = "已签项要改的唯一合法路径，旧版留痕，返回新单号")
    @PostMapping("/voidRedo")
    public AjaxResult voidRedo(@RequestParam String applyNo, @RequestParam String reason,
                               @RequestParam String operator) {
        String newNo = surgApplyService.voidAndRedo(applyNo, reason, operator, new Date());
        return AjaxResult.success("已作废重做，新核查单：" + newNo);
    }

    @ApiOperation(value = "撤台", notes = "开台前撤销放行，同事务释放手术间；已归档不可撤")
    @PostMapping("/cancelRelease")
    public AjaxResult cancelRelease(@RequestParam String applyNo, @RequestParam String reason,
                                    @RequestParam String operator) {
        surgApplyService.cancelRelease(applyNo, reason, operator, new Date());
        return AjaxResult.success("已撤台并释放手术间时段");
    }

    @ApiOperation(value = "归档", notes = "核查齐备、补核闭环才可归档，状态与时刻一笔落库")
    @PostMapping("/archive")
    public AjaxResult archive(@RequestParam String applyNo, @RequestParam String operator) {
        surgApplyService.archive(applyNo, operator, new Date());
        return AjaxResult.success("已归档");
    }

    @ApiOperation(value = "倒查全链", notes = "逐项结果/卡方/事件流；没人核、未齐、不通过分着给")
    @GetMapping("/trace")
    public AjaxResult trace(@RequestParam String applyNo) {
        SurgCheckView view = surgApplyService.traceChain(applyNo);
        if (view == null) {
            return AjaxResult.error(404, "查无此核查单：" + applyNo);
        }
        return AjaxResult.successData(200, view);
    }

    @ApiOperation(value = "取单据主信息")
    @GetMapping("/get")
    public AjaxResult get(@RequestParam String applyNo) {
        TMhSurgApply a = surgApplyService.get(applyNo);
        if (a == null) {
            return AjaxResult.error(404, "查无此核查单：" + applyNo);
        }
        return AjaxResult.successData(200, a);
    }

    /** 口径类拒绝一律 400 + 明确原因，不当成 500 吞掉 */
    @ExceptionHandler(IllegalStateException.class)
    public AjaxResult onReject(IllegalStateException e) {
        return AjaxResult.error(400, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public AjaxResult onBadArg(IllegalArgumentException e) {
        return AjaxResult.error(400, e.getMessage());
    }
}
