<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  ArrowRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Pause,
  Play,
  Plus,
  RotateCcw,
  Search,
  Lightbulb,
} from 'lucide-vue-next'

type Mode = 'guided' | 'sandbox'

interface LruNode {
  key: string
  val: string
  tag?: 'head-moved' | 'inserted' | 'updated'
}

interface LruGuidedStep {
  title: string
  badge: string
  list: LruNode[] // index 0 = MRU (Head), last = LRU (Tail)
  evictedKey: string | null
  activeKey: string | null
  opDesc: string
  speech: string
  tip: string
}

const CAPACITY = 4

const guidedSteps: LruGuidedStep[] = [
  {
    title: '0. 架构原理：HashMap (O(1)定位) + 双向链表 (O(1)移位淘汰)',
    badge: '双结构协同',
    list: [
      { key: 'C', val: '30' },
      { key: 'B', val: '20' },
      { key: 'A', val: '10' },
    ],
    evictedKey: null,
    activeKey: null,
    opDesc: 'capacity = 4，当前 size = 3',
    speech: '单靠 HashMap 无法记录访问顺序，单靠链表查找是 O(n)。因此经典 LRU 把 HashMap（key → Node* 指针）与双向链表（Head=MRU 最近使用，Tail=LRU 最久未用）组合，实现 get 和 put 严格 O(1)！',
    tip: '为什么必须用“双向”链表而不是单链表？因为把中间节点摘除移动到 Head，或者删除已知指针节点时，需要 O(1) 直接拿到前驱 node.prev！',
  },
  {
    title: '1. get("A") 命中：HashMap O(1) 定位 → 摘下移到 Head (MRU)',
    badge: 'get 命中提权',
    list: [
      { key: 'A', val: '10', tag: 'head-moved' },
      { key: 'C', val: '30' },
      { key: 'B', val: '20' },
    ],
    evictedKey: null,
    activeKey: 'A',
    opDesc: 'get("A") → return 10, A 移至 Head',
    speech: '调用 get("A")：先查 HashMap 瞬间定位到 Node(A)；把它从双向链表原位置解绑（prev.next = next），再插入到哨兵 Head 之后，A 从险些被淘汰的 Tail 跃升为 MRU 队头！',
    tip: '使用虚拟头尾哨兵节点 dummyHead / dummyTail 可以彻底消除空链表与单节点边界判断，面试手写非常加分。',
  },
  {
    title: '2. put("D", 40)：新 Key 插入 Head（容量刚好 4/4）',
    badge: 'put 插入队头',
    list: [
      { key: 'D', val: '40', tag: 'inserted' },
      { key: 'A', val: '10' },
      { key: 'C', val: '30' },
      { key: 'B', val: '20' },
    ],
    evictedKey: null,
    activeKey: 'D',
    opDesc: 'put("D", 40) → size = 4/4 (未溢出)',
    speech: 'put("D", 40) 查 HashMap 不存在，新建 Node(D) 写入 HashMap 并插到 Head 之后。此时 size=4 尚未超过 capacity(4)，最久未使用的 B 暂居 Tail。',
    tip: '如果 put 的 key 已经存在（Cache Hit），只需更新 Node.val 并将其移到 Head，千万不要重复插入新节点！',
  },
  {
    title: '3. put("E", 50)：容量超限 (>4) → 淘汰 Tail 节点 "B"！',
    badge: '满容淘汰 LRU',
    list: [
      { key: 'E', val: '50', tag: 'inserted' },
      { key: 'D', val: '40' },
      { key: 'A', val: '10' },
      { key: 'C', val: '30' },
    ],
    evictedKey: 'B',
    activeKey: 'E',
    opDesc: 'put("E", 50) → size=5 > 4 → 双删 Tail("B")',
    speech: '插入 E 后总数达到 5 > capacity(4)：立即定位 dummyTail.prev 即节点 B，同时执行两步删除：① 从双向链表摘除 B；② 从 HashMap.remove("B") 释放引用！',
    tip: '常见手写 Bug：只从链表删了 Tail 节点却忘了从 HashMap 删除对应 key，导致内存泄漏且后续 get("B") 误命中！',
  },
  {
    title: '4. JDK 现成实现：LinkedHashMap(cap, 0.75f, true)',
    badge: 'LinkedHashMap 源码',
    list: [
      { key: 'E', val: '50' },
      { key: 'D', val: '40' },
      { key: 'A', val: '10' },
      { key: 'C', val: '30' },
    ],
    evictedKey: 'B',
    activeKey: null,
    opDesc: 'accessOrder=true + 重写 removeEldestEntry()',
    speech: 'Java 标准库 LinkedHashMap 继承自 HashMap，内部维护了双向链表。构造时传入第 3 个参数 accessOrder = true（开启访问顺序），并在子类重写 removeEldestEntry(eldest) 返回 size() > capacity 即可 5 行代码实现工业级 LRU！',
    tip: '注意：LinkedHashMap 默认是非线程安全的；高并发场景可外层加锁，或参考 Caffeine 采用分段 RingBuffer + W-TinyLFU 提升并发吞吐与命中率。',
  },
]

function initialSandboxList(): LruNode[] {
  return [
    { key: 'D', val: '40' },
    { key: 'C', val: '30' },
    { key: 'B', val: '20' },
    { key: 'A', val: '10' },
  ]
}

const mode = ref<Mode>('guided')
const stepIdx = ref(0)
const playing = ref(false)
let playTimer: number | undefined

// 沙盒状态
const sbList = ref<LruNode[]>(initialSandboxList())
const sbEvicted = ref<string | null>(null)
const sbActiveKey = ref<string | null>(null)
const sbOpDesc = ref('capacity = 4，当前缓存已装满 [D(MRU) ↔ C ↔ B ↔ A(LRU)]')
const sbLog = ref('点击已有 Key 执行 get(key) 将其提权至 Head (MRU)；或点击 put("E") / 自定义 Key 观察满容淘汰 Tail！')
const customKey = ref('G')
const customVal = ref('70')

const cur = computed(() => {
  if (mode.value === 'guided') {
    const s = guidedSteps[stepIdx.value]!
    return {
      list: s.list,
      evictedKey: s.evictedKey,
      activeKey: s.activeKey,
      opDesc: s.opDesc,
    }
  }
  return {
    list: sbList.value,
    evictedKey: sbEvicted.value,
    activeKey: sbActiveKey.value,
    opDesc: sbOpDesc.value,
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

function clearTags() {
  sbList.value.forEach((n) => {
    n.tag = undefined
  })
}

function doGet(key: string) {
  clearTags()
  sbEvicted.value = null
  const idx = sbList.value.findIndex((n) => n.key === key)
  if (idx === -1) {
    sbActiveKey.value = null
    sbOpDesc.value = `get("${key}") → HashMap Miss (返回 -1)`
    sbLog.value = `get("${key}")：HashMap 未命中，返回 -1（链表顺序不变）。`
    return
  }
  const [node] = sbList.value.splice(idx, 1)
  node!.tag = 'head-moved'
  sbList.value.unshift(node!)
  sbActiveKey.value = key
  sbOpDesc.value = `get("${key}") → 命中 (${node!.val})，移至 Head (MRU)`
  sbLog.value = `get("${key}")：HashMap O(1) 命中 Node(${key})，从原第 ${idx + 1} 位摘除并插入 Head (MRU)！`
}

function doPut(rawKey: string, rawVal: string) {
  const key = rawKey.trim().slice(0, 6)
  const val = rawVal.trim().slice(0, 8) || '100'
  if (!key) return

  clearTags()
  sbEvicted.value = null
  const idx = sbList.value.findIndex((n) => n.key === key)

  if (idx !== -1) {
    const [node] = sbList.value.splice(idx, 1)
    node!.val = val
    node!.tag = 'updated'
    sbList.value.unshift(node!)
    sbActiveKey.value = key
    sbOpDesc.value = `put("${key}", ${val}) → 命中覆盖并移至 Head`
    sbLog.value = `put("${key}", ${val})：Key 已存在于 HashMap，原地更新 val=${val} 并移动到 Head (MRU)，未触发淘汰。`
    return
  }

  sbList.value.unshift({ key, val, tag: 'inserted' })
  sbActiveKey.value = key
  if (sbList.value.length > CAPACITY) {
    const evicted = sbList.value.pop()!
    sbEvicted.value = evicted.key
    sbOpDesc.value = `put("${key}", ${val}) → 超容淘汰 Tail("${evicted.key}")`
    sbLog.value = `put("${key}", ${val})：插入 Head 后 size=${CAPACITY + 1} > ${CAPACITY}，淘汰最久未使用的 Tail 节点【${evicted.key}】，并同步从 HashMap 移除！`
  } else {
    sbOpDesc.value = `put("${key}", ${val}) → 插入 Head (${sbList.value.length}/${CAPACITY})`
    sbLog.value = `put("${key}", ${val})：新建节点放入 HashMap 并插入 Head (MRU)，当前容量 ${sbList.value.length}/${CAPACITY}。`
  }
}

function submitCustomPut() {
  doPut(customKey.value, customVal.value)
}

function resetSandbox() {
  sbList.value = initialSandboxList()
  sbEvicted.value = null
  sbActiveKey.value = null
  sbOpDesc.value = 'capacity = 4，已重置为 [D(MRU) ↔ C ↔ B ↔ A(LRU)]'
  sbLog.value = 'LRU 沙盒已重置！尝试先点 get("A") 保住 A，再点 put("E") 看看谁被淘汰。'
}

function nodeCardClass(n: LruNode) {
  if (n.tag === 'inserted') {
    return 'border-emerald-500 bg-emerald-50 ring-2 ring-emerald-500/20 dark:border-emerald-400 dark:bg-emerald-500/15'
  }
  if (n.tag === 'head-moved' || n.tag === 'updated') {
    return 'border-brand-500 bg-brand-50 ring-2 ring-brand-500/20 dark:border-brand-400 dark:bg-brand-500/15'
  }
  return 'border-slate-300 bg-white dark:border-slate-600 dark:bg-slate-800'
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
          原理演练 (5 步)
        </button>
        <button
          class="rounded-lg px-3 py-1.5 font-medium transition"
          :class="mode === 'sandbox' ? 'bg-white text-brand-700 shadow-xs dark:bg-slate-900 dark:text-brand-300' : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'"
          @click="switchMode('sandbox')"
        >
          动手 LRU 沙盒
        </button>
      </div>

      <span
        v-if="mode === 'guided'"
        class="rounded-full bg-brand-100/80 px-2.5 py-0.5 font-mono text-[11px] font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-300"
      >
        {{ currentGuided.badge }} · {{ stepIdx + 1 }}/{{ guidedSteps.length }}
      </span>
      <span v-else class="font-mono text-[11px] text-slate-400">
        {{ cur.opDesc }}
      </span>
    </div>

    <!-- 双数据结构联动可视化：HashMap + Doubly Linked List -->
    <div class="space-y-3 rounded-xl border border-slate-200/80 bg-white p-3.5 dark:border-slate-800 dark:bg-slate-950/70">
      <!-- 1. HashMap (key -> Node*) -->
      <div class="rounded-xl border border-indigo-200/80 bg-indigo-50/40 p-3 dark:border-indigo-500/25 dark:bg-indigo-500/5">
        <div class="mb-2 flex items-center justify-between text-xs">
          <span class="font-bold text-indigo-800 dark:text-indigo-300">① HashMap&lt;K, Node*&gt;（O(1) 索引表）</span>
          <span class="font-mono text-[11px] text-indigo-600/80 dark:text-indigo-300/80">
            size = {{ cur.list.length }} / {{ CAPACITY }}
          </span>
        </div>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="node in cur.list"
            :key="`hm-${node.key}`"
            type="button"
            class="inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 font-mono text-xs transition"
            :class="[
              cur.activeKey === node.key
                ? 'border-brand-500 bg-brand-600 text-white shadow-xs'
                : 'border-indigo-200 bg-white text-slate-700 dark:border-indigo-500/30 dark:bg-slate-900 dark:text-slate-200',
              mode === 'sandbox' ? 'cursor-pointer hover:border-brand-400' : 'cursor-default',
            ]"
            :title="mode === 'sandbox' ? `点击执行 get(&quot;${node.key}&quot;)` : undefined"
            @click="mode === 'sandbox' && doGet(node.key)"
          >
            <span class="font-bold">"{{ node.key }}"</span>
            <ArrowRight class="h-3 w-3 opacity-70" />
            <span class="opacity-90">ptr({{ node.key }})</span>
          </button>

          <span
            v-if="cur.evictedKey"
            class="inline-flex items-center gap-1 rounded-lg border border-dashed border-red-300 bg-red-50 px-2.5 py-1.5 font-mono text-xs text-red-600 line-through dark:border-red-500/40 dark:bg-red-500/10 dark:text-red-300"
          >
            "{{ cur.evictedKey }}" 已同步从 Map 删除
          </span>
        </div>
      </div>

      <!-- 2. Doubly Linked List (Head MRU <-> Tail LRU) -->
      <div class="overflow-x-auto rounded-xl border border-slate-200 bg-slate-50/60 p-3 dark:border-slate-800 dark:bg-slate-900/60">
        <div class="mb-2.5 flex flex-wrap items-center justify-between gap-2 text-xs">
          <span class="font-bold text-slate-800 dark:text-slate-100">② 双向链表（按访问时间排序）</span>
          <span class="font-mono text-[10.5px] text-slate-500 dark:text-slate-400">
            左端 Head = 最近使用 (MRU) · 右端 Tail = 最久未用 (LRU 淘汰端)
          </span>
        </div>

        <div class="flex min-w-[520px] items-center gap-2 py-1">
          <!-- Dummy Head -->
          <div class="flex shrink-0 flex-col items-center rounded-lg border border-dashed border-emerald-400 bg-emerald-50/80 px-2.5 py-2 text-center dark:border-emerald-500/40 dark:bg-emerald-500/10">
            <span class="font-mono text-[11px] font-bold text-emerald-700 dark:text-emerald-300">Head</span>
            <span class="text-[9.5px] text-emerald-600/80 dark:text-emerald-300/80">MRU 哨兵</span>
          </div>

          <template v-for="(node, idx) in cur.list" :key="`dll-${node.key}`">
            <span class="shrink-0 font-mono text-xs font-bold text-slate-400">⇄</span>
            <div
              class="relative flex min-w-[92px] flex-col items-center rounded-xl border px-3 py-2 transition-[fill,stroke,opacity] duration-300"
              :class="nodeCardClass(node)"
            >
              <span
                v-if="node.tag"
                class="mb-0.5 rounded px-1.5 py-0.5 font-mono text-[9px] font-bold text-white"
                :class="node.tag === 'inserted' ? 'bg-emerald-500' : 'bg-brand-600'"
              >
                {{ node.tag === 'inserted' ? 'NEW 头插' : node.tag === 'updated' ? 'HIT 更新' : 'HIT 提权' }}
              </span>
              <span class="font-mono text-sm font-bold text-slate-900 dark:text-white">{{ node.key }}</span>
              <span class="font-mono text-[10.5px] text-slate-500 dark:text-slate-400">val = {{ node.val }}</span>
              <span class="mt-0.5 text-[9px] font-medium" :class="idx === 0 ? 'text-emerald-600 dark:text-emerald-400' : idx === cur.list.length - 1 ? 'text-amber-600 dark:text-amber-400' : 'text-slate-400'">
                {{ idx === 0 ? '最新 (MRU)' : idx === cur.list.length - 1 ? '最旧 (待淘汰)' : `#${idx + 1}` }}
              </span>
            </div>
          </template>

          <span class="shrink-0 font-mono text-xs font-bold text-slate-400">⇄</span>

          <!-- Dummy Tail -->
          <div class="flex shrink-0 flex-col items-center rounded-lg border border-dashed border-amber-400 bg-amber-50/80 px-2.5 py-2 text-center dark:border-amber-500/40 dark:bg-amber-500/10">
            <span class="font-mono text-[11px] font-bold text-amber-700 dark:text-amber-300">Tail</span>
            <span class="text-[9.5px] text-amber-600/80 dark:text-amber-300/80">LRU 哨兵</span>
          </div>
        </div>
      </div>

      <!-- 面试知识点提示条 -->
      <div class="rounded-lg border border-brand-200/70 bg-brand-50/50 px-3 py-2 text-xs text-slate-700 dark:border-brand-500/25 dark:bg-brand-500/10 dark:text-slate-300">
        <span class="font-semibold text-brand-700 dark:text-brand-300">Java 面试速记 · LinkedHashMap：</span>
        <code class="rounded bg-white/80 px-1 py-0.5 font-mono text-[11px] dark:bg-slate-900">new LinkedHashMap&lt;&gt;(cap, 0.75f, true)</code>
        将 <code class="font-mono text-[11px]">accessOrder = true</code> 设为按访问顺序维护双向链表，并重写
        <code class="font-mono text-[11px]">removeEldestEntry(e) { return size() &gt; cap; }</code> 即可。
      </div>
    </div>

    <!-- Guided 模式控制 -->
    <template v-if="mode === 'guided'">
      <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
        <p class="font-semibold text-slate-900 dark:text-white">{{ currentGuided.title }}</p>
        <p class="mt-1.5 leading-relaxed text-slate-600 dark:text-slate-300">{{ currentGuided.speech }}</p>
        <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
          <Lightbulb class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
          <span>{{ currentGuided.tip }}</span>
        </p>
      </div>

      <div class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex items-center gap-1.5">
          <button
            v-for="(_, i) in guidedSteps"
            :key="i"
            class="h-2 rounded-full transition-[width,background-color] duration-200"
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

      <!-- 第一排：现有 key get() 与快捷 put -->
      <div class="flex flex-wrap items-center gap-2">
        <button
          v-for="node in sbList"
          :key="`btn-get-${node.key}`"
          class="btn-ghost h-8 px-2.5 font-mono text-xs"
          @click="doGet(node.key)"
        >
          <Search class="h-3 w-3 text-brand-500" /> get("{{ node.key }}")
        </button>
        <button class="btn-ghost h-8 px-2.5 font-mono text-xs text-slate-500" @click="doGet('Z')">
          <Search class="h-3 w-3" /> get("Z") (Miss)
        </button>
        <button class="btn-primary h-8 px-3 font-mono text-xs" @click="doPut('E', '50')">
          <Plus class="h-3.5 w-3.5" /> put("E")
        </button>
        <button class="btn-primary h-8 px-3 font-mono text-xs" @click="doPut('F', '60')">
          <Plus class="h-3.5 w-3.5" /> put("F")
        </button>
      </div>

      <!-- 第二排：自定义 put(key, val) + 重置 -->
      <div class="flex flex-wrap items-center gap-2">
        <div class="flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-2.5 py-1 text-xs dark:border-slate-700 dark:bg-slate-900">
          <span class="font-mono text-slate-400">put(</span>
          <input
            v-model="customKey"
            type="text"
            maxlength="6"
            placeholder="key"
            class="w-12 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-800 outline-none focus:ring-1 focus:ring-brand-500 dark:bg-slate-800 dark:text-slate-100"
            @keyup.enter="submitCustomPut"
          />
          <span class="font-mono text-slate-400">,</span>
          <input
            v-model="customVal"
            type="text"
            maxlength="8"
            placeholder="val"
            class="w-14 rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-800 outline-none focus:ring-1 focus:ring-brand-500 dark:bg-slate-800 dark:text-slate-100"
            @keyup.enter="submitCustomPut"
          />
          <span class="font-mono text-slate-400">)</span>
        </div>
        <button class="btn-ghost h-8 px-3 text-xs" @click="submitCustomPut">
          <Plus class="h-3.5 w-3.5 text-emerald-500" /> 执行自定义 put
        </button>
        <button class="btn-ghost ml-auto h-8 px-3 text-xs" @click="resetSandbox">
          <RotateCcw class="h-3.5 w-3.5" /> 重置
        </button>
      </div>
    </template>
  </div>
</template>
