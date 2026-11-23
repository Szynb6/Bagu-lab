"""
将 d:\\八股 下的 9 份 Word 面试笔记解析为结构化 JSON 题库（供前端预览 / 后续后端导入）。

输出：
  frontend/public/data/modules.json              9 大模块元信息 + 篇目
  frontend/public/data/questions/{slug}.json     单模块题目（Markdown 答案 + Hints + 图片）
  frontend/public/images/{slug}/*.png            题目引用的原理图（后续将迁移至 RustFS bagu-images 桶）
  scripts/parse_report.json                      解析统计报告

用法：python scripts/docx_to_json.py [源目录]
"""
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
A = '{http://schemas.openxmlformats.org/drawingml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
PR = '{http://schemas.openxmlformats.org/package/2006/relationships}'

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r'd:\八股')
OUT_DATA = ROOT / 'frontend' / 'public' / 'data'
OUT_IMG = ROOT / 'frontend' / 'public' / 'images'

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
]

Q_RE = re.compile(r'^Q(\d+)\s*【\s*(P[012])[^】]*】\s*(.+)$')
CH_RE = re.compile(r'^第[一二三四五六七八九十]+(?:篇|部分)\s*[：:]\s*(.+)$')
DP_RE = re.compile(r'^(\d+)\.\s*(.+)$')
CMP_RE = re.compile(r'^对比\s*\d+\s*[：:]\s*(.+)$')
DP_P0 = ('单例', '工厂', '代理', '模板方法', '策略')
CODE_FONTS = ('Consolas', 'Courier New', 'JetBrains Mono', 'Menlo', 'Monaco')


def ptext(p):
    return ''.join(t.text or '' for t in p.iter(W + 't')).strip()


def pstyle(p):
    st = p.find(f'{W}pPr/{W}pStyle')
    return st.get(W + 'val') if st is not None else None


def run_is_code(r):
    rf = r.find(f'{W}rPr/{W}rFonts')
    return rf is not None and (rf.get(W + 'ascii') or '') in CODE_FONTS


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
            res.append(f'{lead}<strong>{s}</strong>')
        else:
            res.append(txt)
    return ''.join(res).strip()


def guess_lang(code):
    head = code.strip()[:200].upper()
    if re.search(r'\b(SELECT|INSERT|UPDATE|CREATE TABLE|ALTER|EXPLAIN)\b', head):
        return 'sql'
    if re.search(r'^(SET|GET|HSET|ZADD|EXPIRE|SETNX|LPUSH|DEL)\s', head):
        return 'bash'
    if code.lstrip().startswith(('-X', 'java -', 'jstack', 'jmap', 'jstat', 'top', '$', '#!/')):
        return 'bash'
    if code.lstrip().startswith('<'):
        return 'xml'
    return 'java'


def cell_text(tc):
    lines = [inline_md(p) for p in tc.iter(W + 'p')]
    s = '<br>'.join(l for l in lines if l)
    return s.replace('|', '\\|')


def table_md(tbl):
    rows = []
    for tr in tbl.findall(W + 'tr'):
        rows.append([cell_text(tc) for tc in tr.findall(W + 'tc')])
    if not rows:
        return ''
    if len(rows) == 1 and len(rows[0]) == 1 or all(len(r) == 1 for r in rows):
        # 单列表格多用作提示框
        body = '<br>'.join(r[0] for r in rows if r and r[0])
        return '> ' + body if body else ''
    width = max(len(r) for r in rows)
    rows = [r + [''] * (width - len(r)) for r in rows]
    md = ['| ' + ' | '.join(rows[0]) + ' |', '|' + '---|' * width]
    md += ['| ' + ' | '.join(r) + ' |' for r in rows[1:]]
    return '\n'.join(md)


class ModuleParser:
    def __init__(self, path, slug, title, icon, order):
        self.path, self.slug, self.title, self.icon, self.order = path, slug, title, icon, order
        self.z = zipfile.ZipFile(path)
        self.rels = self._load_rels()
        self.img_dir = OUT_IMG / slug
        self.img_count = 0
        self.chapters, self.questions = [], []
        self.cur_q = None
        self.cur_ch = None
        self.desc = ''
        self.guide = ''
        self.is_dp = slug == '07-design-patterns'

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
            self.new_chapter('综合')
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
                if self.cur_q is not None:
                    md = table_md(el)
                    if md:
                        self.add(md)
                        if not md.startswith('>'):
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
            if m_q and not self.is_dp:
                self.new_question(f'Q{int(m_q.group(1)):02d}', m_q.group(2), m_q.group(3))
                continue

            if self.is_dp:
                m_dp = DP_RE.match(text)
                m_cmp = CMP_RE.match(text)
                if style == '4' and m_dp:
                    name = m_dp.group(2)
                    pri = 'P0' if any(k in name for k in DP_P0) else 'P1'
                    self.new_question(f'DP{int(m_dp.group(1)):02d}', pri, f'{name}：核心思想、实现方式与框架应用？')
                    continue
                if style == '5' and m_cmp:
                    n = len([q for q in self.questions if q['code'].startswith('CMP')]) + 1
                    self.new_question(f'CMP{n:02d}', 'P1', f'{m_cmp.group(1)}：有什么区别？')
                    continue

            if self.cur_q is None:
                intro_paras.append(text)
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
            if style is not None:
                self.add(f'#### {text}')
                self.cur_q['_subs'] = self.cur_q.get('_subs', []) + [text]
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
            self.desc = intro_paras[1] if len(intro_paras) > 1 else intro_paras[0]
            self.guide = next((p for p in intro_paras if '冲刺' in p or '速查' in p), '')
        self.chapters = [c for c in self.chapters if c['questionIds']]
        for i, q in enumerate(self.questions):
            q['order'] = i + 1
            q['hints'] = self.make_hints(q)
            q.pop('_subs', None)
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
        subs = [s.rstrip('：:') for s in q.get('_subs', []) if 1 < len(s) < 40]
        hints = []
        for s in subs:
            if s not in hints:
                hints.append(s)
        if len(hints) < 2:
            for b in q['blocks']:
                if b.startswith('- '):
                    t = re.sub(r'[*`]', '', b[2:]).strip()
                    t = re.split(r'[：:，,。]', t)[0]
                    if 1 < len(t) <= 30 and t not in hints:
                        hints.append(t)
                if len(hints) >= 4:
                    break
        return hints[:5]


def main():
    if OUT_DATA.exists():
        shutil.rmtree(OUT_DATA)
    if OUT_IMG.exists():
        shutil.rmtree(OUT_IMG)
    (OUT_DATA / 'questions').mkdir(parents=True)
    files = {p.name[:2]: p for p in SRC.glob('*.docx') if not p.name.startswith('~$')}
    modules, report = [], []
    for order, (prefix, slug, title, icon) in enumerate(MODULES, 1):
        path = files.get(prefix)
        if not path:
            print(f'[WARN] 未找到 {prefix} 开头的笔记')
            continue
        mp = ModuleParser(path, slug, title, icon, order)
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
        (OUT_DATA / 'questions' / f'{slug}.json').write_text(json.dumps(qs, ensure_ascii=False), encoding='utf-8')
        report.append({'slug': slug, 'questions': len(qs), **cnt, 'images': mp.img_count, 'chapters': len(mp.chapters)})
        print(f'{slug:22s} 题目 {len(qs):3d}  P0 {cnt["P0"]:3d}  P1 {cnt["P1"]:3d}  P2 {cnt["P2"]:3d}  图片 {mp.img_count:3d}  篇目 {len(mp.chapters)}')
    (OUT_DATA / 'modules.json').write_text(json.dumps(modules, ensure_ascii=False, indent=2), encoding='utf-8')
    (ROOT / 'scripts' / 'parse_report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('合计题目', sum(r['questions'] for r in report), '图片', sum(r['images'] for r in report))


if __name__ == '__main__':
    main()

