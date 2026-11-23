package com.bagu.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "题库分页列表项（不含 answerMd 正文）")
public class QuestionItemVO {

    private Long id;
    private String moduleSlug;
    private String moduleTitle;
    private Long chapterId;
    private String code;
    private String priority;
    private String title;
    private int imageCount;
    private boolean hasTable;
    private boolean hasCode;
    private Integer order;
    private ProgressSummaryVO progress;
}
