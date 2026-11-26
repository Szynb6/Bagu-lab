"""
根据 docx_to_json.py 解析出的 JSON 数据，生成仅针对第 10 模块 (10-ai-app) 的幂等 Flyway 迁移脚本：
  backend/src/main/resources/db/migration/V6__add_ai_app_module.sql

特点：
1. 不改动任何 V1~V5 历史迁移文件，仅增量写入/更新 10-ai-app 模块、5 个篇目与 254 道双区题目；
2. 全部采用 ON CONFLICT DO UPDATE 幂等语句与 $bagu_q$ 美元引号转义，保留已有用户进度、收藏、笔记与 animation_key。

用法：python scripts/generate_v6_ai_module_migration.py [--data-dir <dir_with_data>]
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_SQL = ROOT / 'backend' / 'src' / 'main' / 'resources' / 'db' / 'migration' / 'V6__add_ai_app_module.sql'
TARGET_SLUG = '10-ai-app'


def pg_dollar_quote(text: str) -> str:
    tag = 'bagu_q'
    idx = 0
    while f'${tag}$' in text:
        idx += 1
        tag = f'bagu_q{idx}'
    return f'${tag}${text}${tag}$'


def build_sql(data_dir: Path) -> tuple[str, dict]:
    modules = json.loads((data_dir / 'modules.json').read_text(encoding='utf-8'))
    target = next((m for m in modules if m['slug'] == TARGET_SLUG), None)
    if not target:
        raise RuntimeError(f'未在 {data_dir / "modules.json"} 中找到模块 {TARGET_SLUG}')

    slug_q = pg_dollar_quote(target['slug'])
    lines = [
        '-- ============================================================================',
        '-- V6: 增量新增第 10 模块《AI 应用开发》（5 篇 · 254 道双区结构化笔记 · 57 张图解）',
        '-- 自动由 scripts/generate_v6_ai_module_migration.py 生成，全脚本幂等',
        '-- ============================================================================',
        '',
        f"-- ---------------- 模块 {target['order']:02d}: {target['title']} ----------------",
        'INSERT INTO kb_module (slug, title, full_title, icon, description, sprint_guide, sort_order, updated_at)',
        'VALUES (',
        f"    {slug_q}, {pg_dollar_quote(target['title'])}, {pg_dollar_quote(target['fullTitle'])},",
        f"    {pg_dollar_quote(target['icon'])}, {pg_dollar_quote(target.get('description', ''))},",
        f"    {pg_dollar_quote(target.get('sprintGuide', ''))}, {int(target['order'])}, NOW()",
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

    ch_cnt = 0
    for ch_idx, ch in enumerate(target['chapters'], 1):
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

    q_list = json.loads((data_dir / 'questions' / f'{TARGET_SLUG}.json').read_text(encoding='utf-8'))
    q_cnt = 0
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
            'FROM kb_module m',
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

    sql_text = '\n'.join(lines)
    stats = {'modules': 1, 'chapters': ch_cnt, 'questions': q_cnt, 'bytes': len(sql_text.encode('utf-8'))}
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
