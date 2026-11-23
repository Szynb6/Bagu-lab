<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, BookOpen, Brain, Eye, EyeOff, Keyboard, Loader2 } from 'lucide-vue-next'
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

const features = [
  { icon: Brain, title: '主动回忆 + SM-2', desc: '先想再看，遗忘曲线自动排期' },
  { icon: BookOpen, title: '9 大模块 · 375 题', desc: 'P0 / P1 / P2 考频分级，102 张原理图' },
  { icon: Keyboard, title: '全键盘极速刷题', desc: 'Space 翻面 · 1~4 自评 · H 提示 · S 收藏' },
]
</script>

<template>
  <div class="relative flex min-h-screen items-center justify-center overflow-hidden px-4 py-10">
    <div class="pointer-events-none absolute -left-40 -top-40 h-[480px] w-[480px] rounded-full bg-brand-500/15 blur-3xl" />
    <div class="pointer-events-none absolute -bottom-40 -right-32 h-[420px] w-[420px] rounded-full bg-indigo-500/15 blur-3xl" />

    <div class="relative grid w-full max-w-5xl items-center gap-12 lg:grid-cols-2">
      <div class="hidden lg:block">
        <div class="flex items-center gap-3">
          <img src="/favicon.svg" class="h-11 w-11" alt="logo" />
          <span class="text-2xl font-semibold tracking-tight">Bagu Lab</span>
        </div>
        <h2 class="mt-8 text-4xl font-semibold leading-tight tracking-tight">
          把 Java 八股<br />
          <span class="bg-gradient-to-r from-brand-600 to-indigo-500 bg-clip-text text-transparent">真正背进脑子里</span>
        </h2>
        <p class="mt-4 text-slate-500 dark:text-slate-400">个人专属的面试八股记忆引擎，电脑与手机进度实时同步。</p>
        <div class="mt-10 space-y-5">
          <div v-for="f in features" :key="f.title" class="flex items-start gap-4">
            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-white shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800">
              <component :is="f.icon" class="h-5 w-5 text-brand-600" />
            </span>
            <div>
              <p class="font-medium">{{ f.title }}</p>
              <p class="text-sm text-slate-500 dark:text-slate-400">{{ f.desc }}</p>
            </div>
          </div>
        </div>
      </div>

      <form class="card mx-auto w-full max-w-md p-8 sm:p-10" @submit.prevent="submit">
        <div class="mb-8 flex items-center gap-3 lg:hidden">
          <img src="/favicon.svg" class="h-9 w-9" alt="logo" />
          <span class="text-xl font-semibold">Bagu Lab</span>
        </div>
        <h3 class="text-2xl font-semibold tracking-tight">欢迎回来 👋</h3>
        <p class="mt-1.5 text-sm text-slate-500 dark:text-slate-400">登录后继续今天的复习计划</p>

        <label class="mt-8 block text-sm font-medium">用户名</label>
        <input
          v-model="username"
          autocomplete="username"
          class="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-white px-3.5 text-sm outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-950"
          placeholder="请输入用户名"
        />

        <label class="mt-5 block text-sm font-medium">密码</label>
        <div class="relative mt-2">
          <input
            v-model="password"
            :type="showPwd ? 'text' : 'password'"
            autocomplete="current-password"
            class="h-11 w-full rounded-xl border border-slate-200 bg-white px-3.5 pr-11 text-sm outline-none transition focus:border-brand-500 focus:ring-4 focus:ring-brand-500/15 dark:border-slate-700 dark:bg-slate-950"
            placeholder="请输入密码"
          />
          <button type="button" class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400" @click="showPwd = !showPwd">
            <component :is="showPwd ? EyeOff : Eye" class="h-4 w-4" />
          </button>
        </div>

        <p v-if="error" class="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600 dark:bg-red-500/10 dark:text-red-400">{{ error }}</p>

        <button type="submit" class="btn-primary mt-8 h-11 w-full" :disabled="loading">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
          <template v-else>登录 <ArrowRight class="h-4 w-4" /></template>
        </button>
      </form>
    </div>
  </div>
</template>
