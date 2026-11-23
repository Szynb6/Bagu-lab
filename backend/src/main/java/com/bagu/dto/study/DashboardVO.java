package com.bagu.dto.study;

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
@Schema(description = "首页复习仪表盘聚合数据")
public class DashboardVO {

    private int dueCount;
    private int newQuota;
    private int doneToday;
    private int dailyGoal;
    private int totalQuestions;
    private int totalMastered;
    private int totalLearned;
    private int p0Total;
    private int p0Mastered;
    private int streak;
    private int checkinDays;
    private List<HeatmapItemVO> heatmap;
    private int mistakeCount;
    private List<ModuleStatVO> modules;
}
