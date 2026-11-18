package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message_like")
public class MessageLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long userId;
    private LocalDateTime createdAt;
}
