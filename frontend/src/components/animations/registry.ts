import { defineAsyncComponent } from 'vue'
import type { AnimationEntry, DeclarativeSpec } from './types'

type SpecModule = { default: unknown }

function specEntry(
  key: string,
  title: string,
  subtitle: string,
  loader: () => Promise<SpecModule>,
): AnimationEntry {
  return {
    key,
    title,
    subtitle,
    loadSpec: async () => {
      const mod = await loader()
      const map = mod.default as Record<string, DeclarativeSpec | undefined>
      const spec = map[key]
      if (!spec) throw new Error(`Missing animation spec: ${key}`)
      return spec
    },
  }
}

const loadMod01 = () => import('./data/mod01Jvm')
const loadMod02 = () => import('./data/mod02Concurrency')
const loadMod03 = () => import('./data/mod03JavaBasics')
const loadMod04 = () => import('./data/mod04SpringMybatis')
const loadMod05 = () => import('./data/mod05Mysql')
const loadMod06 = () => import('./data/mod06Redis')
const loadMod07 = () => import('./data/mod07DesignPatterns')
const loadMod08 = () => import('./data/mod08Network')
const loadMod09 = () => import('./data/mod09Os')

const entries: AnimationEntry[] = [
  // ==================== 13 个专属交互沙盒组件 ====================
  {
    key: 'thread-pool-flow',
    title: 'ThreadPoolExecutor 任务提交全流程',
    subtitle: 'corePoolSize(2) → workQueue(2) → maximumPoolSize(4) → 拒绝策略',
    component: defineAsyncComponent(() => import('./ThreadPoolFlow.vue')),
  },
  {
    key: 'jvm-tricolor-gc',
    title: '并发标记三色算法与漏标修复',
    subtitle: '白/灰/黑三色推演 · 漏标复现 · CMS 增量更新 vs G1 SATB 写屏障',
    component: defineAsyncComponent(() => import('./sandboxes/JvmTriColorGc.vue')),
  },
  {
    key: 'jvm-generational-gc',
    title: '分代堆内存分配与晋升沙盒',
    subtitle: 'Eden / S0 / S1 / Old 动态分配 · 大对象直入 · 动态年龄判定与 Minor GC',
    component: defineAsyncComponent(() => import('./sandboxes/JvmGenerationalGc.vue')),
  },
  {
    key: 'juc-aqs-queue',
    title: 'AQS 同步状态与 CLH 等待队列',
    subtitle: 'CAS 抢占 state · 可重入计数 · 阻塞入队 park 与唤醒后继节点',
    component: defineAsyncComponent(() => import('./sandboxes/JucAqsQueue.vue')),
  },
  {
    key: 'col-hashmap-put',
    title: 'HashMap 1.8 put 寻址与链表树化沙盒',
    subtitle: '扰动哈希 · (n-1)&hash 定位桶 · 尾插链表与 ≥8 且容量≥64 树化',
    component: defineAsyncComponent(() => import('./sandboxes/ColHashMapSandbox.vue')),
  },
  {
    key: 'col-lru-cache',
    title: 'LinkedHashMap 手写 LRU 缓存沙盒',
    subtitle: 'HashMap O(1) 定位 + 双向链表头部最新 / 尾部满容淘汰演练',
    component: defineAsyncComponent(() => import('./sandboxes/ColLruSandbox.vue')),
  },
  {
    key: 'col-priority-queue',
    title: 'PriorityQueue 小顶堆数组与二叉树联动',
    subtitle: 'offer 上浮 (siftUp) 与 poll 下沉 (siftDown) · 揭示增强 for 无序原因',
    component: defineAsyncComponent(() => import('./sandboxes/ColPriorityQueueSandbox.vue')),
  },
  {
    key: 'spring-tx-propagation',
    title: 'Spring 事务传播行为沙盒',
    subtitle: 'REQUIRED / REQUIRES_NEW / NESTED 异常捕获与回滚隔离模拟',
    component: defineAsyncComponent(() => import('./sandboxes/SpringTxSandbox.vue')),
  },
  {
    key: 'mysql-bplustree-lookup',
    title: '聚簇索引、二级索引回表与覆盖索引',
    subtitle: '对比 SELECT * 二次回表查找与 SELECT id,name 覆盖索引直接返回',
    component: defineAsyncComponent(() => import('./sandboxes/MysqlBPlusLookup.vue')),
  },
  {
    key: 'mysql-innodb-locks',
    title: 'InnoDB Record / Gap / Next-Key 锁区间沙盒',
    subtitle: 'RR 隔离级别下唯一/非唯一索引加锁区间与 INSERT 阻塞实测',
    component: defineAsyncComponent(() => import('./sandboxes/MysqlLocksSandbox.vue')),
  },
  {
    key: 'redis-skiplist',
    title: 'Redis ZSet 跳表 (SkipList) 查找演示',
    subtitle: '多级索引自顶向下向右跳跃与下沉，对比单链表 O(N) 与跳表 O(logN)',
    component: defineAsyncComponent(() => import('./sandboxes/RedisSkipListSandbox.vue')),
  },
  {
    key: 'net-tcp-congestion',
    title: 'TCP 拥塞控制 cwnd 动态演化曲线',
    subtitle: '慢启动指数翻倍 → 拥塞避免线性增长 → 快重传/快恢复 vs 超时重置',
    component: defineAsyncComponent(() => import('./sandboxes/NetTcpCongestion.vue')),
  },
  {
    key: 'os-page-replacement',
    title: '页面置换算法对比沙盒 (FIFO / LRU / Clock)',
    subtitle: '固定 3 页框内存访问序列单步演练 · 实时统计命中与缺页率',
    component: defineAsyncComponent(() => import('./sandboxes/OsPageReplacement.vue')),
  },

  // ==================== 01 JVM (12 个声明式动画) ====================
  specEntry('jvm-stack-frame', '方法调用与虚拟机栈帧变化', '入栈 → 局部变量表/操作数栈计算 → 异常/返回出栈', loadMod01),
  specEntry('jvm-object-create', 'HotSpot 创建对象 5 大步骤', '类加载检查 → 分配内存 → 零值初始化 → 设置对象头 → <init>', loadMod01),
  specEntry('jvm-tlab-alloc', '指针碰撞、空闲列表与 TLAB 并发分配', 'Eden 规整/碎片内存分配 + CAS 冲突与 TLAB 线程私有缓冲', loadMod01),
  specEntry('jvm-object-access', '对象访问定位：句柄 vs 直接指针', '对比 GC 移动对象时句柄池间接寻址与 HotSpot 直接指针访问', loadMod01),
  specEntry('jvm-class-lifecycle', '类的生命周期与加载连接初始化', '加载 → 验证 → 准备(零值) → 解析 → 初始化(<clinit>) → 使用 → 卸载', loadMod01),
  specEntry('jvm-init-order', '父子类继承初始化与代码块执行顺序', '静态块/非静态块/构造方法逐行执行与控制台输出轨迹', loadMod01),
  specEntry('jvm-classloader-delegation', '双亲委派模型与 SPI 打破机制', '逐层向上委派 + JDBC 线程上下文类加载器 (TCCL) 逆向加载', loadMod01),
  specEntry('jvm-ref-counting', '引用计数法与循环引用内存泄漏', '引用计数值变化与 A↔B 孤岛计数恒为 1 的回收死结', loadMod01),
  specEntry('jvm-reachability', '可达性分析与 GC Roots 链路染色', '从虚拟机栈/静态属性/常量出发扫描标记，安全回收孤岛对象', loadMod01),
  specEntry('jvm-gc-algorithms', '三大经典垃圾收集算法原理对比', '标记-清除(内存碎片) vs 复制算法(无碎片折半) vs 标记-整理(压缩)', loadMod01),
  specEntry('jvm-card-table', '跨代引用、记忆集 RSet 与卡表 Card Table', '写屏障标记脏卡页 + Minor GC 仅扫描 Dirty Card 避免全扫老年代', loadMod01),
  specEntry('jvm-g1-region', 'G1 收集器 Region 布局与 Mixed GC', '等分 Region(E/S/O/H) + 停顿预测模型优先回收高价值 Region', loadMod01),

  // ==================== 02 Java 并发 (11 个声明式动画) ====================
  specEntry('juc-thread-lifecycle', 'Java 线程 6 种生命周期状态机', 'NEW / RUNNABLE / BLOCKED / WAITING / TIMED_WAITING / TERMINATED 交互跃迁', loadMod02),
  specEntry('juc-deadlock', '多线程/操作系统死锁形成与互斥环路', '互斥 / 占有且等待 / 不可剥夺 / 循环等待四必要条件演示', loadMod02),
  specEntry('juc-jmm', 'JMM 主内存与工作内存可见性模型', '线程本地缓存副本延迟刷新导致死循环 vs volatile 内存失效重读', loadMod02),
  specEntry('juc-dcl-reorder', 'DCL 单例指令重排序隐患与 volatile', '1分配→3赋引用→2初始化 重排拿到半初始化对象 vs volatile 禁用重排', loadMod02),
  specEntry('juc-longadder', 'LongAdder 分段热点消除优化原理', 'AtomicLong 单点 CAS 激烈自旋 vs LongAdder 分散 Cell[] 数组累加', loadMod02),
  specEntry('juc-cas-aba', 'CAS 乐观锁与 ABA 版本号陷阱', 'A→B→A 瞒天过海 vs AtomicStampedReference 带版本号拦截', loadMod02),
  specEntry('juc-synchronized-upgrade', 'synchronized Mark Word 锁升级全过程', '无锁(01) → 偏向锁(101) → 轻量级锁自旋(00) → Monitor 重量级锁(10)', loadMod02),
  specEntry('juc-threadlocal-leak', 'ThreadLocal 内部结构与内存泄漏链路', 'ThreadLocalMap 弱引用 Key 被 GC 后强引用 Value 滞留与 remove() 清理', loadMod02),
  specEntry('juc-cow-list', 'CopyOnWriteArrayList 写时复制机制', '读无锁访问旧数组 · 写加锁拷贝新数组并原子替换引用（弱一致性）', loadMod02),
  specEntry('juc-semaphore', 'Semaphore 信号量并发限流演练', '固定许可池 acquire() 获取与阻塞排队、release() 归还唤醒', loadMod02),
  specEntry('juc-latch-barrier', 'CountDownLatch vs CyclicBarrier 机制对比', '一次性计数器归零放行 vs 多线程栅栏汇合后自动重置循环使用', loadMod02),

  // ==================== 03 Java 基础与集合 (9 个声明式动画) ====================
  specEntry('java-copy-types', '引用拷贝、浅拷贝与深拷贝内存指向', '修改嵌套子对象字段时三种拷贝的独立性与互相污染对比', loadMod03),
  specEntry('java-pass-by-value', 'Java 只有值传递：对象引用传参真相', '对比方法内修改对象属性生效 vs 形参重新 new 对实参毫无影响', loadMod03),
  specEntry('java-dynamic-proxy', 'JDK 动态代理 vs CGLIB 字节码代理', 'InvocationHandler 接口代理 vs MethodInterceptor 子类继承增强', loadMod03),
  specEntry('java-io-models', 'BIO / NIO (Selector) / AIO 三代 I/O 模型', '阻塞一连接一线程 vs 单线程 Selector 事件轮询 vs 异步回调通知', loadMod03),
  specEntry('col-arraylist-grow', 'ArrayList 无参构造与 1.5 倍扩容迁移', '空数组{} → 首次 add 扩至 10 → 15 → 22 与 Arrays.copyOf 迁移', loadMod03),
  specEntry('col-fail-fast', '集合 Fail-Fast 快速失败 vs Fail-Safe', '遍历期间 modCount != expectedModCount 抛 CME vs COW 快照安全遍历', loadMod03),
  specEntry('col-hashmap-resize', 'HashMap 1.8 两倍扩容高低位拆分迁移', '容量翻倍免重算 hash · 按 (e.hash & oldCap) == 0 拆留原位或 +oldCap', loadMod03),
  specEntry('col-hashmap-unsafe', 'HashMap 线程不安全：1.7 成环与 1.8 覆盖', 'JDK 1.7 头插并发扩容成环死循环 vs JDK 1.8 尾插并发写入数据覆盖', loadMod03),
  specEntry('col-concurrenthashmap', 'ConcurrentHashMap 1.7 vs 1.8 并发度演进', 'Segment 分段锁粒度 vs CAS + synchronized 锁单个桶头节点', loadMod03),

  // ==================== 04 Spring 与 MyBatis (8 个声明式动画) ====================
  specEntry('spring-bean-lifecycle', 'Spring Bean 从创建到销毁完整生命周期', '实例化 → 注入 → Aware → BPP 前置 → 初始化 → BPP 后置(AOP) → 使用 → 销毁', loadMod04),
  specEntry('spring-circular-dep', 'Spring 三级缓存解决 Setter 循环依赖', '一级成品池 / 二级半成品池 / 三级工厂 ObjectFactory 提前暴露代理', loadMod04),
  specEntry('spring-aop-advice', 'Spring AOP 五种通知执行顺序洋葱模型', 'Around → Before → 目标方法 → AfterReturning / AfterThrowing → After', loadMod04),
  specEntry('spring-mvc-flow', 'Spring MVC DispatcherServlet 请求分发全链路', 'HandlerMapping → HandlerAdapter → Controller → ViewResolver', loadMod04),
  specEntry('spring-tx-invalid', '@Transactional 事务生效与自调用失效对比', '经 AOP 代理触发事务 vs 同类内部 this.b() 绕过代理导致事务彻底失效', loadMod04),
  specEntry('spring-boot-autoconfig', 'Spring Boot 自动配置 SPI 与条件筛选漏斗', '读取 AutoConfiguration.imports → @ConditionalOnClass 等过滤 → 注入容器', loadMod04),
  specEntry('mybatis-cache', 'MyBatis 一级缓存作用域与二级缓存脏读', 'SqlSession 隔离与跨 Session 关联表更新导致的二级缓存脏数据演示', loadMod04),
  specEntry('mybatis-sql-flow', 'MyBatis Mapper 代理与 SQL 执行四大组件', 'MapperProxy → SqlSession → Executor → Statement/Parameter/ResultSetHandler', loadMod04),

  // ==================== 05 MySQL (8 个声明式动画) ====================
  specEntry('mysql-sql-exec', '一条 SQL 在 MySQL Server 与 InnoDB 的执行旅程', '连接器 → 分析器 → 优化器 → 执行器 → 存储引擎及日志落盘路径', loadMod05),
  specEntry('mysql-buffer-pool', 'InnoDB Buffer Pool 与 Change Buffer 协同机制', '数据页 LRU 缓存命中 · 非唯一二级索引更新先缓存后 Merge 刷盘', loadMod05),
  specEntry('mysql-mvcc', 'MVCC Undo 版本链与 ReadView 可见性判定', '沿 roll_pointer 回溯对比 m_ids / min / max，演示 RC 与 RR 隔离差异', loadMod05),
  specEntry('mysql-leftmost-prefix', '联合索引 (a, b, c) 最左前缀匹配与截断', '全命中 vs 缺左列全表扫描 vs 范围查询导致右侧索引列截断对比', loadMod05),
  specEntry('mysql-two-phase-commit', 'Undo/Redo 与 Binlog 两阶段提交 (2PC)', 'Redo Prepare → 写 Binlog → Redo Commit 与崩溃恢复一致性校验', loadMod05),
  specEntry('mysql-replication', 'MySQL 主从异步复制三线程协同原理', 'Master Binlog → Dump 线程 → 从库 I/O 线程 → Relay Log → SQL 线程', loadMod05),
  specEntry('mysql-deep-pagination', '深分页 LIMIT 1000000,10 与延迟关联优化', '全量回表百万次丢弃 vs 覆盖索引先捞 10 个主键再精确回表 10 次', loadMod05),
  specEntry('mysql-icp', '索引下推 (ICP) 减少回表次数原理', '对比 Server 层后置过滤回表 vs InnoDB 引擎层联合索引提前过滤', loadMod05),

  // ==================== 06 Redis (9 个声明式动画) ====================
  specEntry('redis-thread-model', 'Redis 单线程核心执行与 6.0+ 多线程网络 I/O', '多 I/O 线程并行读写 Socket · 核心内存命令单线程串行无锁执行', loadMod06),
  specEntry('redis-cache-problems', '缓存穿透、击穿、雪崩与三层防线对比', '布隆过滤器/空值拦截 · 互斥锁/逻辑过期防击穿 · 随机 TTL 防雪崩', loadMod06),
  specEntry('redis-double-write', 'Redis + MySQL 双写一致性并发竞态与方案', '先删缓存翻车时序 vs 先更 DB 后删 / 延迟双删 / Canal 异步订阅', loadMod06),
  specEntry('redis-expire-delete', 'Redis 过期 Key 惰性删除 + 定期抽样策略', '读前检查 expires 字典清理 + 周期随机抽 20 Key 超 25% 继续循环', loadMod06),
  specEntry('redis-persistence', 'RDB (fork + COW) 与 AOF 混合持久化机制', '写时复制隔离内存快照 · AOF Rewrite 瘦身与 RDB+AOF 混合文件结构', loadMod06),
  specEntry('redis-distributed-lock', 'Redis 分布式锁超时翻车与 Redisson 看门狗', 'SET NX EX 误删他人锁演示 · WatchDog 自动续期与 Lua 原子判断解锁', loadMod06),
  specEntry('redis-replication', 'Redis 主从全量同步与断线重连增量同步', 'PSYNC 初次全量 RDB+Buffer 同步 vs 依据 offset 从 repl_backlog 续传', loadMod06),
  specEntry('redis-sentinel-failover', 'Redis Sentinel 哨兵故障自动转移全流程', 'SDOWN 主观下线 → Raft 投票 ODOWN → 选举新 Master → 广播切主', loadMod06),
  specEntry('redis-cluster-slots', 'Redis Cluster 16384 哈希槽与重定向路由', 'CRC16(key)%16384 映射槽位 · 打错节点 MOVED 永久转向 vs 迁移中 ASK', loadMod06),

  // ==================== 07 设计模式 (6 个声明式动画) ====================
  specEntry('dp-decorator', '装饰器模式：层层包裹动态叠加增强职责', '在不修改原对象的前提下依次包裹压缩 / 加密 / 缓冲装饰器', loadMod07),
  specEntry('dp-proxy', '代理模式：Proxy 前置鉴权与限流控制', 'Client 通过 Proxy 间接访问 RealSubject 实现访问控制与增强', loadMod07),
  specEntry('dp-template-method', '模板方法模式：父类固定骨架与子类定制步骤', 'AbstractClass 锁定执行流程与 Hook 钩子，子类仅重写可变细节', loadMod07),
  specEntry('dp-observer', '观察者模式：一对多状态变更事件广播', 'Subject 事件发布后解耦遍历通知所有已注册 Observer 并支持动态退订', loadMod07),
  specEntry('dp-chain', '责任链模式：请求流水线校验与中途短路', '限流 → 鉴权 → 敏感词节点链式传递，支持任意节点拒绝短路返回', loadMod07),
  specEntry('dp-strategy-state', '状态模式 (交互状态机) vs 策略模式对比', '订单状态由内部动作自动驱动跃迁 vs 客户端主动外部切换不同策略算法', loadMod07),

  // ==================== 08 计算机网络 (8 个声明式动画) ====================
  specEntry('net-osi-encap', 'TCP/IP 与 OSI 分层数据封装与逐层解包', '应用报文向下依次附加 TCP/IP/MAC 首部成帧发送与对端逐层拆封', loadMod08),
  specEntry('net-url-journey', '浏览器输入 URL 到页面渲染完整链路', 'DNS 解析 → TCP 握手 → TLS 协商 → HTTP 网关与后端 → 浏览器渲染', loadMod08),
  specEntry('net-dns-lookup', 'DNS 递归查询与多级迭代解析流程', '本地 DNS 替客户端依次向根 / 顶级(.com) / 权威域名服务器迭代寻址', loadMod08),
  specEntry('net-tls-handshake', 'HTTPS TLS 安全握手与混合加密建立过程', 'ClientHello → CA 证书验签 → ECDHE 密钥交换 → 切换高效对称加密', loadMod08),
  specEntry('net-http-evolution', 'HTTP/1.1 → HTTP/2 → HTTP/3 队头阻塞演进', '管线阻塞单信道 → H2 二进制流多路复用 → H3 QUIC over UDP 彻底独立流', loadMod08),
  specEntry('net-tcp-handshake', 'TCP 三次握手与禁用两次握手的原因', 'SYN → SYN+ACK → ACK 状态跃迁 + 演示滞留历史 SYN 两次握手幽灵连接', loadMod08),
  specEntry('net-tcp-wave', 'TCP 四次挥手与 TIME_WAIT 2MSL 的意义', '双工独立关闭链路 + 演示末次 ACK 丢失触发对端重传 FIN 的保护', loadMod08),
  specEntry('net-arp', 'ARP 局域网广播问询与 MAC 地址缓存', '泛洪广播询问目标 IP 对应 MAC → 单播回传 → 写入本地 ARP 映射缓存', loadMod08),

  // ==================== 09 操作系统 (7 个声明式动画) ====================
  specEntry('os-user-kernel', '用户态 (Ring 3) 与内核态 (Ring 0) 切换轨迹', 'read() 系统调用软中断陷入内核 → 保存与恢复线程寄存器上下文', loadMod09),
  specEntry('os-process-state', '操作系统进程 5 状态转换交互状态机', '创建 / 就绪 / 运行 / 阻塞 / 终止状态转换（支持自由点击事件探索）', loadMod09),
  specEntry('os-cpu-scheduling', '进程调度算法对比：FCFS vs SJF vs 时间片轮转', '同一组任务在三种调度器下的执行甘特图与平均等待时间对比', loadMod09),
  specEntry('os-virtual-memory', '虚拟地址翻译、TLB 快表与缺页中断处理', '虚拟页号+偏移 → 查 TLB 快表 → 查多级页表 → 物理页框或磁盘调入', loadMod09),
  specEntry('os-epoll', 'I/O 多路复用 select/poll vs epoll 原理', '全量 fd 拷贝 O(N) 轮询 vs 红黑树注册 + 事件回调入 rdlist O(1) 返回', loadMod09),
  specEntry('os-zero-copy', 'Linux 零拷贝演进：传统 vs mmap vs sendfile', '4 次拷贝 4 次切换 → mmap 3 次拷贝 → sendfile+SG-DMA 2 次拷贝 2 次切换', loadMod09),
  specEntry('os-page-cache', 'write() 写入 Page Cache 与 fsync() 落盘对比', '异步写脏页立即返回可能断电丢失 vs fsync() 强制同步刷新磁盘块', loadMod09),
]

const registryMap: Record<string, AnimationEntry> = Object.fromEntries(entries.map((e) => [e.key, e]))

export const ANIMATION_KEYS = entries.map((e) => e.key)

export function getAnimationEntry(key?: string | null): AnimationEntry | null {
  if (!key) return null
  return registryMap[key] ?? null
}
