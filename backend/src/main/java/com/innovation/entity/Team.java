package com.innovation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team")
public class Team {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long competitionId;
    private String teamName;
    private Long captainUserId;
    private LocalDateTime createTime;
    private String status;
    /** 队长上传的项目材料相对路径 */
    private String projectFile;
    /** 管理员对项目管理中该项目所属队伍打分后锁定，禁止该队继续上传新材料 */
    private Boolean projectSubmitLocked;
}
