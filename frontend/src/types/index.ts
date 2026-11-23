/** 与 Spring Boot 后端 REST API 契约严格对齐的类型定义 */

export type Priority = 'P0' | 'P1' | 'P2'

/** 0 未背 · 1 待巩固（不会/模糊） · 2 复习中 · 3 已掌握 */
export type StudyStatus = 0 | 1 | 2 | 3

/** 1 不会 · 2 模糊 · 3 掌握 · 4 秒杀 */
export type Rating = 1 | 2 | 3 | 4

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
  totalPages: number
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
  dailyGoal: number
}

export interface LoginResult {
  tokenName: string
  tokenValue: string
  user: UserInfo
}

export interface Chapter {
  id: number
  title: string
  questionCount: number
}

export interface ModuleMeta {
  id: number
  slug: string
  order: number
  title: string
  fullTitle: string
  icon: string
  description: string
  sprintGuide: string
  questionCount: number
  p0Count: number
  p1Count: number
  p2Count: number
  imageCount: number
  chapters: Chapter[]
}

export interface QuestionImage {
  url: string
  caption: string
}

export interface ProgressSummary {
  status: StudyStatus
  lapseCount: number
  nextReviewAt: number | null
  isStarred: boolean
  hasNote: boolean
}

export interface QuestionItem {
  id: number
  moduleSlug: string
  moduleTitle: string
  chapterId: number
  code: string
  priority: Priority
  title: string
  imageCount: number
  hasTable: boolean
  hasCode: boolean
  order: number
  progress: ProgressSummary
}

export interface Progress {
  questionId: number
  status: StudyStatus
  easeFactor: number
  intervalDays: number
  repetitions: number
  lapseCount: number
  lastRating: Rating | null
  nextReviewAt: number | null
  lastReviewedAt: number | null
  isStarred: boolean
  personalNote: string
}

export interface QuestionWithProgress {
  id: number
  moduleSlug: string
  moduleTitle?: string
  chapterId: number
  code: string
  priority: Priority
  title: string
  hints: string[]
  answerMd: string
  images: QuestionImage[]
  hasTable: boolean
  hasCode: boolean
  order: number
  progress: Progress
}

export interface ModuleStat extends ModuleMeta {
  mastered: number
  p0Mastered: number
  learned: number
  due: number
}

export interface DashboardData {
  dueCount: number
  newQuota: number
  doneToday: number
  dailyGoal: number
  totalQuestions: number
  totalMastered: number
  totalLearned: number
  p0Total: number
  p0Mastered: number
  streak: number
  checkinDays: number
  heatmap: { date: string; count: number }[]
  mistakeCount: number
  modules: ModuleStat[]
}

export type QueueMode = 'today' | 'p0' | 'mistakes' | 'module' | 'custom' | 'single' | 'starred'

export interface QueueRequest {
  mode: QueueMode
  slug?: string
  chapterId?: number | string
  ids?: (number | string)[]
  id?: number | string
}

export interface QuestionFilter {
  slug?: string
  chapterId?: number
  priorities?: Priority[]
  status?: 'all' | 'new' | 'weak' | 'reviewing' | 'mastered' | 'starred' | 'noted'
  keyword?: string
  page?: number
  size?: number
}

export interface ReviewResult {
  progress: Progress
  doneToday: number
}
