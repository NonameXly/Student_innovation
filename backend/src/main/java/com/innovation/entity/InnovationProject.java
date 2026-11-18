package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员维护的科创/竞赛项目档案，与报名表独立。
 */
@Data
@TableName("innovation_project")
public class InnovationProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 项目名称（管理员录入或学生端上传时填写，对应表字段 name） */
    private String name;
    /** 项目说明 */
    private String description;
    /** 关联竞赛（可选） */
    private Long competitionId;
    /** 学生端组队对应的队伍；同一队伍可有多条档案（每次队长上传新增一行） */
    private Long teamId;
    /** 负责人姓名 */
    private String leaderName;
    /** 负责人学号 */
    private String leaderStudentId;
    /** 负责老师用户 ID（学生新建时选择，该教师可在项目管理中查看） */
    private Long advisorTeacherId;
    /** ongoing 进行中 / completed 已结束 */
    private String status;
    /** 附件在存储根目录下的相对路径 */
    private String attachmentPath;
    private String remark;
    /** 教师评价（管理员维护，学生在竞赛详情中查看） */
    private String teacherEvaluation;
    /** 教师评价最近保存时间 */
    private LocalDateTime teacherEvaluationAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
