package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ai_chat")
public class AiChat {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
