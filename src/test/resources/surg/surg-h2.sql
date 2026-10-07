-- H2（MySQL 兼容模式）测试库：结构口径与 doc/schema/mh.sql 保持一致
CREATE TABLE t_mh_surg_apply (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  apply_no VARCHAR(64) NOT NULL,
  subject_no VARCHAR(64),
  bed_no VARCHAR(32),
  subject_ver INT DEFAULT 1,
  dept_name VARCHAR(64),
  procedure_code VARCHAR(64),
  procedure_name VARCHAR(64),
  site_flag VARCHAR(16),
  site_flag_record VARCHAR(16),
  site_source VARCHAR(16),
  record_fix_no VARCHAR(64),
  urgent_flag TINYINT DEFAULT 0,
  status INT DEFAULT 0,
  version_no INT DEFAULT 1,
  redo_of VARCHAR(64),
  recheck_flag TINYINT DEFAULT 0,
  recheck_deadline DATETIME,
  release_at DATETIME,
  occupy_resource VARCHAR(64),
  slot_key VARCHAR(96),
  occupy_at DATETIME,
  archive_at DATETIME,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  del_flag INT DEFAULT 0,
  CONSTRAINT uk_apply_no UNIQUE (apply_no)
);

CREATE TABLE t_mh_surg_sign (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  apply_no VARCHAR(64) NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  item_key VARCHAR(64) NOT NULL,
  item_name VARCHAR(64),
  sign_result TINYINT DEFAULT 0,
  signed_by VARCHAR(64),
  signed_at DATETIME,
  sign_seq INT DEFAULT 0,
  version_no INT DEFAULT 1,
  round_no INT DEFAULT 1,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  del_flag INT DEFAULT 0,
  CONSTRAINT uk_apply_role_item_ver UNIQUE (apply_no, role_code, item_key, version_no, round_no)
);
CREATE INDEX idx_sign_apply ON t_mh_surg_sign (apply_no);

CREATE TABLE t_mh_surg_trace (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  apply_no VARCHAR(64) NOT NULL,
  action_code VARCHAR(32) NOT NULL,
  action_by VARCHAR(64),
  action_at DATETIME,
  detail VARCHAR(512),
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  del_flag INT DEFAULT 0
);
CREATE INDEX idx_trace_apply ON t_mh_surg_trace (apply_no);

CREATE TABLE t_mh_surg_slot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  slot_key VARCHAR(96) NOT NULL,
  room_no VARCHAR(64) NOT NULL,
  time_slot VARCHAR(64) NOT NULL,
  apply_no VARCHAR(64) NOT NULL,
  occupy_at DATETIME,
  create_by VARCHAR(64),
  create_time DATETIME,
  update_by VARCHAR(64),
  update_time DATETIME,
  del_flag INT DEFAULT 0,
  CONSTRAINT uk_slot UNIQUE (slot_key)
);
CREATE INDEX idx_slot_apply ON t_mh_surg_slot (apply_no);
