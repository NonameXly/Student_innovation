package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("registration")
public class Registration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long competitionId;
    private String teamName;
    private String status;
    private String rejectReason;
    private LocalDateTime enrollTime;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
