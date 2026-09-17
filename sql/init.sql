SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    location VARCHAR(300),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS positions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    required_skills VARCHAR(500),
    required_certificates VARCHAR(500),
    required_hours INT DEFAULT 0,
    min_count INT DEFAULT 1,
    max_count INT DEFAULT 10,
    requirement_version INT DEFAULT 1 COMMENT '门槛版本：技能/证书/时长每次加严或改写加1',
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS volunteers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    id_card VARCHAR(18),
    total_hours DECIMAL(10,2) DEFAULT 0,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_id_card (id_card)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS volunteer_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    volunteer_id BIGINT NOT NULL,
    skill_name VARCHAR(100) NOT NULL,
    skill_level INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (volunteer_id) REFERENCES volunteers(id) ON DELETE CASCADE,
    UNIQUE KEY uk_volunteer_skill (volunteer_id, skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS volunteer_certificates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    volunteer_id BIGINT NOT NULL,
    cert_name VARCHAR(100) NOT NULL,
    cert_no VARCHAR(100),
    issue_date DATE,
    expire_date DATE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (volunteer_id) REFERENCES volunteers(id) ON DELETE CASCADE,
    UNIQUE KEY uk_volunteer_cert (volunteer_id, cert_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    volunteer_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    apply_message TEXT,
    status TINYINT DEFAULT 0,
    capability_check_result TEXT,
    check_pass TINYINT DEFAULT 0,
    requirement_version_at_apply INT DEFAULT 1,
    recheck_result TEXT COMMENT '门槛改写后的最新复核结果JSON',
    recheck_pass TINYINT COMMENT '最新复核是否通过：1是 0否 NULL未复核',
    resume_node TINYINT COMMENT '被卡前的原审批节点，闸门解除后恢复',
    block_reason VARCHAR(20) COMMENT '停在能力校验失败的原因：THRESHOLD门槛/CERT证件过期/ACTIVITY活动散场',
    current_approval_node TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_position_status (position_id, status),
    FOREIGN KEY (volunteer_id) REFERENCES volunteers(id) ON DELETE CASCADE,
    FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE,
    FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS approval_flows (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    node_level TINYINT NOT NULL,
    node_name VARCHAR(50) NOT NULL,
    approver_id BIGINT,
    approver_name VARCHAR(100),
    status TINYINT NOT NULL,
    comment TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_registration_id (registration_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO activities (name, description, start_time, end_time, location, status) VALUES
('社区环保公益活动', '组织志愿者参与社区垃圾分类、环境清洁等公益活动', '2026-08-01 09:00:00', '2026-08-31 18:00:00', '阳光社区', 1),
('敬老院关爱行动', '为敬老院老人提供陪伴、日常照料等志愿服务', '2026-08-10 08:00:00', '2026-08-10 17:00:00', '幸福敬老院', 1),
('交通安全宣传', '协助交警开展交通安全宣传活动', '2026-08-15 09:00:00', '2026-08-15 16:00:00', '市中心广场', 1);

INSERT INTO positions (activity_id, name, required_skills, required_certificates, required_hours, min_count, max_count, status) VALUES
(1, '垃圾分类指导员', '沟通能力,组织能力', '垃圾分类培训证书', 10, 5, 15, 1),
(1, '环境清洁工', '体力劳动', '', 5, 10, 20, 1),
(2, '生活照料员', '护理知识,耐心细致', '护理资格证', 20, 3, 8, 1),
(2, '文艺表演者', '唱歌,跳舞,乐器', '', 5, 2, 5, 1),
(3, '交通协管员', '交通法规知识,沟通能力', '交通安全培训证', 10, 4, 10, 1);

INSERT INTO volunteers (name, phone, email, id_card, total_hours, status) VALUES
('张三', '13800138001', 'zhangsan@example.com', '110101199001011234', 120.5, 1),
('李四', '13800138002', 'lisi@example.com', '110101199002022345', 80.0, 1),
('王五', '13800138003', 'wangwu@example.com', '110101199003033456', 50.0, 1),
('赵六', '13800138004', 'zhaoliu@example.com', '110101199004044567', 30.0, 1),
('钱七', '13800138005', 'qianqi@example.com', '110101199005055678', 200.0, 1);

INSERT INTO volunteer_skills (volunteer_id, skill_name, skill_level) VALUES
(1, '沟通能力', 3),
(1, '组织能力', 2),
(2, '体力劳动', 3),
(3, '护理知识', 2),
(3, '耐心细致', 3),
(4, '唱歌', 3),
(4, '跳舞', 2),
(5, '交通法规知识', 3),
(5, '沟通能力', 2);

INSERT INTO volunteer_certificates (volunteer_id, cert_name, cert_no, issue_date, expire_date) VALUES
(1, '垃圾分类培训证书', 'LF2026001', '2026-01-15', '2027-01-15'),
(3, '护理资格证', 'HL2025001', '2025-06-20', '2028-06-20'),
(5, '交通安全培训证', 'JT2026001', '2026-03-10', '2027-03-10');