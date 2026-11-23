package com.bagu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("kb_chapter")
public class KbChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moduleId;
    private String code;
    private String title;
    private String summary;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
}
