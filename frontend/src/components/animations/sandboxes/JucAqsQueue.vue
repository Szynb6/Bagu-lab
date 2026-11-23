<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Lock,
  Pause,
  Play,
  Plus,
  RefreshCw,
  RotateCcw,
  Sparkles,
  Unlock,
} from 'lucide-vue-next'

type Mode = 'guided' | 'sandbox'

interface ClhNode {
  id: string
  thread: string | null // null 表示哨兵 Head 节点
  waitStatus: number // -1 = SIGNAL, 0 = INITIAL
  parked: boolean
  highlight?: 'new' | 'waking' | 'normal'
}

interface AqsGuidedStep {
  title: string
  badge: string
  state: number
  owner: string | null
  queue: ClhNode[]
  actionTag: string
  speech: string
  tip: string
}

const MAX_WAITERS = 4

const guidedSteps: AqsGuidedStep[] = [
  {
    title: '0. AQS 初始态：state = 0，CLH 队列尚未初始化',
    badge: '无锁空闲',
    state: 0,
    owner: null,
    queue: [],
    actionTag: '等待线程抢锁',
    speech: 'AQS 内部维护三个核心成员：volatile int state（同步状态）、exclusiveOwnerThread（独占持有线程）以及由双向链表实现的 CLH 同步队列（懒初始化，初始 head = tail = null）。',
    tip: '面试开口定调：AQS = volatile state (CAS 原子修改) + 双向 FIFO 同步队列 (LockSupport.park/unpark 阻塞唤醒)。',
  },
  {
    title: '1. T1 抢锁成功：CAS(0 → 1) 直接占有锁',
    badge: 'CAS 成功 · owner=T1',
    state: 1,
    owner: 'T1',
    queue: [],
    actionTag: 'T1: compareAndSetState(0, 1) 成功',
    speech: '线程 T1 调用 lock()，直接尝试 CAS 将 state 从 0 改为 1；成功则把 exclusiveOwnerThread 设为 T1，整个过程无需入队！',
    tip: '非公平锁 (NonfairSync) 一上来直接 CAS 抢锁；公平锁会先检查 hasQueuedPredecessors() 队列里有没有前驱在等。',
  },
  {
    title: '2. T1 再次重入：同一线程直接 state + 1',
    badge: '可重入 · state=2',
    state: 2,
    owner: 'T1',
    queue: [],
    actionTag: 'T1 == exclusiveOwnerThread → state = 2',
    speech: 'T1 在持有锁期间再次调用 lock()，AQS 判断 currentThread == exclusiveOwnerThread，直接执行 state = 1 + 1 = 2（普通写即可，因为锁本来就归自己），不会发生自我死锁。',
    tip: '高频考点：ReentrantLock 重入多少次，就必须严格 unlock() 释放多少次，否则 state 归不了 0，后继线程永远被饿死！',
  },
  {
    title: '3. T2、T3 抢锁失败：懒初始化哨兵 Head 并尾插 CLH + park()',
    badge: 'CLH 排队阻塞',
    state: 2,
    owner: 'T1',
    queue: [
      { id: 'head', thread: null, waitStatus: -1, parked: false },
      { id: 'n2', thread: 'T2', waitStatus: -1, parked: true, highlight: 'new' },
      { id: 'n3', thread: 'T3', waitStatus: 0, parked: true, highlight: 'new' },
    ],
    actionTag: 'CAS 失败 → enq() 尾插 + LockSupport.park()',
    speech: 'T2、T3 抢锁 CAS 失败后调用 acquireQueued()：先懒初始化哨兵 Head 节点 (thread=null)，再通过 CAS 尾插 Node(T2)、Node(T3)，并将前驱 waitStatus 设为 SIGNAL(-1)（表示“后继需要我唤醒”），随后调用 LockSupport.park() 挂起！',
    tip: '为什么从后往前找有效节点 & 尾插前驱设为 -1？因为高并发入队时 tail 指针先通过 CAS 更新，prev 指针在并发下最可靠，且 waitStatus=-1 通知前驱释放时记得叫醒我。',
  },
  {
    title: '4. T1 第一次 release()：state 2 → 1（仍未完全释放）',
    badge: '部分释放 · 不唤醒',
    state: 1,
    owner: 'T1',
    queue: [
      { id: 'head', thread: null, waitStatus: -1, parked: false },
      { id: 'n2', thread: 'T2', waitStatus: -1, parked: true },
      { id: 'n3', thread: 'T3', waitStatus: 0, parked: true },
    ],
    actionTag: 'tryRelease(1): state = 1 (>0), 返回 false',
    speech: 'T1 调用一次 unlock()，state 从 2 减为 1。由于 state != 0，锁依然归 T1 所有，不会清理 exclusiveOwnerThread，也不会唤醒队列中的 T2。',
    tip: '只有最后一次 unlock() 将 state 减到 0 时，tryRelease() 才返回 true 并触发 unparkSuccessor()。',
  },
  {
    title: '5. T1 彻底释放 (state=0)：unpark(T2)，T2 抢锁并成为新 Head！',
    badge: '出队交接 · owner=T2',
    state: 1,
    owner: 'T2',
    queue: [
      { id: 'n2', thread: null, waitStatus: -1, parked: false, highlight: 'waking' },
      { id: 'n3', thread: 'T3', waitStatus: 0, parked: true },
    ],
    actionTag: 'unpark(T2) → T2 CAS(0→1) 成功 & setHead(Node(T2))',
    speech: 'T1 再次 unlock() 使 state 归 0，清空 owner 后调用 LockSupport.unpark(head.next) 唤醒 T2！T2 醒来 CAS 抢锁成功 (state=1, owner=T2)，并将自己置为新的哨兵 Head(thread=null)，旧 Head 断链被 GC 回收。',
    tip: '为什么叫“CLH 变体”？标准 CLH 是纯自旋，AQS 改成双向链表 + park/unpark 阻塞唤醒，避免空转耗尽 CPU。',
  },
]

const mode = ref<Mode>('guided')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

// 沙盒状态
const sbState = ref(0)
const sbOwner = ref<string | null>(null)
const sbQueue = ref<ClhNode[]>([])
const nextThreadNo = ref(1)
const sbActionTag = ref('等待线程抢锁')
const sbLog = ref('点击「新线程抢锁」开始体验 AQS 的 CAS 抢锁、重入计数、CLH 入队阻塞与释放唤醒！')

const cur = computed(() => {
  if (mode.value === 'guided') {
    const s = guidedSteps[stepIdx.value]!
    return {
      state: s.state,
      owner: s.owner,
      queue: s.queue,
      actionTag: s.actionTag,
    }
  }
  return {
    state: sbState.value,
    owner: sbOwner.value,
    queue: sbQueue.value,
    actionTag: sbActionTag.value,
  }
})

const currentGuided = computed(() => guidedSteps[stepIdx.value]!)

function stopPlay() {
  playing.value = false
  if (playTimer) {
    clearInterval(playTimer)
    playTimer = undefined
  }
}

function switchMode(m: Mode) {
  stopPlay()
  mode.value = m
}

function togglePlay() {
  if (playing.value) {
    stopPlay()
    return
  }
  if (stepIdx.value >= guidedSteps.length - 1) stepIdx.value = 0
  playing.value = true
  playTimer = window.setInterval(() => {
    if (stepIdx.value < guidedSteps.length - 1) {
      stepIdx.value += 1
    } else {
      stopPlay()
    }
  }, 2500)
}

function prevStep() {
  stopPlay()
  if (stepIdx.value > 0) stepIdx.value -= 1
}

function nextStep() {
  stopPlay()
  if (stepIdx.value < guidedSteps.length - 1) stepIdx.value += 1
}

function resetGuided() {
  stopPlay()
  stepIdx.value = 0
}

onBeforeUnmount(stopPlay)

function acquireNewThread() {
  const waitingCount = sbQueue.value.filter((n) => n.thread !== null).length
  if (sbState.value > 0 && waitingCount >= MAX_WAITERS) {
    sbLog.value = `演示队列最多展示 ${MAX_WAITERS} 个阻塞等待线程，请先点击「释放锁 (release)」观察唤醒出队！`
    return
  }

  const tName = `T${nextThreadNo.value++}`
  if (sbState.value === 0) {
    sbState.value = 1
    sbOwner.value = tName
    sbActionTag.value = `${tName}: CAS(0 → 1) 抢锁成功`
    sbLog.value = `${tName} 抢锁：state 原为 0，CAS(0 → 1) 一次成功！exclusiveOwnerThread = ${tName}，无需入队。`
    return
  }

  // 抢锁失败：若队列空则先初始化哨兵 Head
  sbQueue.value.forEach((n) => {
    n.highlight = 'normal'
  })
  const hadQueue = sbQueue.value.length > 0
  if (!hadQueue) {
    sbQueue.value.push({
      id: 'head-sentinel',
      thread: null,
      waitStatus: -1,
      parked: false,
      highlight: 'normal',
    })
  } else {
    const tailNode = sbQueue.value[sbQueue.value.length - 1]!
    tailNode.waitStatus = -1
  }

  sbQueue.value.push({
    id: `node-${tName}`,
    thread: tName,
    waitStatus: 0,
    parked: true,
    highlight: 'new',
  })

  sbActionTag.value = `${tName}: CAS 失败 → 尾插 CLH + park()`
  sbLog.value = hadQueue
    ? `${tName} 抢锁失败 (state=${sbState.value})：将前驱 waitStatus 置为 SIGNAL(-1)，${tName} 尾插进入 CLH 队列并调用 LockSupport.park() 挂起！`
    : `${tName} 抢锁失败 (state=${sbState.value})：懒初始化哨兵 Head(null, -1)，将 ${tName} 尾插为 Head.next 并调用 LockSupport.park() 挂起！`
}

function reenterLock() {
  if (!sbOwner.value) return
  sbState.value += 1
  sbActionTag.value = `${sbOwner.value} 重入 → state = ${sbState.value}`
  sbLog.value = `持有线程 ${sbOwner.value} 再次调用 lock()：判定 currentThread == exclusiveOwnerThread，直接 state + 1 变为 ${sbState.value}（可重入无死锁）！`
}

function releaseLock() {
  if (sbState.value === 0 || !sbOwner.value) return
  const prevOwner = sbOwner.value
  sbState.value -= 1

  if (sbState.value > 0) {
    sbActionTag.value = `${prevOwner} unlock() → state = ${sbState.value}`
    sbLog.value = `${prevOwner} 调用 release()：state 减为 ${sbState.value}（> 0），尚未完全释放重入锁，暂不唤醒 CLH 队列后继节点。`
    return
  }

  // state 归 0，彻底释放锁并唤醒后继
  sbOwner.value = null
  if (sbQueue.value.length > 1) {
    // 移除旧哨兵 Head，head.next 晋升为新哨兵并抢锁
    sbQueue.value.shift()
    const woken = sbQueue.value[0]!
    const wokenName = woken.thread ?? 'T?'
    woken.thread = null // 成为新哨兵 Head
    woken.parked = false
    woken.highlight = 'waking'
    if (sbQueue.value.length > 1) {
      woken.waitStatus = -1
    } else {
      woken.waitStatus = 0
    }
    sbState.value = 1
    sbOwner.value = wokenName
    sbActionTag.value = `${prevOwner} 归 0 → unpark(${wokenName}) 晋升 Head`
    sbLog.value = `${prevOwner} 彻底释放锁 (state=0) → unpark 唤醒 ${wokenName}！${wokenName} 苏醒 CAS(0→1) 获锁，并替代旧哨兵成为新的 Head 节点。`
  } else {
    sbQueue.value = []
    sbActionTag.value = `${prevOwner} 释放 → state = 0 (无等待者)`
    sbLog.value = `${prevOwner} 彻底释放锁 (state=0)，CLH 队列无等待线程，exclusiveOwnerThread 重置为 null。`
  }
}

function resetSandbox() {
  sbState.value = 0
  sbOwner.value = null
  sbQueue.value = []
  nextThreadNo.value = 1
  sbActionTag.value = '已重置为空闲态'
  sbLog.value = '沙盒已重置！点击「新线程抢锁」开始体验 AQS。'
}
</script>

<template>
  <div class="space-y-4">
    <!-- 模式切换 -->
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="inline-flex rounded-xl border border-slate-200 bg-slate-100/80 p-0.5 text-xs dark:border-slate-700 dark:bg-slate-800">
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'guided' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('guided')"
        >
          标准流程演练 (6 步)
        </button>
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'sandbox' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('sandbox')"
        >
          动手抢锁沙盒
        </button>
      </div>

      <span
        v-if="mode === 'guided'"
        class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300"
      >
        {{ currentGuided.badge }} · {{ stepIdx + 1 }}/{{ guidedSteps.length }}
      </span>
      <span v-else class="font-mono text-[11px] text-slate-400">
        {{ cur.actionTag }}
      </span>
    </div>

    <!-- AQS 核心状态 + CLH 双向队列 SVG/可视化区 -->
    <div class="rounded-xl border border-slate-200/80 bg-white p-3.5 dark:border-slate-800 dark:bg-slate-950/70">
      <!-- 顶部：state 与 exclusiveOwnerThread 实时看板 -->
      <div class="grid gap-3 sm:grid-cols-3">
        <div
          class="flex items-center justify-between rounded-xl border px-3.5 py-2.5 transition"
          :class="cur.state > 0
            ? 'border-brand-400/60 bg-brand-50/70 dark:border-brand-500/40 dark:bg-brand-500/10'
            : 'border-slate-200 bg-slate-50/80 dark:border-slate-800 dark:bg-slate-900'"
        >
          <div>
            <p class="font-mono text-[10.5px] text-slate-500 dark:text-slate-400">volatile int state</p>
            <p class="text-xs font-semibold text-slate-700 dark:text-slate-200">
              {{ cur.state === 0 ? '空闲 (可 CAS 抢占)' : `已加锁 (重入 ${cur.state} 次)` }}
            </p>
          </div>
          <span
            class="flex h-10 w-10 items-center justify-center rounded-xl font-mono text-lg font-bold transition"
            :class="cur.state > 0 ? 'bg-brand-600 text-white' : 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-300'"
          >
            {{ cur.state }}
          </span>
        </div>

        <div
          class="flex items-center justify-between rounded-xl border px-3.5 py-2.5 transition"
          :class="cur.owner
            ? 'border-emerald-400/60 bg-emerald-50/70 dark:border-emerald-500/40 dark:bg-emerald-500/10'
            : 'border-slate-200 bg-slate-50/80 dark:border-slate-800 dark:bg-slate-900'"
        >
          <div>
            <p class="font-mono text-[10.5px] text-slate-500 dark:text-slate-400">exclusiveOwnerThread</p>
            <p class="text-xs font-semibold text-slate-700 dark:text-slate-200">
              {{ cur.owner ? `独占持有线程：${cur.owner}` : 'null (无持有者)' }}
            </p>
          </div>
          <span
            class="flex h-10 min-w-10 items-center justify-center rounded-xl px-2 font-mono text-sm font-bold"
            :class="cur.owner ? 'bg-emerald-500 text-white' : 'bg-slate-200 text-slate-500 dark:bg-slate-800 dark:text-slate-400'"
          >
            <component :is="cur.owner ? Lock : Unlock" class="mr-1 h-3.5 w-3.5" />
            {{ cur.owner ?? 'null' }}
          </span>
        </div>

        <div class="flex flex-col justify-center rounded-xl border border-slate-200 bg-slate-50/80 px-3.5 py-2.5 dark:border-slate-800 dark:bg-slate-900">
          <p class="font-mono text-[10.5px] text-slate-500 dark:text-slate-400">动作跟踪 (CAS / AQS)</p>
          <p class="mt-0.5 truncate font-mono text-xs font-semibold text-brand-700 dark:text-brand-300">{{ cur.actionTag }}</p>
        </div>
      </div>

      <!-- 下方：CLH 双向 FIFO 队列 SVG -->
      <div class="mt-3 overflow-x-auto rounded-xl border border-slate-200/70 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-900/50">
        <div class="mb-2 flex items-center justify-between text-[11px]">
          <span class="font-semibold text-slate-700 dark:text-slate-200">CLH 双向同步队列 (FIFO Doubly-Linked Queue)</span>
          <span class="font-mono text-slate-400">
            排队等待线程: {{ cur.queue.filter((n) => n.thread !== null).length }} 个
          </span>
        </div>

        <svg viewBox="0 0 740 142" class="mx-auto w-full min-w-[560px] select-none" role="img" aria-label="AQS CLH 双向同步队列">
          <defs>
            <marker id="aqs-next" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto">
              <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
            </marker>
            <marker id="aqs-prev" viewBox="0 0 10 10" refX="2" refY="5" markerWidth="5" markerHeight="5" orient="auto">
              <path d="M 10 1 L 0 5 L 10 9 z" fill="currentColor" />
            </marker>
          </defs>

          <!-- 空队列提示 -->
          <g v-if="cur.queue.length === 0">
            <rect x="190" y="34" width="360" height="68" rx="12" class="fill-white stroke-slate-300 dark:fill-slate-800 dark:stroke-slate-600" stroke-dasharray="5 4" stroke-width="1.5" />
            <text x="370" y="64" text-anchor="middle" class="fill-slate-500 text-[12px] font-semibold dark:fill-slate-300">
              head == null, tail == null (CLH 队列尚未初始化)
            </text>
            <text x="370" y="84" text-anchor="middle" class="fill-slate-400 text-[10.5px]">
              只有当发生锁竞争 (CAS 失败) 时，才会懒加载哨兵 Head 并入队
            </text>
          </g>

          <!-- 非空 CLH 链表渲染 -->
          <template v-else>
            <g v-for="(node, idx) in cur.queue" :key="node.id" :transform="`translate(${22 + idx * 144}, 42)`">
              <!-- Head / Tail 顶部指针标识 -->
              <text
                v-if="idx === 0"
                x="58"
                y="-14"
                text-anchor="middle"
                class="fill-brand-600 font-mono text-[10.5px] font-bold dark:fill-brand-400"
              >
                ▼ head
              </text>
              <text
                v-if="idx === cur.queue.length - 1"
                x="58"
                :y="idx === 0 ? -26 : -14"
                text-anchor="middle"
                class="fill-purple-600 font-mono text-[10.5px] font-bold dark:fill-purple-400"
              >
                ▼ tail
              </text>

              <!-- Node 卡片 -->
              <rect
                width="116"
                height="68"
                rx="11"
                stroke-width="1.8"
                class="transition-all duration-300"
                :class="node.thread === null
                  ? node.highlight === 'waking'
                    ? 'fill-emerald-50 stroke-emerald-500 dark:fill-emerald-500/20 dark:stroke-emerald-400'
                    : 'fill-slate-100 stroke-slate-400 dark:fill-slate-800 dark:stroke-slate-500'
                  : node.highlight === 'new'
                    ? 'fill-amber-50 stroke-amber-500 dark:fill-amber-500/20 dark:stroke-amber-400'
                    : 'fill-white stroke-brand-400 dark:fill-slate-800 dark:stroke-brand-500'"
              />
              <text
                x="58"
                y="22"
                text-anchor="middle"
                class="text-[12px] font-bold"
                :class="node.thread === null ? 'fill-slate-700 dark:fill-slate-200' : 'fill-brand-700 dark:fill-brand-300'"
              >
                {{ node.thread === null ? 'Head (哨兵)' : `Node (${node.thread})` }}
              </text>
              <text x="58" y="40" text-anchor="middle" class="fill-slate-500 font-mono text-[10px] dark:fill-slate-400">
                ws={{ node.waitStatus }} ({{ node.waitStatus === -1 ? 'SIGNAL' : '0' }})
              </text>
              <rect
                x="18"
                y="47"
                width="80"
                height="15"
                rx="5"
                :class="node.parked ? 'fill-amber-500/90' : 'fill-emerald-500/90'"
              />
              <text x="58" y="58" text-anchor="middle" class="fill-white font-mono text-[9px] font-semibold">
                {{ node.parked ? 'PARKED 阻塞' : node.highlight === 'waking' ? '唤醒晋升 Head' : 'thread=null' }}
              </text>

              <!-- 与下一个节点之间的双向 prev / next 指针 -->
              <g v-if="idx < cur.queue.length - 1">
                <!-- next 指针 (向右) -->
                <line
                  x1="118"
                  y1="26"
                  x2="142"
                  y2="26"
                  stroke-width="1.8"
                  marker-end="url(#aqs-next)"
                  class="text-brand-500 stroke-brand-500"
                />
                <text x="130" y="20" text-anchor="middle" class="fill-brand-500 font-mono text-[8.5px]">next</text>
                <!-- prev 指针 (向左) -->
                <line
                  x1="118"
                  y1="44"
                  x2="142"
                  y2="44"
                  stroke-width="1.8"
                  marker-start="url(#aqs-prev)"
                  class="text-slate-400 stroke-slate-400"
                />
                <text x="130" y="56" text-anchor="middle" class="fill-slate-400 font-mono text-[8.5px]">prev</text>
              </g>
            </g>
          </template>
        </svg>
      </div>
    </div>

    <!-- Guided 模式讲解 + 控制 -->
    <template v-if="mode === 'guided'">
      <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
        <p class="font-semibold text-slate-900 dark:text-white">{{ currentGuided.title }}</p>
        <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ currentGuided.speech }}</p>
        <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
          <Sparkles class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
          <span>{{ currentGuided.tip }}</span>
        </p>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-1.5">
          <button
            v-for="(_, i) in guidedSteps"
            :key="i"
            class="h-2 rounded-full transition-all"
            :class="i === stepIdx ? 'w-6 bg-brand-600' : 'w-2 bg-slate-300 hover:bg-slate-400 dark:bg-slate-700'"
            :title="`第 ${i + 1} 步`"
            @click="stopPlay(); stepIdx = i"
          />
        </div>

        <div class="flex items-center gap-2">
          <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === 0" @click="prevStep">
            <ChevronLeft class="h-3.5 w-3.5" /> 上一步
          </button>
          <button class="btn-primary h-8 px-3 text-xs" @click="togglePlay">
            <component :is="playing ? Pause : Play" class="h-3.5 w-3.5" />
            {{ playing ? '暂停' : '自动播放' }}
          </button>
          <button class="btn-ghost h-8 px-2.5 text-xs" :disabled="stepIdx === guidedSteps.length - 1" @click="nextStep">
            下一步 <ChevronRight class="h-3.5 w-3.5" />
          </button>
          <button class="btn-ghost h-8 px-2 text-xs" title="重置" @click="resetGuided">
            <RotateCcw class="h-3.5 w-3.5" />
          </button>
        </div>
      </div>
    </template>

    <!-- Sandbox 模式控制 -->
    <template v-else>
      <div class="flex items-start gap-2 rounded-xl border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-xs leading-relaxed text-slate-600 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
        <CheckCircle2 class="mt-0.5 h-4 w-4 shrink-0 text-brand-500" />
        <span>{{ sbLog }}</span>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <button class="btn-primary h-9 px-3.5 text-xs" @click="acquireNewThread">
          <Plus class="h-3.5 w-3.5" /> 新线程抢锁 (T{{ nextThreadNo }})
        </button>
        <button class="btn-ghost h-9 px-3 text-xs" :disabled="!sbOwner" @click="reenterLock">
          <RefreshCw class="h-3.5 w-3.5 text-brand-500" /> 持有线程重入 (state+1)
        </button>
        <button class="btn-ghost h-9 px-3 text-xs" :disabled="sbState === 0" @click="releaseLock">
          <Unlock class="h-3.5 w-3.5 text-emerald-500" /> 释放锁 (release)
        </button>
        <button class="btn-ghost ml-auto h-9 px-3 text-xs" @click="resetSandbox">
          <RotateCcw class="h-3.5 w-3.5" /> 重置
        </button>
      </div>
    </template>
  </div>
</template>
