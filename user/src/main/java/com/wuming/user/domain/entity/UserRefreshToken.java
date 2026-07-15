package com.wuming.user.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_refresh_tokens")
public class UserRefreshToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresTime;
    private LocalDateTime revokedTime;
    private LocalDateTime lastUsedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
