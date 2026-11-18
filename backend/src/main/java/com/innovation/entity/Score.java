package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("score")
public class Score {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long competitionId;
    /** 关联创新项目档案（项目管理「打分」绑定本条成绩，便于同一竞赛多条上传批次各自记分） */
    private Long innovationProjectId;
    private BigDecimal totalScore;
    private String award;
    /** 是否向学生公示：1 公示，0 隐藏（学生端列表/分析不可见） */
    @TableField("`show`")
    private Integer show;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
