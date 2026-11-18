package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("message_reply")
public class MessageReply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long messageId;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
