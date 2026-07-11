package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户针对一个公共角色维护的一条个人演进轨迹。
 */
@Data
@TableName("user_role_tracks")
public class UserRoleTrack implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 轨迹所属用户。 */
    private Long userId;

    /** 轨迹对应的公共角色。 */
    private Long characterId;

    /** 最近创建的个人版本主键；尚未调整时为空。 */
    private Long latestVersionId;

    /** 最近创建的版本号；尚未调整时为0。 */
    private Integer latestVersionNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
