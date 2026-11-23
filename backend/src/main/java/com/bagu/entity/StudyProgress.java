package com.bagu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("study_progress")
public class StudyProgress {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long questionId;
    private Short status;
    private BigDecimal easeFactor;
    private Integer intervalDays;
    private Integer repetitions;
    private Integer lapseCount;
    private Short lastRating;
    private OffsetDateTime nextReviewAt;
    private OffsetDateTime lastReviewedAt;
    private Boolean isStarred;
    private String personalNote;
    private OffsetDateTime updatedAt;
}
