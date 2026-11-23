package com.bagu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("kb_module")
public class KbModule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String slug;
    private String title;
    private String fullTitle;
    private String icon;
    private String description;
    private String sprintGuide;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
