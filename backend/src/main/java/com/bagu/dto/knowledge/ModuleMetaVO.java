package com.bagu.dto.knowledge;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识模块元信息与篇目树")
public class ModuleMetaVO {

    private Long id;
    private String slug;
    private Integer order;
    private String title;
    private String fullTitle;
    private String icon;
    private String description;
    private String sprintGuide;
    private int questionCount;
    private int p0Count;
    private int p1Count;
    private int p2Count;
    private int imageCount;
    private List<ChapterVO> chapters;
}
