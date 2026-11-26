<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, CheckCircle2, Eye, EyeOff, Loader2, Mail } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import SliderCaptchaModal from '@/components/common/SliderCaptchaModal.vue'

type AuthMode = 'login' | 'register' | 'reset'
type PendingCaptchaAction = 'login' | 'send-code' | null

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()

const mode = ref<AuthMode>('login')

// 登录表单字段（纯邮箱 + 密码登录）
const loginEmail = ref('')
const password = ref('')

// 注册 & 找回公共字段（username 保留用于右上角头像展示）
const email = ref('')
const code = ref('')
const regUsername = ref('')
const regPassword = ref('')

const showPwd = ref(false)
const loading = ref(false)
const sendingCode = ref(false)
const cooldown = ref(0)
const error = ref('')
const infoMsg = ref('')

// 滑块人机验证弹窗控制
const captchaModalOpen = ref(false)
const pendingAction = ref<PendingCaptchaAction>(null)

let cooldownTimer: number | undefined

function switchMode(next: AuthMode) {
  mode.value = next
  error.value = ''
  infoMsg.value = ''
}

function startCooldown(seconds = 60) {
  cooldown.value = seconds
  clearInterval(cooldownTimer)
  cooldownTimer = window.setInterval(() => {
    if (cooldown.value > 1) {
      cooldown.value -= 1
    } else {
      cooldown.value = 0
      clearInterval(cooldownTimer)
    }
  }, 1000)
}

onBeforeUnmount(() => {
  clearInterval(cooldownTimer)
})

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function requestSendCode() {
  const targetEmail = email.value.trim()
  error.value = ''
  infoMsg.value = ''
  if (!targetEmail || !EMAIL_RE.test(targetEmail)) {
    error.value = '请先输入格式正确的电子邮箱地址'
    return
  }
  if (cooldown.value > 0 || sendingCode.value) return

  pendingAction.value = 'send-code'
  captchaModalOpen.value = true
}

async function executeSendCode(captchaToken: string) {
  const targetEmail = email.value.trim()
  sendingCode.value = true
  try {
    const scene = mode.value === 'reset' ? 'reset' : 'register'
    await authApi.sendCode(targetEmail, scene, captchaToken)
    startCooldown(60)
    infoMsg.value = `验证码已发送至 ${targetEmail}（5 分钟内有效）`
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    sendingCode.value = false
  }
}

async function executeLogin(captchaToken: string) {
  loading.value = true
  try {
    await auth.login(loginEmail.value.trim(), password.value, captchaToken)
    router.replace((route.query.redirect as string) || '/')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function onCaptchaVerified(captchaToken: string) {
  captchaModalOpen.value = false
  const action = pendingAction.value
  pendingAction.value = null
  if (action === 'login') {
    executeLogin(captchaToken)
  } else if (action === 'send-code') {
    executeSendCode(captchaToken)
  }
}

async function submit() {
  error.value = ''
  infoMsg.value = ''

  if (mode.value === 'login') {
    const targetEmail = loginEmail.value.trim()
    if (!targetEmail || !EMAIL_RE.test(targetEmail)) {
      error.value = '请输入有效的登录邮箱地址'
      return
    }
    if (!password.value || password.value.length < 6) {
      error.value = '请输入至少 6 位登录密码'
      return
    }
    // 弹出滑块拼图验证，通过后自动携带 captchaToken 登录
    pendingAction.value = 'login'
    captchaModalOpen.value = true
    return
  }

  if (mode.value === 'register') {
    const targetEmail = email.value.trim()
    const trimmedCode = code.value.trim()
    const trimmedUser = regUsername.value.trim()
    if (!EMAIL_RE.test(targetEmail)) {
      error.value = '请输入有效的电子邮箱地址'
      return
    }
    if (!/^\d{6}$/.test(trimmedCode)) {
      error.value = '请输入 6 位数字邮箱验证码'
      return
    }
    if (!trimmedUser || trimmedUser.length > 32) {
      error.value = '请输入用户名（1~32 个字符，用于右上角头像展示）'
      return
    }
    if (regPassword.value.length < 6) {
      error.value = '密码长度至少为 6 位'
      return
    }

    loading.value = true
    try {
      await auth.register({
        email: targetEmail,
        code: trimmedCode,
        username: trimmedUser,
        password: regPassword.value,
      })
      ui.showToast('注册成功，欢迎来到 Bagu Lab')
      router.replace((route.query.redirect as string) || '/')
    } catch (e) {
      error.value = (e as Error).message
    } finally {
      loading.value = false
    }
    return
  }

  // mode === 'reset'
  const targetEmail = email.value.trim()
  const trimmedCode = code.value.trim()
  if (!EMAIL_RE.test(targetEmail)) {
    error.value = '请输入注册时使用的电子邮箱地址'
    return
  }
  if (!/^\d{6}$/.test(trimmedCode)) {
    error.value = '请输入 6 位数字邮箱验证码'
    return
  }
  if (regPassword.value.length < 6) {
    error.value = '新密码长度至少为 6 位'
    return
  }

  loading.value = true
  try {
    await authApi.resetPassword({
      email: targetEmail,
      code: trimmedCode,
      newPassword: regPassword.value,
    })
    loginEmail.value = targetEmail
    password.value = ''
    code.value = ''
    regPassword.value = ''
    mode.value = 'login'
    infoMsg.value = '密码已重置成功，请使用邮箱与新密码登录'
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

const specs = [
  { label: '核心模块', value: '09 卷' },
  { label: '精选真题', value: '375 道' },
  { label: '架构图解', value: '102 张' },
  { label: '交互式原理推演', value: '91 组' },
]

const shortcuts = [
  { key: 'Space', desc: '揭晓答案与解析' },
  { key: '1 - 4', desc: 'SM-2 四级记忆评分' },
  { key: 'Z', desc: '撤销上一题评分' },
  { key: 'H / S', desc: '提示关键词 / 收藏' },
]
</script>

<template>
  <div class="flex min-h-dvh items-center justify-center px-4 py-10">
    <div class="grid w-full max-w-4xl overflow-hidden rounded-2xl border border-[var(--border-default)] bg-[var(--bg-surface)] lg:grid-cols-12">
      <!-- 左侧工程手稿规格铭牌 -->
      <aside class="hidden flex-col justify-between border-r border-[var(--border-default)] bg-[var(--bg-sidebar)] p-8 lg:col-span-6 lg:flex">
        <div>
          <div class="flex items-center gap-3">
            <img src="/favicon.svg" class="h-9 w-9" alt="Bagu Lab" />
            <div>
              <p class="text-base font-semibold tracking-tight text-[var(--text-primary)]">Bagu Lab</p>
              <p class="font-mono text-[11px] text-[var(--text-muted)]">Java 后端面试记忆手稿</p>
            </div>
          </div>

          <p class="mt-8 text-2xl font-semibold leading-snug tracking-tight text-[var(--text-primary)]">
            主动回忆与 SM-2 间隔排期，<br />
            <span class="text-[var(--accent)]">把底层原理真正刻进肌肉记忆。</span>
          </p>

          <dl class="mt-7 grid grid-cols-2 divide-x divide-y overflow-hidden rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] [border-color:var(--border-default)] [&>div]:border-[var(--border-default)]">
            <div v-for="s in specs" :key="s.label" class="px-4 py-3">
              <dt class="text-[11px] text-[var(--text-muted)]">{{ s.label }}</dt>
              <dd class="mt-1 font-mono text-base font-semibold tabular-nums text-[var(--text-primary)]">{{ s.value }}</dd>
            </div>
          </dl>
        </div>

        <div class="mt-8 border-t border-[var(--border-default)] pt-5">
          <p class="mb-2.5 text-xs font-medium text-[var(--text-secondary)]">闪卡工作流快捷键</p>
          <div class="grid grid-cols-2 gap-x-4 gap-y-2 text-xs">
            <div v-for="sc in shortcuts" :key="sc.key" class="flex items-center gap-2">
              <kbd class="kbd shrink-0">{{ sc.key }}</kbd>
              <span class="truncate text-[var(--text-secondary)]">{{ sc.desc }}</span>
            </div>
          </div>
        </div>
      </aside>

      <!-- 右侧邮箱登录 / 邮箱注册 / 密码重置面板 -->
      <form class="flex flex-col justify-center p-7 sm:p-10 lg:col-span-6" @submit.prevent="submit">
        <div class="mb-6 flex items-center gap-2.5 lg:hidden">
          <img src="/favicon.svg" class="h-8 w-8" alt="Bagu Lab" />
          <span class="text-lg font-semibold tracking-tight">Bagu Lab</span>
        </div>

        <!-- 顶部模式切换器 -->
        <div class="mb-6 inline-flex self-start rounded-[10px] border border-[var(--border-default)] bg-[var(--bg-subtle)] p-1">
          <button
            type="button"
            class="rounded-md px-3.5 py-1.5 text-xs font-medium transition-colors"
            :class="
              mode === 'login'
                ? 'bg-[var(--bg-surface)] text-[var(--accent)] shadow-2xs'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            "
            @click="switchMode('login')"
          >
            邮箱登录
          </button>
          <button
            type="button"
            class="rounded-md px-3.5 py-1.5 text-xs font-medium transition-colors"
            :class="
              mode === 'register'
                ? 'bg-[var(--bg-surface)] text-[var(--accent)] shadow-2xs'
                : 'text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            "
            @click="switchMode('register')"
          >
            邮箱注册
          </button>
          <button
            v-if="mode === 'reset'"
            type="button"
            class="rounded-md bg-[var(--bg-surface)] px-3.5 py-1.5 text-xs font-medium text-[var(--accent)] shadow-2xs"
          >
            找回密码
          </button>
        </div>

        <h1 class="text-2xl font-semibold tracking-tight text-[var(--text-primary)]">
          <template v-if="mode === 'login'">登录学习工作台</template>
          <template v-else-if="mode === 'register'">创建个人学习账号</template>
          <template v-else>通过邮箱重置密码</template>
        </h1>
        <p class="mt-1 text-sm text-[var(--text-secondary)]">
          <template v-if="mode === 'login'">使用注册邮箱与密码登录，同步桌面与手机进度</template>
          <template v-else-if="mode === 'register'">验证邮箱即可注册，用户名将用于右上角头像展示</template>
          <template v-else>验证注册邮箱后即可设置新登录密码</template>
        </p>

        <!-- 模式 1：邮箱 + 密码登录 -->
        <div v-if="mode === 'login'" class="mt-6 space-y-4">
          <div>
            <label for="login-email" class="block text-sm font-medium text-[var(--text-primary)]">电子邮箱</label>
            <input
              id="login-email"
              v-model="loginEmail"
              type="email"
              autocomplete="email"
              class="mt-1.5 h-11 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[var(--accent)] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]"
              placeholder="name@example.com"
            />
          </div>

          <div>
            <div class="flex items-center justify-between">
              <label for="login-password" class="block text-sm font-medium text-[var(--text-primary)]">密码</label>
              <button
                type="button"
                class="text-xs text-[var(--text-muted)] hover:text-[var(--accent)]"
                @click="switchMode('reset')"
              >
                忘记密码？
              </button>
            </div>
            <div class="relative mt-1.5">
              <input
                id="login-password"
                v-model="password"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="current-password"
                class="h-11 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 pr-11 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[var(--accent)] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]"
                placeholder="请输入登录密码"
              />
              <button
                type="button"
                class="absolute right-1 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-md text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)] focus-visible:outline-2 focus-visible:outline-[var(--accent)]"
                :aria-label="showPwd ? '隐藏密码' : '显示密码'"
                @click="showPwd = !showPwd"
              >
                <component :is="showPwd ? EyeOff : Eye" class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        <!-- 模式 2：邮箱验证码注册 -->
        <div v-else-if="mode === 'register'" class="mt-6 space-y-3.5">
          <div>
            <label for="reg-email" class="block text-sm font-medium text-[var(--text-primary)]">电子邮箱</label>
            <div class="mt-1.5 flex gap-2">
              <input
                id="reg-email"
                v-model="email"
                type="email"
                autocomplete="email"
                class="h-10 min-w-0 flex-1 rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
                placeholder="name@example.com"
              />
              <button
                type="button"
                class="btn-ghost h-10 shrink-0 px-3 text-xs font-medium"
                :disabled="sendingCode || cooldown > 0"
                @click="requestSendCode"
              >
                <Loader2 v-if="sendingCode" class="h-3.5 w-3.5 animate-spin" />
                <Mail v-else class="h-3.5 w-3.5 text-[var(--accent)]" />
                <span class="font-mono tabular-nums">
                  {{ cooldown > 0 ? `${cooldown}s 后重发` : '获取验证码' }}
                </span>
              </button>
            </div>
          </div>

          <div class="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
            <div>
              <label for="reg-code" class="block text-sm font-medium text-[var(--text-primary)]">6 位邮箱验证码</label>
              <input
                id="reg-code"
                v-model="code"
                inputmode="numeric"
                maxlength="6"
                autocomplete="one-time-code"
                class="mt-1.5 h-10 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 font-mono text-sm tracking-widest text-[var(--text-primary)] outline-none transition-colors placeholder:font-sans placeholder:tracking-normal placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
                placeholder="6 位数字"
              />
            </div>

            <div>
              <label for="reg-username" class="block text-sm font-medium text-[var(--text-primary)]">用户名（头像展示）</label>
              <input
                id="reg-username"
                v-model="regUsername"
                autocomplete="nickname"
                class="mt-1.5 h-10 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
                placeholder="用于右上角头像展示"
              />
            </div>
          </div>

          <div>
            <label for="reg-password" class="block text-sm font-medium text-[var(--text-primary)]">设置登录密码</label>
            <div class="relative mt-1.5">
              <input
                id="reg-password"
                v-model="regPassword"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="new-password"
                class="h-10 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 pr-10 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
                placeholder="至少 6 位密码"
              />
              <button
                type="button"
                class="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-[var(--text-muted)] hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
                :aria-label="showPwd ? '隐藏密码' : '显示密码'"
                @click="showPwd = !showPwd"
              >
                <component :is="showPwd ? EyeOff : Eye" class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        <!-- 模式 3：通过邮箱验证码找回密码 -->
        <div v-else class="mt-6 space-y-3.5">
          <div>
            <label for="reset-email" class="block text-sm font-medium text-[var(--text-primary)]">注册电子邮箱</label>
            <div class="mt-1.5 flex gap-2">
              <input
                id="reset-email"
                v-model="email"
                type="email"
                autocomplete="email"
                class="h-10 min-w-0 flex-1 rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
                placeholder="输入注册时使用的邮箱"
              />
              <button
                type="button"
                class="btn-ghost h-10 shrink-0 px-3 text-xs font-medium"
                :disabled="sendingCode || cooldown > 0"
                @click="requestSendCode"
              >
                <Loader2 v-if="sendingCode" class="h-3.5 w-3.5 animate-spin" />
                <Mail v-else class="h-3.5 w-3.5 text-[var(--accent)]" />
                <span class="font-mono tabular-nums">
                  {{ cooldown > 0 ? `${cooldown}s 后重发` : '获取验证码' }}
                </span>
              </button>
            </div>
          </div>

          <div>
            <label for="reset-code" class="block text-sm font-medium text-[var(--text-primary)]">6 位邮箱验证码</label>
            <input
              id="reset-code"
              v-model="code"
              inputmode="numeric"
              maxlength="6"
              autocomplete="one-time-code"
              class="mt-1.5 h-10 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 font-mono text-sm tracking-widest text-[var(--text-primary)] outline-none transition-colors placeholder:font-sans placeholder:tracking-normal placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
              placeholder="输入邮件中的 6 位数字验证码"
            />
          </div>

          <div>
            <label for="reset-password" class="block text-sm font-medium text-[var(--text-primary)]">设置新密码</label>
            <div class="relative mt-1.5">
              <input
                id="reset-password"
                v-model="regPassword"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="new-password"
                class="h-10 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 pr-10 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[var(--accent)]"
                placeholder="请输入 6~64 位新密码"
              />
              <button
                type="button"
                class="absolute right-1 top-1/2 flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-[var(--text-muted)] hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)]"
                :aria-label="showPwd ? '隐藏密码' : '显示密码'"
                @click="showPwd = !showPwd"
              >
                <component :is="showPwd ? EyeOff : Eye" class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        <!-- 提示信息与错误告警 -->
        <p
          v-if="infoMsg"
          role="status"
          class="mt-4 flex items-center gap-2 rounded-lg border border-[#B8DCD1] bg-[#EBF4F1] px-3 py-2 text-xs font-medium text-[#235749] dark:border-[#244E41] dark:bg-[#162923] dark:text-[#86CBB7]"
        >
          <CheckCircle2 class="h-4 w-4 shrink-0" />
          <span>{{ infoMsg }}</span>
        </p>

        <p
          v-if="error"
          role="alert"
          class="mt-4 rounded-lg border border-[#EFC9BF] bg-[#FBF0ED] px-3 py-2 text-xs font-medium text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]"
        >
          {{ error }}
        </p>

        <button type="submit" class="btn-primary mt-6 h-11 w-full" :disabled="loading">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <template v-else-if="mode === 'login'">进入工作台 <ArrowRight class="h-4 w-4" /></template>
          <template v-else-if="mode === 'register'">完成注册并进入工作台 <ArrowRight class="h-4 w-4" /></template>
          <template v-else>确认重置密码 <ArrowRight class="h-4 w-4" /></template>
        </button>
      </form>
    </div>

    <!-- 拖动滑块拼图验证弹窗 -->
    <SliderCaptchaModal
      :open="captchaModalOpen"
      @close="captchaModalOpen = false; pendingAction = null"
      @verified="onCaptchaVerified"
    />
  </div>
</template>
