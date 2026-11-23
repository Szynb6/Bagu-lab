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
@Schema(description = "模块内篇目元信息")
public class ChapterVO {

    private Long id;
    private String code;
    private String title;
    private int questionCount;
}
