import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import json from 'highlight.js/lib/languages/json'
import lua from 'highlight.js/lib/languages/lua'
import { escapeHtml } from './format'

hljs.registerLanguage('java', java)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('json', json)
hljs.registerLanguage('lua', lua)

const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: false,
})

const CALLOUT_OPEN_RE = /^:::(interview|pitfall|followup)\s*$/
const CALLOUT_CLOSE_RE = /^:::\s*$/
const DIVIDER_RE = /^:::explain-divider\s*$/

/**
 * 解析 `:::explain-divider` 原理详解分区分隔头。
 */
md.block.ruler.before('fence', 'bagu_explain_divider', (state, startLine, _endLine, silent) => {
  const pos = state.bMarks[startLine]! + state.tShift[startLine]!
  const max = state.eMarks[startLine]!
  const line = state.src.slice(pos, max).trim()
  if (!DIVIDER_RE.test(line)) return false
  if (silent) return true

  const token = state.push('bagu_explain_divider', 'div', 0)
  token.map = [startLine, startLine + 1]
  state.line = startLine + 1
  return true
})

md.renderer.rules.bagu_explain_divider = () =>
  '<div class="explain-divider"><span>原理详细讲解</span></div>\n'

/**
 * 解析 `:::interview` / `:::pitfall` / `:::followup` ... `:::` 语义卡片容器。
 */
md.block.ruler.before('fence', 'bagu_callout', (state, startLine, endLine, silent) => {
  const pos = state.bMarks[startLine]! + state.tShift[startLine]!
  const max = state.eMarks[startLine]!
  const firstLine = state.src.slice(pos, max).trim()
  const match = CALLOUT_OPEN_RE.exec(firstLine)
  if (!match) return false

  const kind = match[1]!
  let nextLine = startLine + 1
  let foundClose = false
  while (nextLine < endLine) {
    const lPos = state.bMarks[nextLine]! + state.tShift[nextLine]!
    const lMax = state.eMarks[nextLine]!
    if (CALLOUT_CLOSE_RE.test(state.src.slice(lPos, lMax).trim())) {
      foundClose = true
      break
    }
    nextLine += 1
  }
  if (!foundClose) return false
  if (silent) return true

  const oldParent = state.parentType
  const oldLineMax = state.lineMax
  state.parentType = 'container' as typeof state.parentType
  state.lineMax = nextLine

  const openToken = state.push('bagu_callout_open', 'div', 1)
  openToken.info = kind
  openToken.map = [startLine, nextLine + 1]

  state.md.block.tokenize(state, startLine + 1, nextLine)

  state.push('bagu_callout_close', 'div', -1)

  state.parentType = oldParent
  state.lineMax = oldLineMax
  state.line = nextLine + 1
  return true
})

md.renderer.rules.bagu_callout_open = (tokens, idx) => {
  const kind = tokens[idx]?.info ?? 'interview'
  if (kind === 'interview') {
    return (
      '<div class="callout callout-interview">' +
      '<div class="callout-head">' +
      '<span class="callout-title">面试标准回答</span>' +
      '<span class="callout-sub">30~90 秒口述版</span>' +
      '</div>' +
      '<div class="callout-body">\n'
    )
  }
  return `<div class="callout callout-${kind}"><div class="callout-body">\n`
}

md.renderer.rules.bagu_callout_close = () => '</div></div>\n'

/**
 * 判断围栏块是否为文本流程图 / 结构示意图（而非真正的编程语言代码）。
 * 避免将 ┌──┐ / ├─ / ↓ / ▼ 等流程示意图套上 "JAVA | 复制" 代码框并错误高亮。
 */
function isAsciiDiagram(content: string): boolean {
  const trimmed = content.trim()
  if (!trimmed) return false
  const looksLikeCode =
    /(^|\n)\s*(public|private|protected|class|interface|import|package|return|SELECT|INSERT|UPDATE|DELETE|CREATE)\b/.test(
      trimmed,
    ) ||
    /(^|\n)\s*\/\//.test(trimmed) ||
    /;\s*($|\n)/.test(trimmed)
  if (looksLikeCode) return false

  if (/[┌┐└┘├┤┬┴┼│─═║╔╗╚╝↓↑▼▲]/.test(trimmed)) {
    return true
  }
  if (/[→←]/.test(trimmed) && !/[{}]/.test(trimmed)) {
    return true
  }
  return false
}

md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]!
  if (isAsciiDiagram(token.content)) {
    return `<div class="diagram-block"><div class="diagram-head"><span>流程示意图</span></div><pre><code>${escapeHtml(token.content.replace(/\n+$/, ''))}</code></pre></div>`
  }

  const rawLang = (token.info || '').trim().split(/\s+/)[0] || 'text'
  const lang =
    (rawLang === 'sql' || rawLang === 'java') && token.content.trimStart().startsWith('<')
      ? 'xml'
      : rawLang
  let body: string
  try {
    body = hljs.getLanguage(lang) ? hljs.highlight(token.content, { language: lang }).value : escapeHtml(token.content)
  } catch {
    body = escapeHtml(token.content)
  }
  return `<div class="code-block"><div class="code-head"><span>${lang}</span><button type="button" class="copy-btn" data-copy>复制</button></div><pre><code class="hljs">${body}</code></pre></div>`
}

md.renderer.rules.table_open = () => '<div class="table-wrap"><table>'
md.renderer.rules.table_close = () => '</table></div>'

md.renderer.rules.image = (tokens, idx) => {
  const t = tokens[idx]!
  const src = t.attrGet('src') ?? ''
  const alt = escapeHtml(t.content || '原理图解')
  return `<img src="${src}" alt="${alt}" decoding="async" tabindex="0" role="button" data-zoom />`
}

/**
 * 预处理题库 Markdown：
 * 1. 移除 Word 解析残留的空 <strong></strong> 标签；
 * 2. 合并因 Word 混用字体导致被拆成两截的文本流程图；
 * 3. 在非代码块区域，仅保留入库脚本生成的结构化 HTML 标签（<strong>、<br>、<p class="sub-point|img-caption">），
 *    其余尖括号（如 <select>、<script>、<clinit>、List<String>）统一转义为 &lt;，防止吞掉后续正文。
 */
function sanitizeMarkdownSource(src: string): string {
  let cleaned = src.replace(/<strong>\s*<\/strong>/g, '')

  cleaned = cleaned.replace(
    /(^|\n)([^\n`]*[→←][^\n`]*)\n{1,2}```\w*\n(\s*[↓↑▼▲│][^\n]*[\s\S]*?)```/g,
    (_m, lead: string, firstLine: string, rest: string) => {
      const normalizedRest = rest.replace(/\n\s*\n/g, '\n').trimEnd()
      return `${lead}\`\`\`diagram\n${firstLine.trim()}\n${normalizedRest}\n\`\`\``
    },
  )

  return cleaned
    .split(/(```[\s\S]*?```|`[^`\n]+`)/g)
    .map((part, idx) => {
      if (idx % 2 === 1) return part
      return part.replace(
        /(<\/?strong>|<br\s*\/?>|<p class="(?:sub-point|img-caption)">|<\/p>)|(<)/g,
        (_m, allowed: string | undefined) => (allowed ? allowed : '&lt;'),
      )
    })
    .join('')
}

const cache = new Map<string, string>()

export function renderMarkdown(src: string): string {
  const hit = cache.get(src)
  if (hit) return hit
  const html = md.render(sanitizeMarkdownSource(src))
  if (cache.size > 400) cache.clear()
  cache.set(src, html)
  return html
}
