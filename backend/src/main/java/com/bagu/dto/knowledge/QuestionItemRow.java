package com.bagu.dto.knowledge;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 分页列表查询平铺投影行（仅用于 Mapper XML 映射）。
 */
@Data
public class QuestionItemRow {

    private Long id;
    private String moduleSlug;
    private String moduleTitle;
    private Long chapterId;
    private String code;
    private String priority;
    private String title;
    private Integer imageCount;
    private Boolean hasTable;
    private Boolean hasCode;
    private Integer sortOrder;

    private Short progressStatus;
    private Integer lapseCount;
    private OffsetDateTime nextReviewAt;
    private Boolean isStarred;
    private Boolean hasNote;
}
