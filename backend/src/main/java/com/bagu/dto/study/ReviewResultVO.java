package com.bagu.dto.study;

import com.bagu.dto.knowledge.ProgressVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "打分结果响应")
public class ReviewResultVO {

    private ProgressVO progress;
    private int doneToday;
}
