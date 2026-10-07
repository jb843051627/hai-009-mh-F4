-- mh 智慧医疗门诊管理系统 -- schema (hai-009)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：hai_009

CREATE TABLE IF NOT EXISTS t_mh_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '就诊档案卡号',
  node_no int DEFAULT NULL COMMENT '本卡版本次序',
  site_id int DEFAULT NULL COMMENT '所属底档',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底档代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本卡年就诊次数',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本年度新增就诊次数',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版本次序走）',
  grade_level int DEFAULT NULL COMMENT '就诊次数折到的随访保障层级',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已压死',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='就诊档案卡';

CREATE TABLE IF NOT EXISTS t_mh_inpat_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '住院处置单号',
  stage int DEFAULT NULL COMMENT '当前处置段 0..3（入院/检查/治疗/出院封卷）',
  status int DEFAULT NULL COMMENT '单子落定 0未起 1在办 2已封存',
  content varchar(255) DEFAULT NULL COMMENT '单上记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='住院处置单';

CREATE TABLE IF NOT EXISTS t_mh_med_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '特殊用药签核单号',
  node_no int DEFAULT NULL COMMENT '当前所在道 0..2（开方提/药剂科核/医务科批）',
  sign_mode int DEFAULT NULL COMMENT '同口并印办法 0任一人 1两名点齐',
  need_count int DEFAULT NULL COMMENT '本道应落印数',
  sign_count int DEFAULT NULL COMMENT '本道已落印数',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='特殊用药签核单';

CREATE TABLE IF NOT EXISTS t_mh_patient (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '患者底档代号',
  site_name varchar(128) DEFAULT NULL COMMENT '底档名称',
  site_type varchar(32) DEFAULT NULL COMMENT '底档类别(普通/慢病/特需)',
  road_name varchar(128) DEFAULT NULL COMMENT '所属行政区划(省—市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '单档年就诊次数一档上限',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '二档上限',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '三档上限',
  status int DEFAULT NULL COMMENT '底档情形 0在用 1已注销',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者底档';

CREATE TABLE IF NOT EXISTS t_mh_reg_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '号源转号事项号',
  due_at datetime DEFAULT NULL COMMENT '放号截止那一天最后一刻',
  amount decimal(12,2) DEFAULT NULL COMMENT '单条提前转号的自然日数',
  content varchar(255) DEFAULT NULL COMMENT '号源事由与承接科室记要',
  status int DEFAULT NULL COMMENT '条目情形 0待转 1已转 2转不出去',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号源到期转号事项条';

CREATE TABLE IF NOT EXISTS t_mh_tria_rule (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '分诊判定线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '判定线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '危重档界值（含）',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '紧急档界值（含）',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '普通档界值（含）',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '判定线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '判定线情形 0现行 1已让位',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='急诊分诊判定线';

CREATE TABLE IF NOT EXISTS t_mh_visit_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '门诊就诊数据上传批次号',
  row_no int DEFAULT NULL COMMENT '原表行次',
  item_code varchar(64) DEFAULT NULL COMMENT '被校验出的数据项代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行就诊费用',
  status int DEFAULT NULL COMMENT '行落地情形 0待核 1已入库 2退回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门诊就诊记录导入核对行';

-- 初始档案数据（id=1 启用 / id=2 停用）
-- 初始档案数据：id=0 在用、id=1 已注销
-- 年就诊次数上限 3/10/30 次：等于上限算高一档。
INSERT IGNORE INTO t_mh_patient (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'PT00', '临江市东湖街慢病随访组（在用）', '慢病', '临江省—临江市—东湖区—东湖街道', 3.00, 10.00, 30.00, 0, 0, 'seed', NOW()),
       (1, 'PT01', '临江市城南普通门诊组（已注销）', '普通', '临江省—临江市—城南区—望湖街道', 3.00, 10.00, 30.00, 1, 0, 'seed', NOW());


-- mh 附加段：阈值条目投递处置域（与基线表并存）
-- 列名与实体契约逐列对齐；id=0 启用 / 1 停用 为初始档案约定。

CREATE TABLE IF NOT EXISTS t_mh_specimen (
  id bigint NOT NULL COMMENT '主键',
  specimen_no varchar(64) DEFAULT NULL COMMENT '单据唯一编号',
  patient_no varchar(64) DEFAULT NULL COMMENT '关联对象编号',
  dept_name varchar(64) DEFAULT NULL COMMENT '归属部门',
  collect_at datetime DEFAULT NULL COMMENT '单据建立时刻',
  status int DEFAULT NULL COMMENT '单据情形 0进行中 1已完成 2已归档 3已作废',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务单据';

CREATE TABLE IF NOT EXISTS t_mh_crit_value (
  id bigint NOT NULL COMMENT '主键',
  specimen_no varchar(64) DEFAULT NULL COMMENT '单据唯一编号',
  item_code varchar(64) DEFAULT NULL COMMENT '条目代号',
  item_name varchar(128) DEFAULT NULL COMMENT '条目名称',
  raw_value decimal(12,4) DEFAULT NULL COMMENT '度量值',
  ref_high decimal(12,4) DEFAULT NULL COMMENT '度量上限',
  auto_flag int DEFAULT NULL COMMENT '系统初判 0否 1是',
  manual_flag int DEFAULT NULL COMMENT '人工判定 0否 1是 2推翻',
  review_by varchar(64) DEFAULT NULL COMMENT '判定人',
  review_at datetime DEFAULT NULL COMMENT '判定时刻',
  crit_time datetime DEFAULT NULL COMMENT '确认时刻',
  status int DEFAULT NULL COMMENT '记录情形 0待判定 1已确认 2已推翻 3已办结',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阈值条目判定记录';

CREATE TABLE IF NOT EXISTS t_mh_crit_notify (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '投递批次号（同一单据合并成一条）',
  specimen_no varchar(64) DEFAULT NULL COMMENT '单据唯一编号',
  target_user varchar(64) DEFAULT NULL COMMENT '接收方',
  notify_path int DEFAULT NULL COMMENT '投递路径 0一线 1二线',
  try_count int DEFAULT NULL COMMENT '已投递次数',
  notified int DEFAULT NULL COMMENT '是否送达 0否 1是',
  first_notify_at datetime DEFAULT NULL COMMENT '首次投递时刻',
  last_notify_at datetime DEFAULT NULL COMMENT '最近投递时刻',
  deadline_at datetime DEFAULT NULL COMMENT '投递时限（确认时刻起算）',
  timeout_flag int DEFAULT NULL COMMENT '是否超时限 0否 1是',
  upgrade_user varchar(64) DEFAULT NULL COMMENT '超时限上报对象',
  upgrade_at datetime DEFAULT NULL COMMENT '上报时刻',
  deal_result varchar(255) DEFAULT NULL COMMENT '处置回填',
  deal_by varchar(64) DEFAULT NULL COMMENT '处置人',
  deal_at datetime DEFAULT NULL COMMENT '处置时刻',
  closed int DEFAULT NULL COMMENT '办结 0未办结 1已办结',
  false_flag int DEFAULT NULL COMMENT '是否被推翻 0否 1是',
  status int DEFAULT NULL COMMENT '记录情形 0投递中 1已办结 2已终止',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='阈值条目投递处置记录';

INSERT IGNORE INTO t_mh_specimen (id, specimen_no, patient_no, dept_name, collect_at, status, del_flag, create_by, create_time)
VALUES (0, 'SP-REF-01', 'P0001', '内科', '2026-09-14 08:00:00', 2, 0, 'seed', NOW()),
       (1, 'SP-REF-02', 'P0002', '急诊科', '2026-09-14 08:30:00', 2, 0, 'seed', NOW());

-- ============================================================
-- mh 附加段：并行核对与占位联动域（与基线表并存）
-- 命名一律中性化：单据 / 条目 / 事件；题面语义不进注释
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_mh_surg_apply` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(64) NOT NULL COMMENT '单据唯一编号',
  `subject_no` varchar(64) DEFAULT NULL COMMENT '关联对象编号（患者号，换人则整单作废重做）',
  `bed_no` varchar(32) DEFAULT NULL COMMENT '床号（仅转床则护士方重新核对，余签保留）',
  `subject_ver` int DEFAULT 1 COMMENT '患者/床位对象版本，换人转床递增',
  `dept_name` varchar(64) DEFAULT NULL COMMENT '归属科室',
  `procedure_code` varchar(64) DEFAULT NULL COMMENT '术式代号',
  `procedure_name` varchar(64) DEFAULT NULL COMMENT '术式名称',
  `site_flag` varchar(16) DEFAULT NULL COMMENT '核查表部位标识 L/R/N（以现场核查+影像为准）',
  `site_flag_record` varchar(16) DEFAULT NULL COMMENT '病历侧部位标识 L/R/N',
  `site_source` varchar(16) DEFAULT NULL COMMENT '部位状态 0一致 1以单据为准已更正病历 2有异议未放行',
  `record_fix_no` varchar(64) DEFAULT NULL COMMENT '病历部位更正流水号（抢救口头裁定时留空，补核前必须补登）',
  `urgent_flag` tinyint DEFAULT 0 COMMENT '是否抢救 0否 1是',
  `status` int DEFAULT 0 COMMENT '单据情形 0待核 1核对中 2已放行 3已占位 4已作废 5已归档（放行与占位同事务，稳态只见0/1/3/4/5）',
  `version_no` int DEFAULT 1 COMMENT '核查单版本号，作废重做（含换人）时递增，旧版签署留痕',
  `redo_of` varchar(64) DEFAULT NULL COMMENT '重做自哪张单号（作废重做链）',
  `recheck_flag` tinyint DEFAULT 0 COMMENT '是否待事后补核 0否 1是（仅抢救先放行可置1）',
  `recheck_deadline` datetime DEFAULT NULL COMMENT '事后补核时限（默认放行后24小时）',
  `release_at` datetime DEFAULT NULL COMMENT '放行时刻',
  `occupy_resource` varchar(64) DEFAULT NULL COMMENT '放行后占用的手术间',
  `slot_key` varchar(96) DEFAULT NULL COMMENT '排程时段键 手术间+时段，与上游排程同口径',
  `occupy_at` datetime DEFAULT NULL COMMENT '占用时刻',
  `archive_at` datetime DEFAULT NULL COMMENT '归档时刻',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_no` (`apply_no`),
  KEY `idx_subject` (`subject_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='并行核对单据主表';

CREATE TABLE IF NOT EXISTS `t_mh_surg_sign` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(64) NOT NULL COMMENT '单据编号',
  `role_code` varchar(32) NOT NULL COMMENT '核对角色 0角色甲 1角色乙 2角色丙',
  `item_key` varchar(64) NOT NULL COMMENT '核对项键',
  `item_name` varchar(64) DEFAULT NULL COMMENT '核对项名称',
  `sign_result` tinyint DEFAULT 0 COMMENT '核对结果 0未核 1通过 2不通过 3作废重做',
  `signed_by` varchar(64) DEFAULT NULL COMMENT '核对人',
  `signed_at` datetime DEFAULT NULL COMMENT '核对时刻',
  `sign_seq` int DEFAULT 0 COMMENT '提交批次号（并行应同为 0，无先后）',
  `version_no` int DEFAULT 1 COMMENT '签署时的核查单版本，旧版签署随旧单留痕不改写',
  `round_no` int DEFAULT 1 COMMENT '同一版本内的核对轮次，转床后护士重核递增、旧轮留痕',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_role_item_ver` (`apply_no`,`role_code`,`item_key`,`version_no`,`round_no`),
  KEY `idx_apply` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='并行核对条目表';

CREATE TABLE IF NOT EXISTS `t_mh_surg_trace` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apply_no` varchar(64) NOT NULL COMMENT '单据编号',
  `action_code` varchar(32) NOT NULL COMMENT '动作码 建立/核对/放行/占位/撤台/补核/补核待办/补核确认/部位裁定/变更/作废/归档',
  `action_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `action_at` datetime DEFAULT NULL COMMENT '动作时刻',
  `detail` varchar(512) DEFAULT NULL COMMENT '动作说明',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` int DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_apply` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单据动作事件表';

-- 手术间排程占用：唯一键兜底，同一手术间同一时段只能被一张放行单占用
CREATE TABLE IF NOT EXISTS `t_mh_surg_slot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `slot_key` varchar(96) NOT NULL COMMENT '排程时段键 手术间+时段',
  `room_no` varchar(64) NOT NULL COMMENT '手术间',
  `time_slot` varchar(64) NOT NULL COMMENT '排程时段',
  `apply_no` varchar(64) NOT NULL COMMENT '占用该时段的放行单号',
  `occupy_at` datetime DEFAULT NULL COMMENT '占用时刻',
  `del_flag` int DEFAULT 0,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slot` (`slot_key`),
  KEY `idx_apply` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手术间排程占用表';