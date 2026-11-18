package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("competition")
public class Competition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;
    private String level;
    private String status;
    private String description;
    private String requirements;
    private String awards;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate enrollStart;
    private LocalDate enrollEnd;
    private Integer participants;
    private Integer maxParticipants;
    private Integer teamMin;
    private Integer teamMax;
    private String advisor;
    /** 负责老师展示名（advisor 为用户 ID 列表时由接口拼接；非表字段） */
    @TableField(exist = false)
    private String advisorDisplay;
    private String contact;
    /** 是否允许报名队长上传项目材料 */
    private Boolean projectUploadEnabled;
    private Boolean isDraft;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    /** 该竞赛是否已有向学生公示的成绩（非表字段，GET /competition/{id} 时填充） */
    @TableField(exist = false)
    private Boolean scorePublished;
}
