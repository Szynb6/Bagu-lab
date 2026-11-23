package com.bagu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("kb_question")
public class KbQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long moduleId;
    private Long chapterId;
    private String code;
    private String priority;
    private String title;
    private String hintsJson;
    private String answerMd;
    private String imagesJson;
    private String tagsJson;
    private Boolean hasTable;
    private Boolean hasCode;
    private String animationKey;
    private Integer sortOrder;
    private Boolean isDeleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
