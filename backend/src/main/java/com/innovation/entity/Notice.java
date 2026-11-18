package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notice")
public class Notice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    /** info / success / warning */
    private String type;
    /** student / teacher / admin；null 表示全员可见 */
    private String targetRole;
    /** 前端路由路径，可选 */
    private String linkUrl;
    /** 指定接收用户：非空时仅该用户可见（与全员/角色广播互斥） */
    private Long recipientUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    /** 列表接口填充：当前用户是否已读（非表字段） */
    @TableField(exist = false)
    private Boolean hasRead;
}
