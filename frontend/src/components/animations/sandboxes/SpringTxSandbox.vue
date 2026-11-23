<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import {
  AlertOctagon,
  CheckCircle2,
  Database,
  Play,
  RotateCcw,
  ShieldAlert,
  Sparkles,
  XCircle,
} from 'lucide-vue-next'

type Propagation = 'REQUIRED' | 'REQUIRES_NEW' | 'NESTED'

interface TxResult {
  txStructure: string
  bMechanism: string
  rollbackOnlyTriggered: boolean
  unexpectedRollbackEx: boolean
  savepointUsed: boolean
  tx2Created: boolean
  aOutcome: 'COMMIT' | 'ROLLBACK'
  bOutcome: 'COMMIT' | 'ROLLBACK'
  headline: string
  phaseSteps: string[]
  explanation: string
  tip: string
}

interface Preset {
  label: string
  prop: Propagation
  outerThrows: boolean
  innerThrows: boolean
  caught: boolean
}

const presets: Preset[] = [
  {
    label: '坑点: REQUIRED + B抛 + A捕获',
    prop: 'REQUIRED',
    outerThrows: false,
    innerThrows: true,
    caught: true,
  },
  {
    label: 'REQUIRES_NEW + B抛 + A捕获',
    prop: 'REQUIRES_NEW',
    outerThrows: false,
    innerThrows: true,
    caught: true,
  },
  {
    label: 'REQUIRES_NEW + A抛异常',
    prop: 'REQUIRES_NEW',
    outerThrows: true,
    innerThrows: false,
    caught: false,
  },
  {
    label: 'NESTED + B抛 + A捕获',
    prop: 'NESTED',
    outerThrows: false,
    innerThrows: true,
    caught: true,
  },
]

const propagation = ref<Propagation>('REQUIRED')
const outerThrows = ref(false)
const innerThrows = ref(true)
const caughtByA = ref(true)
const animPhase = ref<number>(4) // 1..4 阶段展示
let phaseTimer: number | undefined

function computeResult(
  prop: Propagation,
  aThrows: boolean,
  bThrows: boolean,
  caught: boolean,
): TxResult {
  const effectiveCaught = bThrows && caught

  if (prop === 'REQUIRED') {
    if (!bThrows && !aThrows) {
      return {
        txStructure: '共用同一物理事务 TX1',
        bMechanism: 'B 直接加入 A 的 TX1（不新建事务）',
        rollbackOnlyTriggered: false,
        unexpectedRollbackEx: false,
        savepointUsed: false,
        tx2Created: false,
        aOutcome: 'COMMIT',
        bOutcome: 'COMMIT',
        headline: 'TX1 正常提交 → A 提交、B 提交',
        phaseSteps: [
          'ServiceA.outer() 开启物理事务 TX1',
          'ServiceB.inner() 检测到 TX1 存在，直接加入 TX1，写入 DB_B',
          'ServiceA 继续执行并写入 DB_A，无异常发生',
          '外层 A 方法正常返回，事务管理器提交 TX1（DB_A 与 DB_B 同时落库）',
        ],
        explanation: 'REQUIRED 下内外方法同属一个物理连接事务 TX1，均无异常则一起提交。',
        tip: 'REQUIRED 是 Spring @Transactional 的默认传播行为：外有事务则加入，外无事务则新建。',
      }
    }

    if (!bThrows && aThrows) {
      return {
        txStructure: '共用同一物理事务 TX1',
        bMechanism: 'B 加入 TX1 并写库，但尚未真正提交',
        rollbackOnlyTriggered: false,
        unexpectedRollbackEx: false,
        savepointUsed: false,
        tx2Created: false,
        aOutcome: 'ROLLBACK',
        bOutcome: 'ROLLBACK',
        headline: '外层抛出异常 → 整条 TX1 回滚 (A 回滚、B 回滚)',
        phaseSteps: [
          'ServiceA.outer() 开启物理事务 TX1',
          'ServiceB.inner() 加入 TX1 写入 DB_B 并返回（此时事务未提交！）',
          'ServiceA 写入 DB_A 后抛出 RuntimeException',
          'A 的事务切面捕获异常，回滚整个 TX1（DB_A 与 DB_B 全部撤销）',
        ],
        explanation: '因为 B 只是加入了 A 的 TX1，B 返回时 TX1 尚未 commit；A 后续抛异常会连带把 B 的写入一并回滚。',
        tip: '同一物理事务具有原子性：要么同生（一起 commit），要么共死（一起 rollback）。',
      }
    }

    if (bThrows && !effectiveCaught) {
      return {
        txStructure: '共用同一物理事务 TX1',
        bMechanism: 'B 抛异常 → 标记 TX1 rollback-only = true',
        rollbackOnlyTriggered: true,
        unexpectedRollbackEx: false,
        savepointUsed: false,
        tx2Created: false,
        aOutcome: 'ROLLBACK',
        bOutcome: 'ROLLBACK',
        headline: '内层 B 异常向上传播 → TX1 回滚 (A 回滚、B 回滚)',
        phaseSteps: [
          'ServiceA.outer() 开启物理事务 TX1',
          'ServiceB.inner() 写入 DB_B 后抛异常，Spring 切面将 TX1 标记为 rollback-only',
          '异常穿透回 ServiceA 且未被 try-catch 捕获，A 中断执行',
          'TX1 触发全面回滚（DB_A 与 DB_B 均回滚）',
        ],
        explanation: '内层异常直接传出外层切面，整个 TX1 回滚。',
        tip: '内层切面不仅会继续往上抛异常，还会提前在 TransactionStatus 中打上 rollback-only 标记。',
      }
    }

    // 经典高频踩坑：REQUIRED + B 抛异常 + A try-catch 捕获
    return {
      txStructure: '共用同一物理事务 TX1 (已污染 rollback-only)',
      bMechanism: 'B 切面捕获异常并偷偷把 TX1 标为 rollback-only = true！',
      rollbackOnlyTriggered: true,
      unexpectedRollbackEx: true,
      savepointUsed: false,
      tx2Created: false,
      aOutcome: 'ROLLBACK',
      bOutcome: 'ROLLBACK',
      headline: '触发 UnexpectedRollbackException！即使 A try-catch 了，A 与 B 仍全部回滚！',
      phaseSteps: [
        'ServiceA.outer() 开启物理事务 TX1',
        'ServiceB.inner() 写入 DB_B 后抛 RuntimeException，B 的 AOP 代理拦截后将共享 TX1 标记 rollback-only=true',
        'ServiceA 使用 try-catch 吞掉了 B 的异常，继续执行并写入 DB_A，尝试正常结束方法',
        'A 准备提交 TX1 时，Spring 检测到 rollbackOnly=true，强制回滚并抛出 UnexpectedRollbackException！',
      ],
      explanation: '最高频面试坑！很多同学以为 A 里 try-catch 了 B 就没事；实际上只要是同一个 REQUIRED 物理事务，B 退出 AOP 代理时已经把共享 TX1 标成了「只回滚 (rollback-only)」，导致外层提交时直接爆出 UnexpectedRollbackException，A 和 B 一起回滚！',
      tip: '避坑方案：如果希望 B 失败不影响 A 提交，要么用 REQUIRES_NEW，要么用 NESTED（配合 try-catch），绝不能直接 REQUIRED + try-catch！',
    }
  }

  if (prop === 'REQUIRES_NEW') {
    if (!bThrows && !aThrows) {
      return {
        txStructure: 'TX1 (挂起/恢复) + 独立物理事务 TX2',
        bMechanism: '挂起 TX1，为 B 新建独立数据库连接 TX2',
        rollbackOnlyTriggered: false,
        unexpectedRollbackEx: false,
        savepointUsed: false,
        tx2Created: true,
        aOutcome: 'COMMIT',
        bOutcome: 'COMMIT',
        headline: 'TX2 先提交，TX1 后提交 → A 提交、B 提交',
        phaseSteps: [
          'ServiceA.outer() 开启 TX1',
          '进入 B 时挂起 TX1，新建独立物理事务 TX2 并写入 DB_B',
          'B 返回后 TX2 立即独立提交！随后恢复 TX1，A 写入 DB_A',
          'A 返回后 TX1 正常提交（DB_A 与 DB_B 均持久化）',
        ],
        explanation: 'REQUIRES_NEW 会申请两个完全独立的物理事务连接，B 返回时 TX2 就已经真正提交到数据库。',
        tip: '注意连接池风险：A 挂起 TX1 不释放连接同时又占着 TX2，高并发下可能把数据库连接池打满导致死锁！',
      }
    }

    if (!bThrows && aThrows) {
      return {
        txStructure: 'TX1 (挂起后回滚) + 独立物理事务 TX2 (已提交)',
        bMechanism: 'B 独立提交 TX2 后才恢复 TX1',
        rollbackOnlyTriggered: false,
        unexpectedRollbackEx: false,
        savepointUsed: false,
        tx2Created: true,
        aOutcome: 'ROLLBACK',
        bOutcome: 'COMMIT',
        headline: 'A 异常回滚 TX1，但 B 的 TX2 早已提交！→ A 回滚、B 提交',
        phaseSteps: [
          'ServiceA.outer() 开启 TX1',
          '进入 B 时挂起 TX1，新建 TX2 写入 DB_B 并立即独立 COMMIT',
          '恢复 TX1，A 写入 DB_A 后抛出 RuntimeException',
          'TX1 回滚 DB_A；但 DB_B 已在独立事务 TX2 中落盘，不受 A 回滚影响！',
        ],
        explanation: '典型适用场景：审计日志/扣款流水。即便外层业务 A 最终失败回滚，内层 REQUIRES_NEW 的日志写入 TX2 已经独立提交保留下来。',
        tip: '这是 REQUIRES_NEW 与 NESTED 的核心差异：外层 A 抛异常时，REQUIRES_NEW 的 B 不会跟着回滚，而 NESTED 的 B 会跟着 TX1 一起回滚！',
      }
    }

    if (bThrows && !effectiveCaught) {
      return {
        txStructure: 'TX1 (因异常传播回滚) + TX2 (独立回滚)',
        bMechanism: 'TX2 自己回滚，随后异常继续传出把 TX1 也带崩',
        rollbackOnlyTriggered: false,
        unexpectedRollbackEx: false,
        savepointUsed: false,
        tx2Created: true,
        aOutcome: 'ROLLBACK',
        bOutcome: 'ROLLBACK',
        headline: 'B 未被捕获，异常上抛导致 TX2、TX1 先后回滚 → A 回滚、B 回滚',
        phaseSteps: [
          'ServiceA.outer() 开启 TX1',
          '进入 B 时挂起 TX1，新建 TX2 并写入 DB_B，随后 B 抛异常',
          'B 的切面回滚 TX2，并恢复 TX1；因 A 未捕获异常，异常继续穿透 A',
          'A 的切面回滚 TX1（A 和 B 均回滚，但原因并非 rollback-only）',
        ],
        explanation: '虽然 TX1 和 TX2 相互独立，但因为 A 没有 try-catch 住 B 抛出的异常，导致 A 自己也异常退出从而触发 TX1 回滚。',
        tip: '想让 REQUIRES_NEW 做到「B 败 A 成」，外层 A 必须显式 try-catch 住 B 抛出的异常！',
      }
    }

    // REQUIRES_NEW + B抛 + A捕获
    return {
      txStructure: 'TX1 (不受污染，正常提交) + TX2 (独立回滚)',
      bMechanism: 'TX2 独立回滚，TX1 全程被挂起完全隔离',
      rollbackOnlyTriggered: false,
      unexpectedRollbackEx: false,
      savepointUsed: false,
      tx2Created: true,
      aOutcome: 'COMMIT',
      bOutcome: 'ROLLBACK',
      headline: '隔离成功！TX2 回滚 DB_B，TX1 照常提交 DB_A → A 提交、B 回滚',
      phaseSteps: [
        'ServiceA.outer() 开启 TX1',
        '挂起 TX1，新建独立 TX2 执行 B 并抛异常 → B 的切面仅回滚 TX2',
        '恢复 TX1，A 用 try-catch 捕获异常后继续写入 DB_A',
        'A 正常结束，TX1 干净提交（DB_A 成功落库，DB_B 被回滚）！',
      ],
      explanation: '由于 TX2 与 TX1 是两条独立物理事务，TX2 回滚不会给 TX1 打任何 rollback-only 标记；A 捕获异常后 TX1 完美提交。',
      tip: '注意如果同时勾选「外层 A 抛异常」，TX1 也会因 A 自己的异常而回滚（A、B 全回滚）。',
    }
  }

  // NESTED (基于 JDBC Savepoint)
  if (!bThrows && !aThrows) {
    return {
      txStructure: '单一物理事务 TX1 + 内嵌 Savepoint sp1',
      bMechanism: 'B 在 TX1 内创建 JDBC Savepoint 保存点，返回时释放保存点',
      rollbackOnlyTriggered: false,
      unexpectedRollbackEx: false,
      savepointUsed: true,
      tx2Created: false,
      aOutcome: 'COMMIT',
      bOutcome: 'COMMIT',
      headline: 'TX1 统一提交 → A 提交、B 提交',
      phaseSteps: [
        'ServiceA.outer() 开启 TX1',
        '进入 B 时在 TX1 上设立保存点 Savepoint(sp1)，写入 DB_B 并释放 sp1',
        'ServiceA 继续写入 DB_A',
        '外层 A 结束，TX1 统一提交（DB_A 与 DB_B 同时落库）',
      ],
      explanation: 'NESTED 不会开第二个物理连接，只在同一个 TX1 上借用数据库的 Savepoint 机制实现局部回滚能力。',
      tip: 'NESTED 只需 1 个数据库连接，连接开销远低于 REQUIRES_NEW。',
    }
  }

  if (!bThrows && aThrows) {
    return {
      txStructure: '单一物理事务 TX1 (整条回滚)',
      bMechanism: 'B 创建并释放 sp1，但底层仍挂靠在 TX1 上未最终提交',
      rollbackOnlyTriggered: false,
      unexpectedRollbackEx: false,
      savepointUsed: true,
      tx2Created: false,
      aOutcome: 'ROLLBACK',
      bOutcome: 'ROLLBACK',
      headline: '外层 A 抛异常 → 整个 TX1 回滚 (A 回滚、B 回滚！)',
      phaseSteps: [
        'ServiceA.outer() 开启 TX1',
        '进入 B 设立 Savepoint(sp1)，写入 DB_B 后释放 sp1（未提交物理事务）',
        'ServiceA 写入 DB_A 后抛出 RuntimeException',
        '整个物理事务 TX1 回滚 → DB_A 与 DB_B 全部撤销！',
      ],
      explanation: '关键考点！与 REQUIRES_NEW 不同，NESTED 依赖外层 TX1 统一提交，外层 A 一旦失败回滚，内层 B 的修改也会跟着一起回滚！',
      tip: '面试高频对照：A 抛异常时，REQUIRES_NEW 的 B 已落库，NESTED 的 B 随 A 一起回滚！',
    }
  }

  if (bThrows && !effectiveCaught) {
    return {
      txStructure: 'TX1 + Savepoint sp1 (局部回滚后整条回滚)',
      bMechanism: 'B 回滚到 sp1 后异常未捕获，继续引爆外层 TX1',
      rollbackOnlyTriggered: false,
      unexpectedRollbackEx: false,
      savepointUsed: true,
      tx2Created: false,
      aOutcome: 'ROLLBACK',
      bOutcome: 'ROLLBACK',
      headline: 'B 回滚到 sp1 但异常未捕获 → 外层 TX1 跟着回滚 (A 回滚、B 回滚)',
      phaseSteps: [
        'ServiceA.outer() 开启 TX1',
        'B 在 TX1 内设置 Savepoint(sp1)，写入 DB_B 后抛异常，Spring 触发 ROLLBACK TO SAVEPOINT sp1',
        '异常向上抛给 A 且未被 try-catch 捕获，A 中断执行',
        '外层切面回滚整个 TX1（A、B 均回滚）',
      ],
      explanation: 'Savepoint 虽把 B 自己撤销了，但异常依然传到了 A 且未被处理，最终整个 TX1 还是要回滚。',
      tip: 'NESTED 想实现「B 败 A 成」，同样需要外层 A 配合 try-catch 捕获异常。',
    }
  }

  // NESTED + B抛 + A捕获
  return {
    txStructure: '单一物理事务 TX1 + Savepoint sp1 局部回滚',
    bMechanism: '回滚至 Savepoint(sp1)，且不会把 TX1 标成 rollback-only！',
    rollbackOnlyTriggered: false,
    unexpectedRollbackEx: false,
    savepointUsed: true,
    tx2Created: false,
    aOutcome: aThrows ? 'ROLLBACK' : 'COMMIT',
    bOutcome: 'ROLLBACK',
    headline: aThrows
      ? 'B 局部回滚至 sp1，但后续 A 也抛异常 → 最终 TX1 全回滚 (A 回滚、B 回滚)'
      : 'Savepoint 局部回滚成功！DB_B 撤销，TX1 照常提交 DB_A → A 提交、B 回滚',
    phaseSteps: [
      'ServiceA.outer() 开启 TX1',
      'B 建立 Savepoint(sp1) 后写入 DB_B 并抛异常 → Spring 执行 ROLLBACK TO SAVEPOINT sp1（不污染 TX1！）',
      aThrows
        ? 'A try-catch 捕获 B 异常后写入 DB_A，但随后 A 自己也抛出异常！'
        : 'A try-catch 捕获 B 异常后继续写入 DB_A 并正常返回',
      aThrows
        ? '外层 A 异常导致整个 TX1 回滚（A、B 全回滚）'
        : 'TX1 正常 COMMIT：DB_A 持久化成功，DB_B 因回到 sp1 已被撤销！',
    ],
    explanation: aThrows
      ? 'B 虽然通过 Savepoint 干净回滚了，但随后 A 自己抛出异常，导致整个 TX1 最终还是回滚。'
      : '这就是 NESTED + try-catch 的真正威力：只用 1 条物理连接，借助 Savepoint 仅撤销 B 的操作，不会给 TX1 标 rollback-only，外层 A 可正常提交！',
    tip: '面试总结：B 抛异常且 A try-catch 时，REQUIRED 会报 UnexpectedRollbackException 全回滚；REQUIRES_NEW 和 NESTED 都能实现「B 回滚、A 提交」。',
  }
}

// 注：如果用户同时勾了外层 A 抛异常 + 内层 B 抛异常 + A 捕获 (在 REQUIRES_NEW 时 A 也要回滚)
const result = computed<TxResult>(() => {
  const base = computeResult(
    propagation.value,
    outerThrows.value,
    innerThrows.value,
    caughtByA.value,
  )
  // 若 REQUIRES_NEW + B抛 + A捕获，且 A 自己后续又抛异常：A 回滚，B 回滚
  if (
    propagation.value === 'REQUIRES_NEW'
    && innerThrows.value
    && caughtByA.value
    && outerThrows.value
  ) {
    return {
      ...base,
      aOutcome: 'ROLLBACK',
      bOutcome: 'ROLLBACK',
      headline: 'TX2 独立回滚，随后 A 自己又抛异常导致 TX1 也回滚 → A 回滚、B 回滚',
      phaseSteps: [
        base.phaseSteps[0]!,
        base.phaseSteps[1]!,
        '恢复 TX1，A try-catch 住 B 的异常并写入 DB_A，但随后 A 自己抛出异常',
        'A 的切面回滚 TX1，DB_A 与 DB_B 最终全部回滚',
      ],
      explanation: 'B 的 TX2 因 B 异常已回滚；A 虽捕获了 B 的异常，但后续 A 自己又抛出异常，因此 TX1 也回滚。',
    }
  }
  return base
})

function stopTimer() {
  if (phaseTimer) {
    clearInterval(phaseTimer)
    phaseTimer = undefined
  }
}

function runSimulation() {
  stopTimer()
  animPhase.value = 1
  phaseTimer = window.setInterval(() => {
    if (animPhase.value < 4) {
      animPhase.value += 1
    } else {
      stopTimer()
    }
  }, 480)
}

function applyPreset(p: Preset) {
  propagation.value = p.prop
  outerThrows.value = p.outerThrows
  innerThrows.value = p.innerThrows
  caughtByA.value = p.caught
  runSimulation()
}

function resetAll() {
  stopTimer()
  propagation.value = 'REQUIRED'
  outerThrows.value = false
  innerThrows.value = true
  caughtByA.value = true
  animPhase.value = 4
}

onBeforeUnmount(stopTimer)
</script>

<template>
  <div class="space-y-4">
    <!-- 4 个经典预设场景一键切换 -->
    <div class="space-y-1.5">
      <div class="text-xs font-semibold text-slate-600 dark:text-slate-300">经典高频面试预设（一键装载）：</div>
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="p in presets"
          :key="p.label"
          class="rounded-xl border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-medium text-slate-700 transition hover:border-brand-400 hover:bg-brand-50 hover:text-brand-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:border-brand-500/50 dark:hover:bg-brand-500/15 dark:hover:text-blue-300"
          @click="applyPreset(p)"
        >
          {{ p.label }}
        </button>
      </div>
    </div>

    <!-- 自定义交互控制面板 -->
    <div class="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-800 dark:bg-slate-900/70">
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex items-center gap-1.5">
          <span class="text-xs font-semibold text-slate-600 dark:text-slate-300">B 的传播行为:</span>
          <div class="inline-flex rounded-lg border border-slate-200 bg-white p-0.5 font-mono text-xs dark:border-slate-700 dark:bg-slate-800">
            <button
              v-for="prop in (['REQUIRED', 'REQUIRES_NEW', 'NESTED'] as Propagation[])"
              :key="prop"
              class="rounded-md px-2.5 py-1 font-semibold transition"
              :class="propagation === prop
                ? 'bg-brand-600 text-white'
                : 'text-slate-600 hover:text-slate-900 dark:text-slate-300'"
              @click="propagation = prop; animPhase = 4"
            >
              {{ prop }}
            </button>
          </div>
        </div>

        <label class="inline-flex cursor-pointer items-center gap-1.5 text-xs text-slate-700 dark:text-slate-200">
          <input v-model="innerThrows" type="checkbox" class="h-3.5 w-3.5 rounded accent-brand-600" @change="animPhase = 4" />
          内层 B 抛异常
        </label>

        <label
          class="inline-flex items-center gap-1.5 text-xs"
          :class="innerThrows ? 'cursor-pointer text-slate-700 dark:text-slate-200' : 'cursor-not-allowed text-slate-400'"
        >
          <input
            v-model="caughtByA"
            type="checkbox"
            :disabled="!innerThrows"
            class="h-3.5 w-3.5 rounded accent-brand-600"
            @change="animPhase = 4"
          />
          A try-catch 捕获 B 异常
        </label>

        <label class="inline-flex cursor-pointer items-center gap-1.5 text-xs text-slate-700 dark:text-slate-200">
          <input v-model="outerThrows" type="checkbox" class="h-3.5 w-3.5 rounded accent-brand-600" @change="animPhase = 4" />
          外层 A 抛异常
        </label>
      </div>

      <div class="flex items-center gap-2">
        <button class="btn-primary h-8 px-3 text-xs" @click="runSimulation">
          <Play class="h-3.5 w-3.5" /> 执行模拟
        </button>
        <button class="btn-ghost h-8 px-2.5 text-xs" title="重置" @click="resetAll">
          <RotateCcw class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <!-- 事务调用链 & TX1 / TX2 / Savepoint 状态 SVG -->
    <div class="overflow-x-auto rounded-xl border border-slate-200/80 bg-white p-3 dark:border-slate-800 dark:bg-slate-950/70">
      <svg viewBox="0 0 760 264" class="mx-auto w-full min-w-[600px] max-w-[740px] select-none" role="img" aria-label="Spring 事务传播机制 TX1 TX2 Savepoint 状态演示">
        <defs>
          <marker id="tx-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 1 L 10 5 L 0 9 z" fill="currentColor" />
          </marker>
        </defs>

        <!-- ServiceA.outer() 卡片 -->
        <g transform="translate(20, 18)">
          <rect
            width="285"
            height="112"
            rx="14"
            stroke-width="1.8"
            class="transition-all duration-300"
            :class="animPhase >= 1
              ? 'fill-brand-50/70 stroke-brand-500 dark:fill-brand-500/15 dark:stroke-brand-500/60'
              : 'fill-slate-50 stroke-slate-200 dark:fill-slate-900 dark:stroke-slate-700'"
          />
          <text x="16" y="25" class="font-mono text-[12px] font-bold fill-brand-700 dark:fill-blue-300">
            ServiceA.outer()
          </text>
          <text x="16" y="42" class="font-mono text-[10px] fill-slate-500 dark:fill-slate-400">
            @Transactional(REQUIRED)
          </text>

          <!-- B 调用状态标签 -->
          <rect x="16" y="54" width="253" height="22" rx="6" class="fill-white stroke-slate-200 dark:fill-slate-900 dark:stroke-slate-700" stroke-width="1" />
          <text x="26" y="69" class="font-mono text-[10.5px] fill-slate-700 dark:fill-slate-200">
            {{ innerThrows && caughtByA ? 'try { b.inner() } catch (e) { 捕获! }' : 'serviceB.inner(); // 无 try-catch' }}
          </text>

          <!-- A 的 DB 写入结果 -->
          <rect
            x="16"
            y="82"
            width="253"
            height="22"
            rx="6"
            class="transition-all duration-300"
            :class="animPhase < 4
              ? 'fill-slate-200/80 dark:fill-slate-800'
              : result.aOutcome === 'COMMIT'
                ? 'fill-emerald-500'
                : 'fill-red-500'"
          />
          <text
            x="142"
            y="96.5"
            text-anchor="middle"
            class="font-mono text-[10.5px] font-bold"
            :class="animPhase < 4 ? 'fill-slate-600 dark:fill-slate-300' : 'fill-white'"
          >
            {{
              animPhase < 4
                ? `DB_A 写入${outerThrows ? ' + A 抛异常' : ' (等待提交)'}`
                : result.aOutcome === 'COMMIT'
                  ? 'DB_A 写操作 → ✓ COMMIT (提交成功)'
                  : 'DB_A 写操作 → ✗ ROLLBACK (已回滚)'
            }}
          </text>
        </g>

        <!-- A -> B 调用箭头 -->
        <path
          d="M 308 66 L 448 66"
          fill="none"
          stroke-width="2.4"
          marker-end="url(#tx-arrow)"
          class="transition-all duration-300"
          :class="animPhase >= 2 ? 'stroke-brand-500 text-brand-500 [stroke-dasharray:5_3]' : 'stroke-slate-300 text-slate-300 dark:stroke-slate-700'"
        />
        <text x="378" y="57" text-anchor="middle" class="font-mono text-[10px] font-semibold fill-slate-500 dark:fill-slate-400">
          代理调用 inner()
        </text>
        <text
          v-if="innerThrows && animPhase >= 2"
          x="378"
          y="85"
          text-anchor="middle"
          class="font-mono text-[10px] font-bold fill-red-500"
        >
          ← 抛出 RuntimeException
        </text>

        <!-- ServiceB.inner() 卡片 -->
        <g transform="translate(454, 18)">
          <rect
            width="286"
            height="112"
            rx="14"
            stroke-width="1.8"
            class="transition-all duration-300"
            :class="animPhase >= 2
              ? innerThrows
                ? 'fill-red-50/70 stroke-red-400 dark:fill-red-500/15 dark:stroke-red-400'
                : 'fill-indigo-50/70 stroke-indigo-400 dark:fill-indigo-500/15 dark:stroke-indigo-400'
              : 'fill-slate-50 stroke-slate-200 dark:fill-slate-900 dark:stroke-slate-700'"
          />
          <text x="16" y="25" class="font-mono text-[12px] font-bold fill-indigo-700 dark:fill-indigo-300">
            ServiceB.inner()
          </text>
          <text x="16" y="42" class="font-mono text-[10px] fill-slate-500 dark:fill-slate-400">
            @Transactional({{ propagation }})
          </text>

          <rect x="16" y="54" width="254" height="22" rx="6" class="fill-white stroke-slate-200 dark:fill-slate-900 dark:stroke-slate-700" stroke-width="1" />
          <text x="26" y="69" class="font-mono text-[10.5px]" :class="innerThrows ? 'fill-red-600 font-semibold dark:fill-red-300' : 'fill-slate-700 dark:fill-slate-200'">
            {{ innerThrows ? 'writeDB_B(); throw RuntimeException!' : 'writeDB_B(); return ok;' }}
          </text>

          <!-- B 的 DB 写入结果 -->
          <rect
            x="16"
            y="82"
            width="254"
            height="22"
            rx="6"
            class="transition-all duration-300"
            :class="animPhase < 4
              ? 'fill-slate-200/80 dark:fill-slate-800'
              : result.bOutcome === 'COMMIT'
                ? 'fill-emerald-500'
                : 'fill-red-500'"
          />
          <text
            x="143"
            y="96.5"
            text-anchor="middle"
            class="font-mono text-[10.5px] font-bold"
            :class="animPhase < 4 ? 'fill-slate-600 dark:fill-slate-300' : 'fill-white'"
          >
            {{
              animPhase < 4
                ? 'DB_B 写操作 (执行中...)'
                : result.bOutcome === 'COMMIT'
                  ? 'DB_B 写操作 → ✓ COMMIT (提交成功)'
                  : 'DB_B 写操作 → ✗ ROLLBACK (已回滚)'
            }}
          </text>
        </g>

        <!-- 下半区：底层物理事务 TX1 / TX2 / Savepoint 可视化条 -->
        <rect x="20" y="148" width="720" height="104" rx="14" class="fill-slate-50 stroke-slate-200 dark:fill-slate-900/70 dark:stroke-slate-800" stroke-width="1.5" />
        <text x="36" y="168" class="text-[11px] font-semibold fill-slate-600 dark:fill-slate-300">
          底层数据库连接与物理事务状态 ({{ result.txStructure }})
        </text>

        <!-- REQUIRED 单一 TX1 条 -->
        <g v-if="propagation === 'REQUIRED'" transform="translate(36, 180)">
          <rect
            width="688"
            height="56"
            rx="10"
            stroke-width="2"
            :class="animPhase >= 4 && result.aOutcome === 'COMMIT'
              ? 'fill-emerald-50 stroke-emerald-500 dark:fill-emerald-500/15'
              : result.rollbackOnlyTriggered
                ? 'fill-red-50 stroke-red-500 dark:fill-red-500/15'
                : 'fill-brand-50 stroke-brand-500 dark:fill-brand-500/15'"
          />
          <text x="20" y="24" class="font-mono text-[12px] font-bold fill-slate-800 dark:fill-white">
            物理事务 TX1 (A 创建 → B 直接加入共用)
          </text>
          <text x="20" y="44" class="text-[11px] fill-slate-600 dark:fill-slate-300">
            {{ result.bMechanism }}
          </text>
          <g v-if="result.rollbackOnlyTriggered && animPhase >= 2" transform="translate(460, 12)">
            <rect width="212" height="30" rx="8" class="fill-red-500" />
            <text x="106" y="20" text-anchor="middle" class="font-mono text-[11px] font-bold fill-white">
              ⚠ rollbackOnly = true
            </text>
          </g>
        </g>

        <!-- REQUIRES_NEW: TX1 挂起 + TX2 独立 -->
        <g v-else-if="propagation === 'REQUIRES_NEW'" transform="translate(36, 180)">
          <rect
            x="0"
            y="0"
            width="324"
            height="56"
            rx="10"
            stroke-width="2"
            :class="animPhase >= 4 && result.aOutcome === 'COMMIT'
              ? 'fill-emerald-50 stroke-emerald-500 dark:fill-emerald-500/15'
              : animPhase >= 4
                ? 'fill-red-50 stroke-red-500 dark:fill-red-500/15'
                : 'fill-brand-50 stroke-brand-500 dark:fill-brand-500/15'"
          />
          <text x="14" y="23" class="font-mono text-[11.5px] font-bold fill-slate-800 dark:fill-white">
            物理事务 TX1 (外层 A 专属)
          </text>
          <text x="14" y="43" class="text-[10.5px] fill-slate-600 dark:fill-slate-300">
            B 执行期间被挂起 (Suspended)，不受 TX2 污染
          </text>

          <rect
            x="348"
            y="0"
            width="340"
            height="56"
            rx="10"
            stroke-width="2"
            :class="animPhase >= 3 && result.bOutcome === 'COMMIT'
              ? 'fill-emerald-50 stroke-emerald-500 dark:fill-emerald-500/15'
              : animPhase >= 3
                ? 'fill-red-50 stroke-red-500 dark:fill-red-500/15'
                : 'fill-indigo-50 stroke-indigo-400 dark:fill-indigo-500/15'"
          />
          <text x="364" y="23" class="font-mono text-[11.5px] font-bold fill-slate-800 dark:fill-white">
            物理事务 TX2 (B 新建独立连接)
          </text>
          <text x="364" y="43" class="text-[10.5px] fill-slate-600 dark:fill-slate-300">
            B 退出即决定 TX2 {{ result.bOutcome }}（不标记 TX1）
          </text>
        </g>

        <!-- NESTED: TX1 + Savepoint sp1 -->
        <g v-else transform="translate(36, 180)">
          <rect
            x="0"
            y="0"
            width="688"
            height="56"
            rx="10"
            stroke-width="2"
            :class="animPhase >= 4 && result.aOutcome === 'COMMIT'
              ? 'fill-emerald-50 stroke-emerald-500 dark:fill-emerald-500/15'
              : animPhase >= 4
                ? 'fill-red-50 stroke-red-500 dark:fill-red-500/15'
                : 'fill-brand-50 stroke-brand-500 dark:fill-brand-500/15'"
          />
          <text x="16" y="23" class="font-mono text-[11.5px] font-bold fill-slate-800 dark:fill-white">
            物理事务 TX1 (全程仅 1 条连接)
          </text>
          <text x="16" y="43" class="text-[10.5px] fill-slate-600 dark:fill-slate-300">
            统一等待外层 A 最终提交或整体回滚
          </text>

          <rect
            x="338"
            y="8"
            width="336"
            height="40"
            rx="8"
            stroke-width="1.8"
            class="[stroke-dasharray:5_3]"
            :class="innerThrows
              ? 'fill-amber-50 stroke-amber-500 dark:fill-amber-500/20'
              : 'fill-indigo-50 stroke-indigo-500 dark:fill-indigo-500/20'"
          />
          <text x="506" y="25" text-anchor="middle" class="font-mono text-[11px] font-bold fill-slate-800 dark:fill-white">
            Savepoint (sp1) · B 嵌套保存点
          </text>
          <text x="506" y="41" text-anchor="middle" class="text-[10px] fill-slate-600 dark:fill-slate-300">
            {{ innerThrows ? 'ROLLBACK TO SAVEPOINT sp1 (不标 rollback-only)' : '随 TX1 最终一起 COMMIT' }}
          </text>
        </g>
      </svg>
    </div>

    <!-- UnexpectedRollbackException 警示横幅 & 最终判决 -->
    <div
      class="flex flex-wrap items-center justify-between gap-2 rounded-xl border px-3.5 py-2.5 text-xs"
      :class="result.unexpectedRollbackEx
        ? 'border-red-300 bg-red-50 text-red-800 dark:border-red-500/40 dark:bg-red-500/15 dark:text-red-300'
        : result.aOutcome === 'COMMIT' && result.bOutcome === 'COMMIT'
          ? 'border-emerald-200 bg-emerald-50 text-emerald-800 dark:border-emerald-500/30 dark:bg-emerald-500/10 dark:text-emerald-300'
          : 'border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-300'"
    >
      <div class="flex items-center gap-2 font-semibold">
        <AlertOctagon v-if="result.unexpectedRollbackEx" class="h-4 w-4 shrink-0 text-red-500" />
        <ShieldAlert v-else-if="result.aOutcome === 'ROLLBACK' || result.bOutcome === 'ROLLBACK'" class="h-4 w-4 shrink-0 text-amber-500" />
        <CheckCircle2 v-else class="h-4 w-4 shrink-0 text-emerald-500" />
        <span>最终结果：{{ result.headline }}</span>
      </div>

      <div class="flex items-center gap-2 font-mono text-[11px]">
        <span
          class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-bold"
          :class="result.aOutcome === 'COMMIT' ? 'bg-emerald-600 text-white' : 'bg-red-600 text-white'"
        >
          <Database class="h-3 w-3" /> A: {{ result.aOutcome }}
        </span>
        <span
          class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-bold"
          :class="result.bOutcome === 'COMMIT' ? 'bg-emerald-600 text-white' : 'bg-red-600 text-white'"
        >
          <XCircle v-if="result.bOutcome === 'ROLLBACK'" class="h-3 w-3" />
          <CheckCircle2 v-else class="h-3 w-3" />
          B: {{ result.bOutcome }}
        </span>
      </div>
    </div>

    <!-- 4 步执行时间线 + 面试原理解析 -->
    <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3.5 text-sm dark:border-slate-800 dark:bg-slate-900/80">
      <div class="mb-2.5 grid gap-1.5 sm:grid-cols-2">
        <button
          v-for="(txt, idx) in result.phaseSteps"
          :key="idx"
          class="rounded-lg border px-2.5 py-1.5 text-left text-xs transition"
          :class="animPhase >= idx + 1
            ? 'border-brand-200 bg-white text-slate-700 dark:border-brand-500/30 dark:bg-slate-800 dark:text-slate-200'
            : 'border-slate-200/60 bg-slate-100/50 text-slate-400 dark:border-slate-800 dark:bg-slate-900/40'"
          @click="stopTimer(); animPhase = idx + 1"
        >
          <span class="mr-1 font-mono font-bold text-brand-600 dark:text-blue-300">{{ idx + 1 }}.</span>
          {{ txt }}
        </button>
      </div>

      <p class="leading-relaxed text-slate-600 dark:text-slate-300">{{ result.explanation }}</p>
      <p class="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50/90 px-2.5 py-1.5 text-xs text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
        <Sparkles class="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" />
        <span>{{ result.tip }}</span>
      </p>
    </div>
  </div>
</template>
