/**
 * 统一后端 REST API 入口（对接 Spring Boot /api/*）。
 */
import { request } from './client'
import type {
  DashboardData, LoginResult, ModuleMeta, PageResult, QuestionFilter,
  QuestionItem, QuestionWithProgress, QueueRequest, Rating, RegisterPayload,
  ResetPasswordPayload, ReviewResult, SliderCaptchaData, SliderVerifyPayload,
  SliderVerifyResult, UserInfo,
} from '@/types'

export const authApi = {
  getSliderCaptcha: () =>
    request<SliderCaptchaData>({ url: '/auth/captcha/slider', method: 'GET' }),
  verifySliderCaptcha: (data: SliderVerifyPayload) =>
    request<SliderVerifyResult>({ url: '/auth/captcha/verify', method: 'POST', data }),
  login: (email: string, password: string, captchaToken: string) =>
    request<LoginResult>({ url: '/auth/login', method: 'POST', data: { email, password, captchaToken } }),
  sendCode: (email: string, scene: 'register' | 'reset', captchaToken: string) =>
    request<void>({ url: '/auth/send-code', method: 'POST', data: { email, scene, captchaToken } }),
  register: (data: RegisterPayload) =>
    request<LoginResult>({ url: '/auth/register', method: 'POST', data }),
  resetPassword: (data: ResetPasswordPayload) =>
    request<void>({ url: '/auth/reset-password', method: 'POST', data }),
  sendDeleteCode: () =>
    request<void>({ url: '/auth/send-delete-code', method: 'POST' }),
  deleteAccount: (code: string) =>
    request<void>({ url: '/auth/delete-account', method: 'POST', data: { code } }),
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
