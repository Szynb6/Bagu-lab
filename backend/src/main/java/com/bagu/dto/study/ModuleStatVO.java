package com.bagu.dto.study;

import com.bagu.dto.knowledge.ChapterVO;
import com.bagu.dto.knowledge.ModuleMetaVO;
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
@Schema(description = "模块掌握度统计卡片")
public class ModuleStatVO {

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

    private int mastered;
    private int p0Mastered;
    private int learned;
    private int due;

    public static ModuleStatVO from(ModuleMetaVO m, int mastered, int p0Mastered, int learned, int due) {
        return ModuleStatVO.builder()
                .id(m.getId())
                .slug(m.getSlug())
                .order(m.getOrder())
                .title(m.getTitle())
                .fullTitle(m.getFullTitle())
                .icon(m.getIcon())
                .description(m.getDescription())
                .sprintGuide(m.getSprintGuide())
                .questionCount(m.getQuestionCount())
                .p0Count(m.getP0Count())
                .p1Count(m.getP1Count())
                .p2Count(m.getP2Count())
                .imageCount(m.getImageCount())
                .chapters(m.getChapters())
                .mastered(mastered)
                .p0Mastered(p0Mastered)
                .learned(learned)
                .due(due)
                .build();
    }
}
