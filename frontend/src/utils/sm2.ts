import type { Progress, Rating, StudyStatus } from '@/types'

const DAY = 24 * 60 * 60 * 1000
const MIN = 60 * 1000

export function emptyProgress(questionId: number): Progress {
  return {
    questionId,
    status: 0,
    easeFactor: 2.5,
    intervalDays: 0,
    repetitions: 0,
    lapseCount: 0,
    lastRating: null,
    nextReviewAt: null,
    lastReviewedAt: null,
    isStarred: false,
    personalNote: '',
  }
}

/**
 * 简化版 SM-2 调度（与后端 StudyService 保持同一套规则）
 *  1 不会：重置连续次数，10 分钟后重现，难度因子 -0.20
 *  2 模糊：1 天后复习，难度因子 -0.15
 *  3 掌握：按 1 → 4 → interval × EF 递增
 *  4 秒杀：在“掌握”基础上额外 × 1.3，难度因子 +0.15
 */
export function schedule(p: Progress, rating: Rating, now = Date.now()): Progress {
  let { easeFactor: ef, intervalDays: iv, repetitions: reps, lapseCount } = p
  let next: number
  let status: StudyStatus

  switch (rating) {
    case 1:
      reps = 0
      iv = 0
      ef = Math.max(1.3, ef - 0.2)
      lapseCount += 1
      next = now + 10 * MIN
      status = 1
      break
    case 2:
      iv = 1
      ef = Math.max(1.3, ef - 0.15)
      next = now + DAY
      status = 1
      break
    case 3:
      reps += 1
      iv = reps === 1 ? 1 : reps === 2 ? 4 : Math.max(iv + 1, Math.round(iv * ef))
      next = now + iv * DAY
      status = iv >= 21 ? 3 : 2
      break
    case 4:
    default:
      reps += 1
      iv = reps === 1 ? 4 : Math.max(iv + 2, Math.round(Math.max(iv, 1) * ef * 1.3))
      ef = Math.min(3.0, ef + 0.15)
      next = now + iv * DAY
      status = iv >= 10 ? 3 : 2
      break
  }

  return {
    ...p,
    easeFactor: Math.round(ef * 100) / 100,
    intervalDays: iv,
    repetitions: reps,
    lapseCount,
    lastRating: rating,
    nextReviewAt: next,
    lastReviewedAt: now,
    status,
  }
}

/** 打分按钮下方展示的“下次出现时间” */
export function previewLabel(p: Progress, rating: Rating): string {
  const n = schedule(p, rating, 0)
  if (n.intervalDays === 0) return '10 分钟后'
  return `${n.intervalDays} 天后`
}
