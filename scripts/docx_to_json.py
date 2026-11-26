"""
将 d:\\八股 下的 9 份双区 Word 面试笔记解析为结构化 JSON 题库（供生成 Flyway 迁移 SQL 或导入脚本使用）。

输出（默认输出到 frontend/public，也可通过 --out-dir 指定临时目录）：
  {out_dir}/data/modules.json              9 大模块元信息 + 篇目
  {out_dir}/data/questions/{slug}.json     单模块题目（含 :::interview / :::pitfall / :::followup / :::explain-divider）
  {out_dir}/images/{slug}/*.png            题目引用的原理图
  scripts/parse_report.json                解析统计报告

用法：python scripts/docx_to_json.py [--src d:\八股] [--out-dir <dir>] [--no-report]
"""
import argparse
import json
import re
import shutil
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
A = '{http://schemas.openxmlformats.org/drawingml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
PR = '{http://schemas.openxmlformats.org/package/2006/relationships}'

ROOT = Path(__file__).resolve().parent.parent

MODULES = [
    ('01', '01-jvm', 'JVM', 'cpu'),
    ('02', '02-concurrency', 'Java 并发', 'git-branch'),
    ('03', '03-java-basics', 'Java 基础与集合', 'coffee'),
    ('04', '04-spring-mybatis', 'Spring 与 MyBatis', 'leaf'),
    ('05', '05-mysql', 'MySQL', 'database'),
    ('06', '06-redis', 'Redis', 'zap'),
    ('07', '07-design-patterns', '设计模式', 'shapes'),
    ('08', '08-network', '计算机网络', 'globe'),
    ('09', '09-os', '操作系统', 'monitor'),
    ('10', '10-ai-app', 'AI 应用开发', 'sparkles'),
]

Q_RE = re.compile(r'^Q(\d+)\s*【\s*(P[012])[^】]*】\s*(.+)$')
CH_RE = re.compile(r'^第[一二三四五六七八九十]+(?:篇|部分)\s*[：:]\s*(.+)$')
CODE_FONTS = ('Consolas', 'Courier New', 'JetBrains Mono', 'Menlo', 'Monaco')


def ptext(p):
    return ''.join(t.text or '' for t in p.iter(W + 't')).strip()


def pstyle(p):
    st = p.find(f'{W}pPr/{W}pStyle')
    return st.get(W + 'val') if st is not None else None


def run_is_code(r):
    rf = r.find(f'{W}rPr/{W}rFonts')
    if rf is None:
        return False
    return (rf.get(W + 'ascii') or '') in CODE_FONTS or (rf.get(W + 'hAnsi') or '') in CODE_FONTS


def run_is_bold(r):
    b = r.find(f'{W}rPr/{W}b')
    return b is not None and b.get(W + 'val') not in ('0', 'false')


def is_code_para(p):
    runs = [r for r in p.iter(W + 'r') if ''.join(t.text or '' for t in r.iter(W + 't'))]
    return bool(runs) and all(run_is_code(r) for r in runs)


def code_text(p):
    parts = []
    for r in p.iter(W + 'r'):
        for ch in r:
            if ch.tag == W + 't':
                parts.append(ch.text or '')
            elif ch.tag == W + 'tab':
                parts.append('    ')
            elif ch.tag == W + 'br':
                parts.append('\n')
    return ''.join(parts)


def inline_md(p):
    """段落转行内 Markdown（加粗 / 行内代码）。"""
    out = []
    for r in p.iter(W + 'r'):
        txt = ''.join(t.text or '' for t in r.iter(W + 't'))
        if not txt:
            continue
        if run_is_code(r) and txt.strip():
            out.append(('code', txt))
        elif run_is_bold(r) and txt.strip():
            out.append(('bold', txt))
        else:
            out.append(('text', txt))
    # 合并相邻同类片段
    merged = []
    for kind, txt in out:
        if merged and merged[-1][0] == kind:
            merged[-1] = (kind, merged[-1][1] + txt)
        else:
            merged.append((kind, txt))
    res = []
    for kind, txt in merged:
        if kind == 'code':
            res.append(f'`{txt.strip()}`')
        elif kind == 'bold':
            s = txt.strip()
            lead = txt[: len(txt) - len(txt.lstrip())]
            trail = txt[len(txt.rstrip()):]
            res.append(f'{lead}<strong>{s}</strong>{trail}')
        else:
            res.append(txt)
    return ''.join(res).strip()


def guess_lang(code):
    s = code.lstrip()
    if s.startswith('<'):
        return 'xml'
    if 'redis.call' in code or re.search(r'\bif\b.*\bthen\b.*\bend\b', code, re.S):
        return 'lua'
    head = code.strip()[:250].upper()
    if re.search(r'\b(SELECT|INSERT\s+INTO|UPDATE\s+\w+\s+SET|CREATE\s+TABLE|ALTER\s+TABLE|EXPLAIN|SHOW\s+)\b', head):
        return 'sql'
    if re.search(r'^(SET|GET|HSET|ZADD|EXPIRE|SETNX|LPUSH|DEL|XADD|PFADD)\s', head, re.M):
        return 'bash'
    if s.startswith(('-X', 'java -', 'jstack', 'jmap', 'jstat', 'jcmd', 'top', 'vmstat', 'iostat', '$', '#!/', '# ')) and '{' not in code:
        return 'bash'
    if re.match(r'^[a-z0-9_.-]+:\s*($|\n|[a-zA-Z0-9_-])', s) and ';' not in code and '{' not in code:
        return 'yaml'
    return 'java'


def cell_text(tc):
    lines = [inline_md(p) for p in tc.iter(W + 'p')]
    s = '<br>'.join(l for l in lines if l)
    return s.replace('|', '\\|')


def cell_fill(tc):
    shd = tc.find(f'{W}tcPr/{W}shd')
    if shd is None:
        return ''
    val = shd.get(W + 'fill') or ''
    return '' if val in ('auto', 'FFFFFF') else val.upper()


def table_md(tbl):
    rows = []
    for tr in tbl.findall(W + 'tr'):
        rows.append([cell_text(tc) for tc in tr.findall(W + 'tc')])
    if not rows:
        return '', False
    if len(rows) == 1 and len(rows[0]) == 1 or all(len(r) == 1 for r in rows):
        body = '<br>'.join(r[0] for r in rows if r and r[0])
        return ('> ' + body if body else ''), False
    width = max(len(r) for r in rows)
    rows = [r + [''] * (width - len(r)) for r in rows]
    md = ['| ' + ' | '.join(rows[0]) + ' |', '|' + '---|' * width]
    md += ['| ' + ' | '.join(r) + ' |' for r in rows[1:]]
    return '\n'.join(md), True


class ModuleParser:
    def __init__(self, path, slug, title, icon, order, out_img: Path):
        self.path, self.slug, self.title, self.icon, self.order = path, slug, title, icon, order
        self.z = zipfile.ZipFile(path)
        self.rels = self._load_rels()
        self.img_dir = out_img / slug
        self.img_count = 0
        self.chapters, self.questions = [], []
        self.cur_q = None
        self.cur_ch = None
        self.desc = ''
        self.guide = ''

    def _load_rels(self):
        root = ET.fromstring(self.z.read('word/_rels/document.xml.rels'))
        return {r.get('Id'): r.get('Target') for r in root.iter(PR + 'Relationship')}

    def export_image(self, rid):
        target = self.rels.get(rid)
        if not target:
            return None
        name = 'word/' + target.lstrip('/').replace('word/', '') if not target.startswith('media') else 'word/' + target
        ext = Path(name).suffix.lower()
        if ext not in ('.png', '.jpg', '.jpeg', '.gif', '.webp'):
            return None
        try:
            data = self.z.read(name)
        except KeyError:
            return None
        self.img_dir.mkdir(parents=True, exist_ok=True)
        self.img_count += 1
        fname = f'img_{self.img_count:03d}{ext}'
        (self.img_dir / fname).write_bytes(data)
        return f'/images/{self.slug}/{fname}'

    def new_chapter(self, title):
        self.cur_ch = {'id': f'{self.slug}-ch{len(self.chapters) + 1}', 'title': title, 'questionIds': []}
        self.chapters.append(self.cur_ch)

    def new_question(self, code, priority, title):
        if self.cur_ch is None:
            self.new_chapter('第一篇：核心考点')
        qid = f'{self.slug}-{code.lower()}'
        self.cur_q = {
            'id': qid, 'moduleSlug': self.slug, 'chapterId': self.cur_ch['id'], 'code': code,
            'priority': priority, 'title': title.strip(), 'hints': [], 'blocks': [], 'images': [],
            'hasTable': False, 'hasCode': False,
        }
        self.cur_ch['questionIds'].append(qid)
        self.questions.append(self.cur_q)

    def add(self, md):
        if self.cur_q is not None and md:
            self.cur_q['blocks'].append(md)

    def handle_single_cell_box(self, tbl):
        tc = tbl.find(f'{W}tr/{W}tc')
        if tc is None:
            return False
        fill = cell_fill(tc)
        paras = [p for p in tc.findall(W + 'p')]
        first_txt = ptext(paras[0]) if paras else ''

        # 卷首单格框：提取简介或考前冲刺指南
        if self.cur_ch is None:
            texts = [ptext(p) for p in paras if ptext(p)]
            if fill == '1B365D' and len(texts) >= 2 and not self.desc:
                self.desc = texts[1]
            elif (fill == 'F1F5F9' or first_txt.startswith('考前复习指南：')) and texts:
                guide_txt = '\n'.join(texts)
                guide_txt = re.sub(r'^考前复习指南[：:]\s*', '', guide_txt)
                self.guide = guide_txt
            return True

        if self.cur_q is None:
            return True

        # 1. 青绿卡片：【面试标准回答】
        if fill == 'F0F7F4' or first_txt.startswith('【面试标准回答】'):
            lines = []
            auto_num = 0
            for p in paras:
                pt = ptext(p)
                if not pt or pt.startswith('【面试标准回答】'):
                    continue
                md = inline_md(p)
                # 将 "<strong>1. 关键词：</strong>说明" 转为有序列表 "1. <strong>关键词：</strong>说明"
                m_pt = re.match(r'^<strong>(\d+)\.\s*([^<]+?)</strong>(.*)$', md)
                if m_pt:
                    num, lead, rest = m_pt.group(1), m_pt.group(2).strip(), m_pt.group(3).lstrip()
                    auto_num = max(auto_num, int(num))
                    hint_key = lead.rstrip('：:')
                    if 1 < len(hint_key) <= 30:
                        self.cur_q.setdefault('_ia_hints', []).append(hint_key)
                    lines.append(f'{num}. <strong>{lead}</strong> {rest}'.rstrip())
                else:
                    # 兼容无序号粗体分点（如第 10 卷 "<strong>本质与切分机制</strong>说明..."）
                    m_sub = re.match(r'^<strong>([^<]{2,32})</strong>\s*(.+)$', md)
                    if m_sub and not pt.startswith(('核心结论', '加分收尾')):
                        auto_num += 1
                        lead = m_sub.group(1).strip()
                        rest = m_sub.group(2).lstrip()
                        if not lead.endswith(('：', ':')):
                            lead = f'{lead}：'
                        hint_key = lead.rstrip('：:')
                        if 1 < len(hint_key) <= 30:
                            self.cur_q.setdefault('_ia_hints', []).append(hint_key)
                        lines.append(f'{auto_num}. <strong>{lead}</strong> {rest}'.rstrip())
                    else:
                        if lines:
                            lines.append('')
                        lines.append(md)
                        if pt.startswith('核心结论'):
                            lines.append('')
            if lines:
                self.add(':::interview\n' + '\n'.join(lines) + '\n:::')
            return True

        # 2. 琥珀卡片：避坑提醒
        if fill == 'FFFBEB' or first_txt.startswith('避坑提醒：'):
            body = '\n\n'.join(inline_md(p) for p in paras if ptext(p))
            if body:
                self.add(':::pitfall\n' + body + '\n:::')
            return True

        # 3. 蓝色卡片：高频追问
        if fill == 'EFF6FF' or first_txt.startswith('高频追问：'):
            body = '\n\n'.join(inline_md(p) for p in paras if ptext(p))
            if body:
                self.add(':::followup\n' + body + '\n:::')
            return True

        # 4. 灰底代码块
        if fill == 'F8FAFC':
            raw_code = '\n'.join(code_text(p) for p in paras).rstrip()
            if raw_code.strip():
                lang = guess_lang(raw_code)
                self.add(f'```{lang}\n{raw_code}\n```')
                self.cur_q['hasCode'] = True
            return True

        return False

    def parse(self):
        body = ET.fromstring(self.z.read('word/document.xml')).find(W + 'body')
        code_buf = []
        intro_paras = []

        def flush_code():
            if code_buf and self.cur_q is not None:
                code = '\n'.join(code_buf).rstrip()
                self.add(f'```{guess_lang(code)}\n{code}\n```')
                self.cur_q['hasCode'] = True
            code_buf.clear()

        for el in body:
            tag = el.tag.replace(W, '')
            if tag == 'tbl':
                flush_code()
                rows = el.findall(W + 'tr')
                is_single_box = len(rows) == 1 and len(rows[0].findall(W + 'tc')) == 1
                if is_single_box and self.handle_single_cell_box(el):
                    continue
                if self.cur_q is not None:
                    md, is_real_table = table_md(el)
                    if md:
                        self.add(md)
                        if is_real_table:
                            self.cur_q['hasTable'] = True
                continue
            if tag != 'p':
                continue

            text = ptext(el)
            blips = [b.get(R + 'embed') for b in el.iter(A + 'blip')]
            style = pstyle(el)

            if is_code_para(el) and self.cur_q is not None:
                code_buf.append(code_text(el))
                continue
            flush_code()

            # 图片
            if blips and self.cur_q is not None:
                for rid in blips:
                    url = self.export_image(rid)
                    if url:
                        self.cur_q['images'].append({'url': url, 'caption': ''})
                        self.add(f'![]({url})')
                if not text:
                    continue

            if not text:
                continue

            m_ch = CH_RE.match(text)
            if m_ch and len(text) < 80:
                self.new_chapter(text)
                self.cur_q = None
                continue

            m_q = Q_RE.match(text)
            if m_q:
                raw_num = m_q.group(1)
                code = f'Q{raw_num}' if len(raw_num) >= 3 else f'Q{int(raw_num):02d}'
                self.new_question(code, m_q.group(2), m_q.group(3))
                continue

            if self.cur_q is None:
                intro_paras.append(text)
                continue

            # 原理详细讲解分隔标识
            if text == '【原理详细讲解】':
                self.add(':::explain-divider')
                continue

            # 图注
            if re.match(r'^图\s*\d+', text) and self.cur_q['images'] and not self.cur_q['images'][-1]['caption']:
                self.cur_q['images'][-1]['caption'] = text
                last = self.cur_q['blocks'][-1] if self.cur_q['blocks'] else ''
                if last.startswith('![]('):
                    self.cur_q['blocks'][-1] = last.replace('![](', f'![{text}](', 1)
                self.add(f'<p class="img-caption">{text}</p>')
                continue

            md = inline_md(el) or text
            if style is not None and style not in ('Normal', 'a'):
                clean_h = re.sub(r'^(?:\d+\.\s+|[一二三四五六七八九十]+、\s*)', '', text).strip()
                self.add(f'#### {text}')
                self.cur_q['_subs'] = self.cur_q.get('_subs', []) + [clean_h]
            elif text.startswith('▸'):
                self.add(f'<p class="sub-point">{text.lstrip("▸ ").strip()}</p>')
                self.cur_q['_subs'] = self.cur_q.get('_subs', []) + [text.lstrip('▸ ').strip()]
            elif text.startswith(('•', '●', '·')):
                self.add('- ' + re.sub(r'^(<strong>)?\s*[•●·]\s*', r'\1', md).strip())
            elif re.match(r'^[①②③④⑤⑥⑦⑧⑨⑩]', text):
                self.add('- ' + md)
            else:
                self.add(md)
        flush_code()

        if intro_paras:
            if not self.desc:
                self.desc = intro_paras[1] if len(intro_paras) > 1 else intro_paras[0]
            if not self.guide:
                self.guide = next((p for p in intro_paras if '冲刺' in p or '速查' in p), '')
        self.chapters = [c for c in self.chapters if c['questionIds']]
        for i, q in enumerate(self.questions):
            q['order'] = i + 1
            q['hints'] = self.make_hints(q)
            q.pop('_subs', None)
            q.pop('_ia_hints', None)
            q['answerMd'] = self.join_blocks(q.pop('blocks'))

    @staticmethod
    def join_blocks(blocks):
        out = []
        for b in blocks:
            if out and b.startswith('- ') and out[-1].startswith('- '):
                out[-1] += '\n' + b
            else:
                out.append(b)
        return '\n\n'.join(out)

    @staticmethod
    def make_hints(q):
        hints = []
        for h in q.get('_ia_hints', []):
            if h not in hints:
                hints.append(h)
        if len(hints) < 3:
            for s in q.get('_subs', []):
                s = s.rstrip('：:')
                if 1 < len(s) < 40 and s not in hints:
                    hints.append(s)
        if len(hints) < 2:
            for b in q['blocks']:
                if b.startswith('- '):
                    t = re.sub(r'</?strong>|[`*]', '', b[2:]).strip()
                    t = re.split(r'[：:，,。]', t)[0].strip()
                    if 1 < len(t) <= 30 and t not in hints:
                        hints.append(t)
                if len(hints) >= 4:
                    break
        return hints[:5]


def parse_all(src: Path, out_dir: Path, write_report: bool = True):
    out_data = out_dir / 'data'
    out_img = out_dir / 'images'
    if out_data.exists():
        shutil.rmtree(out_data)
    if out_img.exists():
        shutil.rmtree(out_img)
    (out_data / 'questions').mkdir(parents=True)

    files = {p.name[:2]: p for p in src.glob('*.docx') if not p.name.startswith('~$')}
    modules, report = [], []
    for order, (prefix, slug, title, icon) in enumerate(MODULES, 1):
        path = files.get(prefix)
        if not path:
            print(f'[WARN] 未找到 {prefix} 开头的笔记')
            continue
        mp = ModuleParser(path, slug, title, icon, order, out_img)
        mp.parse()
        qs = mp.questions
        cnt = {p: sum(1 for q in qs if q['priority'] == p) for p in ('P0', 'P1', 'P2')}
        modules.append({
            'slug': slug, 'order': order, 'title': title, 'fullTitle': path.stem, 'icon': icon,
            'description': mp.desc, 'sprintGuide': mp.guide, 'questionCount': len(qs),
            'p0Count': cnt['P0'], 'p1Count': cnt['P1'], 'p2Count': cnt['P2'],
            'imageCount': sum(len(q['images']) for q in qs),
            'chapters': [{'id': c['id'], 'title': c['title'], 'questionCount': len(c['questionIds'])} for c in mp.chapters],
        })
        (out_data / 'questions' / f'{slug}.json').write_text(json.dumps(qs, ensure_ascii=False), encoding='utf-8')
        report.append({'slug': slug, 'questions': len(qs), **cnt, 'images': mp.img_count, 'chapters': len(mp.chapters)})
        print(f'{slug:22s} 题目 {len(qs):3d}  P0 {cnt["P0"]:3d}  P1 {cnt["P1"]:3d}  P2 {cnt["P2"]:3d}  图片 {mp.img_count:3d}  篇目 {len(mp.chapters)}')
    (out_data / 'modules.json').write_text(json.dumps(modules, ensure_ascii=False, indent=2), encoding='utf-8')
    if write_report:
        (ROOT / 'scripts' / 'parse_report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('合计题目', sum(r['questions'] for r in report), '图片', sum(r['images'] for r in report))
    return modules, report


def main():
    ap = argparse.ArgumentParser(description='解析 d:\\八股 9 份 Word 笔记为 JSON')
    ap.add_argument('pos_src', nargs='?', default=r'd:\八股', help='源 Word 目录（位置参数兼容旧用法）')
    ap.add_argument('--src', default=None, help='源 Word 目录')
    ap.add_argument('--out-dir', default=str(ROOT / 'frontend' / 'public'), help='输出根目录（含 data/ 与 images/）')
    ap.add_argument('--no-report', action='store_true', help='不覆盖 scripts/parse_report.json')
    args = ap.parse_args()
    src = Path(args.src or args.pos_src)
    out_dir = Path(args.out_dir)
    parse_all(src, out_dir, write_report=not args.no_report)


if __name__ == '__main__':
    main()
