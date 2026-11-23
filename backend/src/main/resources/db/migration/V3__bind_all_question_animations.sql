-- 为全部 9 大模块筛选出的 112 道高频/原理题批量绑定交互式动画标识（涵盖 91 个独立动画，含复用）
-- 注意：本脚本幂等更新 animation_key，绝不触碰历史表结构 V1/V2

UPDATE kb_question q
SET animation_key = v.anim_key,
    updated_at    = NOW()
FROM kb_module m
JOIN (
    VALUES
        -- 01-jvm (18 题)
        ('01-jvm', 'Q03', 'jvm-stack-frame'),
        ('01-jvm', 'Q09', 'jvm-object-create'),
        ('01-jvm', 'Q10', 'jvm-tlab-alloc'),
        ('01-jvm', 'Q12', 'jvm-object-access'),
        ('01-jvm', 'Q15', 'jvm-class-lifecycle'),
        ('01-jvm', 'Q16', 'jvm-class-lifecycle'),
        ('01-jvm', 'Q20', 'jvm-init-order'),
        ('01-jvm', 'Q22', 'jvm-classloader-delegation'),
        ('01-jvm', 'Q23', 'jvm-classloader-delegation'),
        ('01-jvm', 'Q24', 'jvm-classloader-delegation'),
        ('01-jvm', 'Q27', 'jvm-generational-gc'),
        ('01-jvm', 'Q29', 'jvm-generational-gc'),
        ('01-jvm', 'Q31', 'jvm-ref-counting'),
        ('01-jvm', 'Q32', 'jvm-reachability'),
        ('01-jvm', 'Q35', 'jvm-gc-algorithms'),
        ('01-jvm', 'Q39', 'jvm-card-table'),
        ('01-jvm', 'Q42', 'jvm-tricolor-gc'),
        ('01-jvm', 'Q43', 'jvm-g1-region'),

        -- 02-concurrency (17 题)
        ('02-concurrency', 'Q04', 'juc-thread-lifecycle'),
        ('02-concurrency', 'Q11', 'juc-deadlock'),
        ('02-concurrency', 'Q13', 'juc-jmm'),
        ('02-concurrency', 'Q16', 'juc-dcl-reorder'),
        ('02-concurrency', 'Q19', 'juc-longadder'),
        ('02-concurrency', 'Q20', 'juc-cas-aba'),
        ('02-concurrency', 'Q21', 'juc-synchronized-upgrade'),
        ('02-concurrency', 'Q26', 'juc-threadlocal-leak'),
        ('02-concurrency', 'Q27', 'juc-threadlocal-leak'),
        ('02-concurrency', 'Q29', 'juc-cow-list'),
        ('02-concurrency', 'Q33', 'thread-pool-flow'),
        ('02-concurrency', 'Q34', 'thread-pool-flow'),
        ('02-concurrency', 'Q35', 'thread-pool-flow'),
        ('02-concurrency', 'Q45', 'juc-aqs-queue'),
        ('02-concurrency', 'Q46', 'juc-semaphore'),
        ('02-concurrency', 'Q47', 'juc-latch-barrier'),
        ('02-concurrency', 'Q48', 'juc-latch-barrier'),

        -- 03-java-basics (15 题)
        ('03-java-basics', 'Q22', 'java-copy-types'),
        ('03-java-basics', 'Q26', 'java-pass-by-value'),
        ('03-java-basics', 'Q49', 'java-dynamic-proxy'),
        ('03-java-basics', 'Q56', 'java-io-models'),
        ('03-java-basics', 'Q65', 'col-arraylist-grow'),
        ('03-java-basics', 'Q67', 'col-fail-fast'),
        ('03-java-basics', 'Q72', 'col-priority-queue'),
        ('03-java-basics', 'Q74', 'col-priority-queue'),
        ('03-java-basics', 'Q80', 'col-hashmap-put'),
        ('03-java-basics', 'Q81', 'col-hashmap-resize'),
        ('03-java-basics', 'Q83', 'col-hashmap-resize'),
        ('03-java-basics', 'Q84', 'col-hashmap-put'),
        ('03-java-basics', 'Q87', 'col-hashmap-unsafe'),
        ('03-java-basics', 'Q89', 'col-lru-cache'),
        ('03-java-basics', 'Q90', 'col-concurrenthashmap'),

        -- 04-spring-mybatis (10 题)
        ('04-spring-mybatis', 'Q13', 'spring-bean-lifecycle'),
        ('04-spring-mybatis', 'Q14', 'spring-circular-dep'),
        ('04-spring-mybatis', 'Q17', 'spring-aop-advice'),
        ('04-spring-mybatis', 'Q19', 'spring-mvc-flow'),
        ('04-spring-mybatis', 'Q22', 'spring-tx-propagation'),
        ('04-spring-mybatis', 'Q25', 'spring-tx-invalid'),
        ('04-spring-mybatis', 'Q29', 'spring-boot-autoconfig'),
        ('04-spring-mybatis', 'Q35', 'mybatis-cache'),
        ('04-spring-mybatis', 'Q37', 'mybatis-sql-flow'),
        ('04-spring-mybatis', 'Q38', 'mybatis-sql-flow'),

        -- 05-mysql (13 题)
        ('05-mysql', 'Q11', 'mysql-sql-exec'),
        ('05-mysql', 'Q14', 'mysql-buffer-pool'),
        ('05-mysql', 'Q20', 'mysql-bplustree-lookup'),
        ('05-mysql', 'Q25', 'mysql-mvcc'),
        ('05-mysql', 'Q28', 'mysql-innodb-locks'),
        ('05-mysql', 'Q31', 'mysql-bplustree-lookup'),
        ('05-mysql', 'Q32', 'mysql-leftmost-prefix'),
        ('05-mysql', 'Q38', 'mysql-two-phase-commit'),
        ('05-mysql', 'Q39', 'mysql-two-phase-commit'),
        ('05-mysql', 'Q41', 'mysql-two-phase-commit'),
        ('05-mysql', 'Q42', 'mysql-replication'),
        ('05-mysql', 'Q47', 'mysql-deep-pagination'),
        ('05-mysql', 'Q48', 'mysql-icp'),

        -- 06-redis (12 题)
        ('06-redis', 'Q02', 'redis-thread-model'),
        ('06-redis', 'Q03', 'redis-cache-problems'),
        ('06-redis', 'Q04', 'redis-cache-problems'),
        ('06-redis', 'Q05', 'redis-cache-problems'),
        ('06-redis', 'Q07', 'redis-double-write'),
        ('06-redis', 'Q09', 'redis-expire-delete'),
        ('06-redis', 'Q12', 'redis-skiplist'),
        ('06-redis', 'Q13', 'redis-persistence'),
        ('06-redis', 'Q15', 'redis-distributed-lock'),
        ('06-redis', 'Q19', 'redis-replication'),
        ('06-redis', 'Q20', 'redis-sentinel-failover'),
        ('06-redis', 'Q21', 'redis-cluster-slots'),

        -- 07-design-patterns (7 题)
        ('07-design-patterns', 'DP05', 'dp-decorator'),
        ('07-design-patterns', 'DP06', 'dp-proxy'),
        ('07-design-patterns', 'DP07', 'dp-template-method'),
        ('07-design-patterns', 'DP08', 'dp-observer'),
        ('07-design-patterns', 'DP09', 'dp-chain'),
        ('07-design-patterns', 'DP10', 'dp-strategy-state'),
        ('07-design-patterns', 'DP11', 'dp-strategy-state'),

        -- 08-network (10 题)
        ('08-network', 'Q01', 'net-osi-encap'),
        ('08-network', 'Q02', 'net-url-journey'),
        ('08-network', 'Q03', 'net-dns-lookup'),
        ('08-network', 'Q07', 'net-tls-handshake'),
        ('08-network', 'Q08', 'net-http-evolution'),
        ('08-network', 'Q15', 'net-tcp-handshake'),
        ('08-network', 'Q16', 'net-tcp-wave'),
        ('08-network', 'Q17', 'net-tcp-wave'),
        ('08-network', 'Q19', 'net-tcp-congestion'),
        ('08-network', 'Q22', 'net-arp'),

        -- 09-os (10 题，含 Q10 复用死锁动画)
        ('09-os', 'Q01', 'os-user-kernel'),
        ('09-os', 'Q03', 'os-process-state'),
        ('09-os', 'Q06', 'os-cpu-scheduling'),
        ('09-os', 'Q10', 'juc-deadlock'),
        ('09-os', 'Q12', 'os-virtual-memory'),
        ('09-os', 'Q13', 'os-virtual-memory'),
        ('09-os', 'Q14', 'os-page-replacement'),
        ('09-os', 'Q19', 'os-epoll'),
        ('09-os', 'Q20', 'os-zero-copy'),
        ('09-os', 'Q21', 'os-page-cache')
) AS v(slug, code, anim_key)
    ON m.slug = v.slug
WHERE q.module_id = m.id
  AND q.code = v.code;
