package com.bagu.service;

import com.bagu.entity.StudyProgress;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * 简化版 SM-2 间隔重复调度算法（与前端 src/utils/sm2.ts 规则严格对齐）。
 *  1 完全不会：重置连续次数，10 分钟后重现，难度因子 -0.20，status = 1
 *  2 有点模糊：1 天后复习，难度因子 -0.15，status = 1
 *  3 基本掌握：1 -> 4 -> round(iv * ef)，iv >= 21 天标记已掌握(3)，否则复习中(2)
 *  4 秒杀跳过：4 -> round(iv * ef * 1.3)，难度因子 +0.15，iv >= 10 天标记已掌握(3)，否则复习中(2)
 */
@Component
public class Sm2Scheduler {

    private static final BigDecimal DEFAULT_EF = new BigDecimal("2.50");
    private static final BigDecimal MIN_EF = new BigDecimal("1.30");
    private static final BigDecimal MAX_EF = new BigDecimal("3.00");

    public void applyRating(StudyProgress sp, int rating, OffsetDateTime now) {
        double ef = sp.getEaseFactor() == null ? DEFAULT_EF.doubleValue() : sp.getEaseFactor().doubleValue();
        int iv = sp.getIntervalDays() == null ? 0 : sp.getIntervalDays();
        int reps = sp.getRepetitions() == null ? 0 : sp.getRepetitions();
        int lapses = sp.getLapseCount() == null ? 0 : sp.getLapseCount();

        OffsetDateTime nextReview;
        short status;

        switch (rating) {
            case 1 -> {
                reps = 0;
                iv = 0;
                ef = Math.max(MIN_EF.doubleValue(), ef - 0.20);
                lapses += 1;
                nextReview = now.plusMinutes(10);
                status = 1;
            }
            case 2 -> {
                iv = 1;
                ef = Math.max(MIN_EF.doubleValue(), ef - 0.15);
                nextReview = now.plusDays(1);
                status = 1;
            }
            case 3 -> {
                reps += 1;
                if (reps == 1) {
                    iv = 1;
                } else if (reps == 2) {
                    iv = 4;
                } else {
                    iv = Math.max(iv + 1, (int) Math.round(iv * ef));
                }
                nextReview = now.plusDays(iv);
                status = (short) (iv >= 21 ? 3 : 2);
            }
            case 4 -> {
                reps += 1;
                if (reps == 1) {
                    iv = 4;
                } else {
                    iv = Math.max(iv + 2, (int) Math.round(Math.max(iv, 1) * ef * 1.3));
                }
                ef = Math.min(MAX_EF.doubleValue(), ef + 0.15);
                nextReview = now.plusDays(iv);
                status = (short) (iv >= 10 ? 3 : 2);
            }
            default -> throw new IllegalArgumentException("Invalid rating: " + rating);
        }

        sp.setEaseFactor(BigDecimal.valueOf(ef).setScale(2, RoundingMode.HALF_UP));
        sp.setIntervalDays(iv);
        sp.setRepetitions(reps);
        sp.setLapseCount(lapses);
        sp.setLastRating((short) rating);
        sp.setNextReviewAt(nextReview);
        sp.setLastReviewedAt(now);
        sp.setStatus(status);
        sp.setUpdatedAt(now);
    }
}
