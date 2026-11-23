-- 为题目表新增交互式原理动画标识，并为首个 P0 核心题绑定线程池提交流程动画
ALTER TABLE kb_question
    ADD COLUMN IF NOT EXISTS animation_key VARCHAR(64);

COMMENT ON COLUMN kb_question.animation_key IS '前端交互式原理动画组件标识（如 thread-pool-flow）';

UPDATE kb_question q
SET animation_key = 'thread-pool-flow',
    updated_at    = NOW()
FROM kb_module m
WHERE q.module_id = m.id
  AND m.slug = '02-concurrency'
  AND q.code = 'Q34';
