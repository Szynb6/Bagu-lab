package com.bagu.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单题完整详情（含答案 Markdown、Hints、图解与个人进度）")
public class QuestionDetailVO {

    private Long id;
    private String moduleSlug;
    private String moduleTitle;
    private Long chapterId;
    private String code;
    private String priority;
    private String title;
    private List<String> hints;
    private String answerMd;
    private List<QuestionImageVO> images;
    private boolean hasTable;
    private boolean hasCode;
    private String animationKey;
    private Integer order;
    private ProgressVO progress;
}
