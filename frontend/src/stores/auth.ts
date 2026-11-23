import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { TOKEN_KEY } from '@/api/client'
import type { UserInfo } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<UserInfo | null>(JSON.parse(localStorage.getItem('bagu:user') ?? 'null'))
  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const res = await authApi.login(username, password)
    token.value = res.tokenValue
    user.value = res.user
    localStorage.setItem(TOKEN_KEY, res.tokenValue)
    localStorage.setItem('bagu:user', JSON.stringify(res.user))
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      token.value = null
      user.value = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('bagu:user')
    }
  }

  return { token, user, isLoggedIn, login, logout }
})
