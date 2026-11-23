/**
 * 统一后端 REST API 入口（对接 Spring Boot /api/*）。
 */
import { request } from './client'
import type {
  DashboardData, LoginResult, ModuleMeta, PageResult, QuestionFilter,
  QuestionItem, QuestionWithProgress, QueueRequest, Rating, ReviewResult, UserInfo,
} from '@/types'

export const authApi = {
  login: (username: string, password: string) =>
    request<LoginResult>({ url: '/auth/login', method: 'POST', data: { username, password } }),
  logout: () => request<void>({ url: '/auth/logout', method: 'POST' }),
  me: () => request<UserInfo>({ url: '/auth/me' }),
}

export const knowledgeApi = {
  modules: () => request<ModuleMeta[]>({ url: '/modules' }),
  reader: (slug: string, priority?: string) =>
    request<{ module: ModuleMeta; questions: QuestionWithProgress[] }>({
      url: `/modules/${slug}/reader`,
      params: priority ? { priority } : undefined,
    }),
  questions: (filter: QuestionFilter) =>
    request<PageResult<QuestionItem>>({
      url: '/questions',
      params: { ...filter, priorities: filter.priorities?.join(',') || undefined },
    }),
  questionIds: (filter: QuestionFilter) =>
    request<number[]>({
      url: '/questions/ids',
      params: {
        slug: filter.slug,
        chapterId: filter.chapterId,
        priorities: filter.priorities?.join(',') || undefined,
        status: filter.status,
        keyword: filter.keyword,
      },
    }),
  question: (id: number | string) =>
    request<QuestionWithProgress>({ url: `/questions/${id}` }),
}

export const studyApi = {
  dashboard: () => request<DashboardData>({ url: '/study/dashboard' }),
  queue: (req: QueueRequest) =>
    request<QuestionWithProgress[]>({
      url: '/study/queue',
      params: { ...req, ids: req.ids?.join(',') || undefined },
    }),
  review: (questionId: number, rating: Rating, durationSec: number) =>
    request<ReviewResult>({ url: '/study/review', method: 'POST', data: { questionId, rating, durationSec } }),
  toggleStar: (id: number) =>
    request<boolean>({ url: `/study/questions/${id}/star`, method: 'PATCH' }),
  saveNote: (id: number, note: string) =>
    request<void>({ url: `/study/questions/${id}/note`, method: 'PUT', data: { note } }),
}
