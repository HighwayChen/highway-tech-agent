CREATE TABLE IF NOT EXISTS interview_resume (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    content_text_path VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    tech_tags VARCHAR(512),
    target_position VARCHAR(128),
    analysis_summary TEXT,
    interview_plan_summary TEXT,
    interview_plan_path VARCHAR(512),
    report_md_path VARCHAR(512),
    report_html_path VARCHAR(512),
    failure_reason VARCHAR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS interview_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    interview_plan_path VARCHAR(512),
    overall_grade VARCHAR(32),
    overall_feedback TEXT,
    improvement_suggestions TEXT,
    report_md_path VARCHAR(512),
    report_html_path VARCHAR(512),
    failure_reason VARCHAR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resume_id (resume_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS interview_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    round_number INT NOT NULL,
    round_name VARCHAR(128) NOT NULL,
    difficulty VARCHAR(64) NOT NULL,
    question_number INT NOT NULL,
    content TEXT NOT NULL,
    scoring_points TEXT,
    user_answer TEXT,
    feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_round_number (round_number),
    CONSTRAINT uk_session_question UNIQUE (session_id, round_number, question_number)
);

CREATE TABLE IF NOT EXISTS interview_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1024),
    input_summary TEXT,
    output_summary TEXT,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_biz (biz_type, biz_id),
    INDEX idx_task_type (task_type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);
