package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("announcement")
public class Announcement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String type;
    private Boolean pinned;
    private String content;
    private String author;
    private Long authorId;
    private Integer views;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
