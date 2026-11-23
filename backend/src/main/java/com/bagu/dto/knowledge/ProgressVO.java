package com.bagu.dto.knowledge;

import com.bagu.entity.StudyProgress;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单题完整学习进度")
public class ProgressVO {

    private Long questionId;
    private int status;
    private BigDecimal easeFactor;
    private int intervalDays;
    private int repetitions;
    private int lapseCount;
    private Integer lastRating;
    private Long nextReviewAt;
    private Long lastReviewedAt;

    @JsonProperty("isStarred")
    private boolean isStarred;

    private String personalNote;

    public static ProgressVO empty(Long questionId) {
        return ProgressVO.builder()
                .questionId(questionId)
                .status(0)
                .easeFactor(new BigDecimal("2.50"))
                .intervalDays(0)
                .repetitions(0)
                .lapseCount(0)
                .lastRating(null)
                .nextReviewAt(null)
                .lastReviewedAt(null)
                .isStarred(false)
                .personalNote("")
                .build();
    }

    public static ProgressVO from(StudyProgress sp, Long questionId) {
        if (sp == null) {
            return empty(questionId);
        }
        return ProgressVO.builder()
                .questionId(sp.getQuestionId())
                .status(sp.getStatus() == null ? 0 : sp.getStatus().intValue())
                .easeFactor(sp.getEaseFactor() == null ? new BigDecimal("2.50") : sp.getEaseFactor())
                .intervalDays(sp.getIntervalDays() == null ? 0 : sp.getIntervalDays())
                .repetitions(sp.getRepetitions() == null ? 0 : sp.getRepetitions())
                .lapseCount(sp.getLapseCount() == null ? 0 : sp.getLapseCount())
                .lastRating(sp.getLastRating() == null ? null : sp.getLastRating().intValue())
                .nextReviewAt(sp.getNextReviewAt() == null ? null : sp.getNextReviewAt().toInstant().toEpochMilli())
                .lastReviewedAt(sp.getLastReviewedAt() == null ? null : sp.getLastReviewedAt().toInstant().toEpochMilli())
                .isStarred(Boolean.TRUE.equals(sp.getIsStarred()))
                .personalNote(sp.getPersonalNote() == null ? "" : sp.getPersonalNote())
                .build();
    }
}
