-- =========================================
-- 学生科创管理平台 数据库初始化脚本
-- =========================================

CREATE DATABASE IF NOT EXISTS student_innovation DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE student_innovation;

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录账号',
    password    VARCHAR(200) NOT NULL COMMENT '密码(BCrypt)',
    name        VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    email       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    role        VARCHAR(20)  NOT NULL DEFAULT 'student' COMMENT '角色: admin/teacher/student',
    college     VARCHAR(100) DEFAULT NULL COMMENT '学院/部门',
    student_id  VARCHAR(30)  DEFAULT NULL COMMENT '学号(学生专用)',
    active      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    avatar_color VARCHAR(10) DEFAULT NULL COMMENT '头像颜色',
    join_date   DATE         DEFAULT NULL COMMENT '入学/入职日期',
    last_login  DATETIME     DEFAULT NULL COMMENT '最近登录时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0
) COMMENT '用户表';

-- 学校表（业务字段仅学校编号、学校名称；编号自编且创建后不允许修改，由应用层保证）
CREATE TABLE IF NOT EXISTS school (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code  VARCHAR(64)  NOT NULL COMMENT '学校编号(自编，全局唯一，创建后禁止修改)',
    school_name  VARCHAR(200) NOT NULL COMMENT '学校名称',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    UNIQUE KEY uk_school_code (school_code)
) COMMENT '学校表';

-- 竞赛表
CREATE TABLE IF NOT EXISTS competition (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(200) NOT NULL COMMENT '竞赛名称',
    category         VARCHAR(50)  NOT NULL COMMENT '类别: 数学/计算机/电子/综合/创新创业',
    level            VARCHAR(20)  NOT NULL DEFAULT 'school' COMMENT '级别: national/provincial/city/school/college',
    status           VARCHAR(20)  NOT NULL DEFAULT 'preparing' COMMENT '状态: preparing/enrolling/enrollend/ongoing/finished',
    description      TEXT         DEFAULT NULL COMMENT '竞赛描述',
    requirements     TEXT         DEFAULT NULL COMMENT '参赛要求',
    awards           TEXT         DEFAULT NULL COMMENT '奖项设置',
    start_date       DATE         DEFAULT NULL COMMENT '竞赛开始日期',
    end_date         DATE         DEFAULT NULL COMMENT '竞赛结束日期',
    enroll_start     DATE         DEFAULT NULL COMMENT '报名开始日期',
    enroll_end       DATE         DEFAULT NULL COMMENT '报名截止日期',
    participants     INT          NOT NULL DEFAULT 0 COMMENT '已报名人数',
    max_participants INT          DEFAULT NULL COMMENT '最大参赛人数',
    team_min         INT          DEFAULT 1 COMMENT '最小团队人数',
    team_max         INT          DEFAULT 1 COMMENT '最大团队人数',
    advisor          VARCHAR(500) DEFAULT NULL COMMENT '负责老师：用户ID逗号分隔；兼容旧数据纯文本',
    contact          VARCHAR(100) DEFAULT NULL COMMENT '联系方式',
    project_upload_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许学生上传项目材料',
    is_draft         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否草稿',
    created_by       BIGINT       DEFAULT NULL COMMENT '创建人ID',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted          TINYINT(1)   NOT NULL DEFAULT 0
) COMMENT '竞赛表';

-- 报名表
CREATE TABLE IF NOT EXISTS registration (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL COMMENT '报名用户ID',
    competition_id BIGINT       NOT NULL COMMENT '竞赛ID',
    team_name      VARCHAR(100) DEFAULT NULL COMMENT '团队名称',
    status         VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/rejected',
    reject_reason  VARCHAR(500) DEFAULT NULL COMMENT '驳回理由',
    enroll_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    reviewed_by    BIGINT       DEFAULT NULL COMMENT '审核人ID',
    reviewed_at    DATETIME     DEFAULT NULL COMMENT '审核时间',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_user (user_id),
    INDEX idx_competition (competition_id)
) COMMENT '报名表';

-- 队伍表（与实体 Team 对应；学生端 /apply/enroll 建队）
CREATE TABLE IF NOT EXISTS team (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    competition_id   BIGINT       NOT NULL COMMENT '竞赛ID',
    team_name        VARCHAR(100) DEFAULT NULL COMMENT '队伍名称',
    captain_user_id  BIGINT       NOT NULL COMMENT '队长用户ID',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    status           VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/rejected',
    project_file     VARCHAR(500) DEFAULT NULL COMMENT '队长上传的项目材料相对路径',
    project_submit_locked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '项目管理打分后禁止该队再上传项目',
    INDEX idx_team_competition (competition_id),
    INDEX idx_team_captain (captain_user_id)
) COMMENT '队伍表';

-- 队伍成员表（与实体 TeamMember 对应）
CREATE TABLE IF NOT EXISTS team_member (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id    BIGINT       NOT NULL COMMENT '队伍ID',
    user_id    BIGINT       NOT NULL COMMENT '成员用户ID',
    role       VARCHAR(20)  NOT NULL DEFAULT 'member' COMMENT '角色: captain/member',
    join_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    INDEX idx_team_member_team (team_id),
    INDEX idx_team_member_user (user_id)
) COMMENT '队伍成员表';

-- 创新项目管理（管理员端）
CREATE TABLE IF NOT EXISTS innovation_project (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(200) NOT NULL COMMENT '项目名称',
    description        TEXT         DEFAULT NULL COMMENT '项目说明',
    competition_id     BIGINT       DEFAULT NULL COMMENT '关联竞赛ID',
    team_id              BIGINT       DEFAULT NULL COMMENT '学生组队关联队伍；同一队伍可对应多条学生上传批次',
    leader_name        VARCHAR(50)  DEFAULT NULL COMMENT '负责人姓名',
    leader_student_id  VARCHAR(30)  DEFAULT NULL COMMENT '负责人学号',
    advisor_teacher_id BIGINT       DEFAULT NULL COMMENT '负责老师用户ID（学生新建时选择，该教师可查看本项目）',
    status             VARCHAR(20)  NOT NULL DEFAULT 'ongoing' COMMENT 'ongoing进行中/completed已结束',
    attachment_path    VARCHAR(500) DEFAULT NULL COMMENT '附件相对路径',
    remark             VARCHAR(500) DEFAULT NULL COMMENT '备注',
    teacher_evaluation TEXT         DEFAULT NULL COMMENT '教师评价（管理员填写，学生端可见）',
    teacher_evaluation_at DATETIME DEFAULT NULL COMMENT '教师评价最近保存时间',
    created_by         BIGINT       DEFAULT NULL COMMENT '创建人',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version            INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted            TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_competition (competition_id),
    INDEX idx_innovation_project_team (team_id),
    INDEX idx_innovation_project_advisor_teacher (advisor_teacher_id),
    INDEX idx_status (status)
) COMMENT '创新项目管理';

-- 公告表
CREATE TABLE IF NOT EXISTS announcement (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(200) NOT NULL COMMENT '标题',
    type       VARCHAR(20)  NOT NULL DEFAULT 'normal' COMMENT '类型: important/normal',
    pinned     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否置顶',
    content    TEXT         NOT NULL COMMENT '内容',
    author     VARCHAR(50)  DEFAULT NULL COMMENT '发布人',
    author_id  BIGINT       DEFAULT NULL COMMENT '发布人ID',
    views      INT          NOT NULL DEFAULT 0 COMMENT '浏览次数',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT(1)   NOT NULL DEFAULT 0
) COMMENT '公告表';

-- 系统通知（顶栏通知列表；target_role 为空表示全员可见；recipient_user_id 非空时仅该用户可见）
CREATE TABLE IF NOT EXISTS notice (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    title                VARCHAR(200) NOT NULL COMMENT '标题',
    content              TEXT         DEFAULT NULL COMMENT '正文',
    type                 VARCHAR(20)  NOT NULL DEFAULT 'info' COMMENT '类型: info/success/warning',
    target_role          VARCHAR(20)  DEFAULT NULL COMMENT '可见角色: student/teacher/admin，NULL=全员',
    link_url             VARCHAR(500) DEFAULT NULL COMMENT '点击后前端路由，可选',
    recipient_user_id  BIGINT       DEFAULT NULL COMMENT '指定接收用户；非空时仅该用户可见，与其它广播规则互斥',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted              TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_notice_created (created_at),
    INDEX idx_notice_recipient (recipient_user_id)
) COMMENT '系统通知';

-- 用户已读通知
CREATE TABLE IF NOT EXISTS notice_read (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id  BIGINT   NOT NULL COMMENT '通知ID',
    user_id    BIGINT   NOT NULL COMMENT '用户ID',
    read_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_notice_user (notice_id, user_id),
    INDEX idx_notice_read_user (user_id)
) COMMENT '通知已读';

-- 留言表
CREATE TABLE IF NOT EXISTS message (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL COMMENT '留言用户ID',
    content    TEXT         NOT NULL COMMENT '留言内容',
    tag        VARCHAR(50)  DEFAULT NULL COMMENT '标签',
    likes      INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_user (user_id)
) COMMENT '留言表';

-- 留言回复表
CREATE TABLE IF NOT EXISTS message_reply (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT   NOT NULL COMMENT '留言ID',
    user_id    BIGINT   NOT NULL COMMENT '回复用户ID',
    content    TEXT     NOT NULL COMMENT '回复内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT(1) NOT NULL DEFAULT 0,
    INDEX idx_message (message_id)
) COMMENT '留言回复表';

-- 留言点赞记录表
CREATE TABLE IF NOT EXISTS message_like (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL COMMENT '留言ID',
    user_id    BIGINT NOT NULL COMMENT '用户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_unique (message_id, user_id)
) COMMENT '点赞记录表';

-- 成绩表
CREATE TABLE IF NOT EXISTS score (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT      DEFAULT NULL COMMENT '学生用户ID',
    competition_id BIGINT      NOT NULL COMMENT '竞赛ID',
    innovation_project_id BIGINT DEFAULT NULL COMMENT '关联创新项目档案ID（项目管理打分）',
    total_score    DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '总分',
    award          VARCHAR(20) DEFAULT NULL COMMENT '奖项: first/second/third/excellent',
    `show`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否向学生公示：1公示 0隐藏',
    remark         VARCHAR(500) DEFAULT NULL COMMENT '备注',
    created_by     BIGINT       DEFAULT NULL COMMENT '录入人ID',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_user (user_id),
    INDEX idx_competition (competition_id),
    UNIQUE INDEX uk_score_innovation_project (innovation_project_id)
) COMMENT '成绩表';

-- AI对话表
CREATE TABLE IF NOT EXISTS ai_chat (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL COMMENT '用户ID',
    title      VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '对话标题',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    INDEX idx_user (user_id)
) COMMENT 'AI对话表';

-- AI消息表
CREATE TABLE IF NOT EXISTS ai_message (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_id    BIGINT       NOT NULL COMMENT '对话ID',
    role       VARCHAR(20)  NOT NULL COMMENT '角色: user/assistant',
    content    TEXT         NOT NULL COMMENT '消息内容',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chat (chat_id)
) COMMENT 'AI消息表';



-- =========================================
-- 已有数据库增量（若列已存在可忽略执行报错）
-- =========================================
-- ALTER TABLE competition ADD COLUMN project_upload_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许学生上传项目材料' AFTER contact;
-- ALTER TABLE team ADD COLUMN project_file VARCHAR(500) DEFAULT NULL COMMENT '队长上传的项目材料相对路径' AFTER status;
-- 若旧库 registration 曾含 project_file，可迁移后删除列: UPDATE team t INNER JOIN registration r ON r.competition_id=t.competition_id AND r.user_id=t.captain_user_id AND (r.team_name<=>t.team_name) SET t.project_file=r.project_file WHERE r.project_file IS NOT NULL; ALTER TABLE registration DROP COLUMN project_file;
-- 若旧库缺少 team / team_member 表，请复制本文件正文中的 CREATE TABLE team / team_member 语句执行。
-- ALTER TABLE innovation_project ADD COLUMN team_id BIGINT DEFAULT NULL COMMENT '学生组队关联队伍' AFTER competition_id;
-- DROP INDEX idx_innovation_team ON innovation_project;  -- 若曾建普通索引
-- CREATE INDEX idx_innovation_project_team ON innovation_project (team_id);  -- 同一队伍可多条上传批次，勿用 UNIQUE(team_id)
-- ALTER TABLE score ADD COLUMN `show` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否向学生公示：1公示 0隐藏' AFTER award;
-- ALTER TABLE notice ADD COLUMN recipient_user_id BIGINT DEFAULT NULL COMMENT '指定接收用户；非空时仅该用户可见' AFTER link_url;
-- CREATE INDEX idx_notice_recipient ON notice(recipient_user_id);

-- =========================================
-- 初始数据
-- =========================================

-- 示例学校（注册页下拉；可按需增删）
INSERT INTO school (school_code, school_name) VALUES
('SCH-DEMO-001', '示例科技大学'),
('SCH-DEMO-002', '示例师范大学');

-- 管理员账号 (密码: admin123, BCrypt加密)
INSERT INTO sys_user (username, password, name, email, role, college, active, join_date) VALUES
('admin', '$2a$10$JP/Fwv4tNScDfe4UDCnl7eT4O9GSJ4.mA.DXyjoW0OoEcLbW.weN.', '管理员', 'admin@school.edu', 'admin', '信息中心', 1, '2020-09-01');

-- 示例通知（全员 + 仅学生）
INSERT INTO notice (title, content, type, target_role, link_url) VALUES
('欢迎使用学生科创管理平台', '您可在侧边栏使用竞赛、公告、留言板等功能；顶栏本按钮可查看系统通知。', 'info', NULL, NULL),
('请及时完善个人信息', '建议在「个人中心」核对学院、学号（学生）及联系方式。', 'success', NULL, NULL),
('竞赛报名提醒', '近期有多场竞赛开放报名，请关注竞赛列表中的报名截止时间。', 'warning', 'student', NULL);
