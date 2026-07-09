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

@Data
@TableName("novels")
public class Novel implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String filePath;
    /**
     * 文件存储类型：LOCAL/COS。
     */
    private String storageType;
    /**
     * 文件存储定位符：LOCAL为本地路径，COS为对象key。
     */
    private String objectKey;
    /**
     * 上传时的原始文件名。
     */
    private String originalFilename;
    /**
     * UTF-8归一化后的文件大小，单位字节。
     */
    private Long fileSize;
    private Long uploaderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}
