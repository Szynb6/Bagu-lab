package com.bagu.dto.knowledge;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "列表项精简进度")
public class ProgressSummaryVO {

    private int status;
    private int lapseCount;
    private Long nextReviewAt;

    @JsonProperty("isStarred")
    private boolean isStarred;

    private boolean hasNote;
}
