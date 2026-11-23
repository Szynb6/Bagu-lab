import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { TOKEN_KEY } from '@/api/client'
import type { LoginResult, RegisterPayload, UserInfo } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<UserInfo | null>(JSON.parse(localStorage.getItem('bagu:user') ?? 'null'))
  const isLoggedIn = computed(() => !!token.value)

  function clearUserSessionStorage() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem('bagu:user')
    localStorage.removeItem('bagu:lastSlug')
    sessionStorage.removeItem('bagu:customQueue')
    sessionStorage.removeItem('bagu:customLabel')
  }

  function applySession(res: LoginResult) {
    clearUserSessionStorage()
    token.value = res.tokenValue
    user.value = res.user
    localStorage.setItem(TOKEN_KEY, res.tokenValue)
    localStorage.setItem('bagu:user', JSON.stringify(res.user))
  }

  async function login(email: string, password: string, captchaToken: string) {
    const res = await authApi.login(email, password, captchaToken)
    applySession(res)
  }

  async function register(payload: RegisterPayload) {
    const res = await authApi.register(payload)
    applySession(res)
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      token.value = null
      user.value = null
      clearUserSessionStorage()
    }
  }

  async function deleteAccount(code: string) {
    await authApi.deleteAccount(code)
    token.value = null
    user.value = null
    clearUserSessionStorage()
  }

  return { token, user, isLoggedIn, login, register, logout, deleteAccount }
})
