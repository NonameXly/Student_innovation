package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("school")
public class School {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String schoolCode;
    private String schoolName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
