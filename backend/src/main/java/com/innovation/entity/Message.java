package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String content;
    private String tag;
    private Integer likes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
