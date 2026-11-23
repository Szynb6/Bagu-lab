package com.bagu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("study_review_log")
public class StudyReviewLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long questionId;
    private Short rating;
    private Integer durationSec;
    private Integer prevInterval;
    private Integer nextInterval;
    private OffsetDateTime reviewedAt;
}
