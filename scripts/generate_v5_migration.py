"""
根据 docx_to_json.py 解析出的 JSON 数据，生成幂等的 Flyway 迁移脚本：
  backend/src/main/resources/db/migration/V5__refresh_dual_zone_knowledge_data.sql

特点：
1. 原地将 07-design-patterns 的旧题号 (DP01~DP11 / CMP01~CMP04) 重命名为 Q01~Q15，
   保持 kb_question.id 主键不变，100% 保留用户学习进度、收藏、笔记与 7 组动画绑定；
2. 幂等 Upsert 全部 9 大模块、39 个篇目与 375 道双区题目（不覆盖已有 animation_key）；
3. 兜底补绑 07-design-patterns 7 组交互动画（兼容空库从零迁移场景）。

用法：python scripts/generate_v5_migration.py [--data-dir <dir_with_data>]
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_SQL = ROOT / 'backend' / 'src' / 'main' / 'resources' / 'db' / 'migration' / 'V5__refresh_dual_zone_knowledge_data.sql'


def pg_dollar_quote(text: str) -> str:
    tag = 'bagu_q'
    idx = 0
    while f'${tag}$' in text:
        idx += 1
        tag = f'bagu_q{idx}'
    return f'${tag}${text}${tag}$'


def build_sql(data_dir: Path) -> tuple[str, dict]:
    modules = json.loads((data_dir / 'modules.json').read_text(encoding='utf-8'))
    lines = [
        '-- ============================================================================',
        '-- V5: 全量同步 9 大模块 375 道双区结构化笔记（先【面试标准回答】→ 后【原理详细讲解】）',
        '-- 自动由 scripts/generate_v5_migration.py 生成，全脚本幂等且保留用户进度与动画绑定',
        '-- ============================================================================',
        '',
        '-- 1. 原地重命名 07-design-patterns 旧题号（保持 kb_question.id 不变，保留进度与动画绑定）',
        'UPDATE kb_question q',
        'SET code = v.new_code,',
        '    updated_at = NOW()',
        'FROM kb_module m',
        'JOIN (VALUES',
        "    ('DP01', 'Q01'), ('DP02', 'Q02'), ('DP03', 'Q03'), ('DP04', 'Q04'), ('DP05', 'Q05'),",
        "    ('DP06', 'Q06'), ('DP07', 'Q07'), ('DP08', 'Q08'), ('DP09', 'Q09'), ('DP10', 'Q10'),",
        "    ('DP11', 'Q11'), ('CMP01', 'Q12'), ('CMP02', 'Q13'), ('CMP03', 'Q14'), ('CMP04', 'Q15')",
        ') AS v(old_code, new_code) ON TRUE',
        'WHERE q.module_id = m.id',
        "  AND m.slug = '07-design-patterns'",
        '  AND q.code = v.old_code',
        '  AND NOT EXISTS (',
        '      SELECT 1 FROM kb_question q2',
        '      WHERE q2.module_id = m.id AND q2.code = v.new_code',
        '  );',
        '',
    ]

    mod_cnt = ch_cnt = q_cnt = 0
    for m in modules:
        mod_cnt += 1
        slug_q = pg_dollar_quote(m['slug'])
        lines += [
            f"-- ---------------- 模块 {m['order']:02d}: {m['title']} ----------------",
            'INSERT INTO kb_module (slug, title, full_title, icon, description, sprint_guide, sort_order, updated_at)',
            'VALUES (',
            f"    {slug_q}, {pg_dollar_quote(m['title'])}, {pg_dollar_quote(m['fullTitle'])},",
            f"    {pg_dollar_quote(m['icon'])}, {pg_dollar_quote(m.get('description', ''))},",
            f"    {pg_dollar_quote(m.get('sprintGuide', ''))}, {int(m['order'])}, NOW()",
            ')',
            'ON CONFLICT (slug) DO UPDATE SET',
            '    title = EXCLUDED.title,',
            '    full_title = EXCLUDED.full_title,',
            '    icon = EXCLUDED.icon,',
            '    description = EXCLUDED.description,',
            '    sprint_guide = EXCLUDED.sprint_guide,',
            '    sort_order = EXCLUDED.sort_order,',
            '    updated_at = NOW();',
            '',
        ]

        for ch_idx, ch in enumerate(m['chapters'], 1):
            ch_cnt += 1
            lines += [
                'INSERT INTO kb_chapter (module_id, code, title, sort_order)',
                f"SELECT id, {pg_dollar_quote(ch['id'])}, {pg_dollar_quote(ch['title'])}, {ch_idx}",
                f'FROM kb_module WHERE slug = {slug_q}',
                'ON CONFLICT (module_id, code) DO UPDATE SET',
                '    title = EXCLUDED.title,',
                '    sort_order = EXCLUDED.sort_order;',
            ]
        lines.append('')

        q_list = json.loads((data_dir / 'questions' / f"{m['slug']}.json").read_text(encoding='utf-8'))
        for q in q_list:
            q_cnt += 1
            hints_str = json.dumps(q.get('hints', []), ensure_ascii=False)
            images_str = json.dumps(q.get('images', []), ensure_ascii=False)
            has_table = 'TRUE' if q.get('hasTable') else 'FALSE'
            has_code = 'TRUE' if q.get('hasCode') else 'FALSE'
            lines += [
                'INSERT INTO kb_question (',
                '    module_id, chapter_id, code, priority, title,',
                '    hints_json, answer_md, images_json, has_table, has_code,',
                '    sort_order, is_deleted, updated_at',
                ')',
                'SELECT',
                '    m.id, c.id,',
                f"    {pg_dollar_quote(q['code'])}, {pg_dollar_quote(q['priority'])}, {pg_dollar_quote(q['title'])},",
                f"    {pg_dollar_quote(hints_str)}::jsonb, {pg_dollar_quote(q['answerMd'])},",
                f"    {pg_dollar_quote(images_str)}::jsonb, {has_table}, {has_code},",
                f"    {int(q['order'])}, FALSE, NOW()",
                f'FROM kb_module m',
                f"LEFT JOIN kb_chapter c ON c.module_id = m.id AND c.code = {pg_dollar_quote(q['chapterId'])}",
                f'WHERE m.slug = {slug_q}',
                'ON CONFLICT (module_id, code) DO UPDATE SET',
                '    chapter_id = EXCLUDED.chapter_id,',
                '    priority = EXCLUDED.priority,',
                '    title = EXCLUDED.title,',
                '    hints_json = EXCLUDED.hints_json,',
                '    answer_md = EXCLUDED.answer_md,',
                '    images_json = EXCLUDED.images_json,',
                '    has_table = EXCLUDED.has_table,',
                '    has_code = EXCLUDED.has_code,',
                '    sort_order = EXCLUDED.sort_order,',
                '    is_deleted = FALSE,',
                '    updated_at = NOW();',
            ]
        lines.append('')

    lines += [
        '-- 3. 兜底补绑 07-design-patterns 7 组交互动画（若已在第一步由 DPxx 原地改名保留则无冲突）',
        'UPDATE kb_question q',
        'SET animation_key = v.anim_key,',
        '    updated_at = NOW()',
        'FROM kb_module m',
        'JOIN (VALUES',
        "    ('Q05', 'dp-decorator'),",
        "    ('Q06', 'dp-proxy'),",
        "    ('Q07', 'dp-template-method'),",
        "    ('Q08', 'dp-observer'),",
        "    ('Q09', 'dp-chain'),",
        "    ('Q10', 'dp-strategy-state'),",
        "    ('Q11', 'dp-strategy-state')",
        ') AS v(code, anim_key) ON TRUE',
        'WHERE q.module_id = m.id',
        "  AND m.slug = '07-design-patterns'",
        '  AND q.code = v.code',
        "  AND (q.animation_key IS NULL OR q.animation_key = '');",
        '',
    ]

    sql_text = '\n'.join(lines)
    stats = {'modules': mod_cnt, 'chapters': ch_cnt, 'questions': q_cnt, 'bytes': len(sql_text.encode('utf-8'))}
    return sql_text, stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--data-dir', default=str(ROOT / 'frontend' / 'public' / 'data'))
    args = ap.parse_args()

    sql_text, stats = build_sql(Path(args.data_dir))
    OUT_SQL.write_text(sql_text, encoding='utf-8')
    print(f"[GENERATED] {OUT_SQL} ({stats['bytes']:,} bytes)")
    print(f"  modules={stats['modules']}, chapters={stats['chapters']}, questions={stats['questions']}")


if __name__ == '__main__':
    main()
