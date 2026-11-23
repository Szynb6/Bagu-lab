<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Eye, EyeOff, Loader2 } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const showPwd = ref(false)
const loading = ref(false)
const error = ref('')

async function submit() {
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    router.replace((route.query.redirect as string) || '/')
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
            <span class="text-[#C84B31] dark:text-[#E06246]">把底层原理真正刻进肌肉记忆。</span>
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

      <!-- 右侧登录表单 -->
      <form class="flex flex-col justify-center p-7 sm:p-10 lg:col-span-6" @submit.prevent="submit">
        <div class="mb-7 flex items-center gap-2.5 lg:hidden">
          <img src="/favicon.svg" class="h-8 w-8" alt="Bagu Lab" />
          <span class="text-lg font-semibold tracking-tight">Bagu Lab</span>
        </div>

        <h1 class="text-2xl font-semibold tracking-tight text-[var(--text-primary)]">登录学习工作台</h1>
        <p class="mt-1.5 text-sm text-[var(--text-secondary)]">输入账号密码，同步桌面与移动端背诵进度</p>

        <div class="mt-7 space-y-4">
          <div>
            <label for="login-username" class="block text-sm font-medium text-[var(--text-primary)]">用户名</label>
            <input
              id="login-username"
              v-model="username"
              autocomplete="username"
              class="mt-1.5 h-11 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[#C84B31] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-500"
              placeholder="请输入用户名"
            />
          </div>

          <div>
            <label for="login-password" class="block text-sm font-medium text-[var(--text-primary)]">密码</label>
            <div class="relative mt-1.5">
              <input
                id="login-password"
                v-model="password"
                :type="showPwd ? 'text' : 'password'"
                autocomplete="current-password"
                class="h-11 w-full rounded-lg border border-[var(--border-default)] bg-[var(--bg-surface)] px-3.5 pr-11 text-sm text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] focus:border-[#C84B31] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-brand-500"
                placeholder="请输入密码"
              />
              <button
                type="button"
                class="absolute right-1 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-md text-[var(--text-muted)] transition-colors hover:bg-[var(--bg-subtle)] hover:text-[var(--text-primary)] focus-visible:outline-2 focus-visible:outline-brand-500"
                :aria-label="showPwd ? '隐藏密码' : '显示密码'"
                @click="showPwd = !showPwd"
              >
                <component :is="showPwd ? EyeOff : Eye" class="h-4 w-4" />
              </button>
            </div>
          </div>
        </div>

        <p
          v-if="error"
          role="alert"
          class="mt-4 rounded-lg border border-[#EFC9BF] bg-[#FBF0ED] px-3 py-2 text-sm text-[#96311D] dark:border-[#572C22] dark:bg-[#2E1B16] dark:text-[#EFC0B3]"
        >
          {{ error }}
        </p>

        <button type="submit" class="btn-primary mt-7 h-11 w-full" :disabled="loading">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <template v-else>进入工作台 <ArrowRight class="h-4 w-4" /></template>
        </button>
      </form>
    </div>
  </div>
</template>
