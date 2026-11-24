import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import json from 'highlight.js/lib/languages/json'
import { escapeHtml } from './format'

hljs.registerLanguage('java', java)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('json', json)

const md = new MarkdownIt({
  html: true,
  linkify: true,
  breaks: false,
})

md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
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
  const t = tokens[idx]
  const src = t.attrGet('src') ?? ''
  const alt = escapeHtml(t.content || '原理图解')
  return `<img src="${src}" alt="${alt}" decoding="async" tabindex="0" role="button" data-zoom />`
}

/**
 * 预处理题库 Markdown：
 * 1. 移除 Word 解析残留的空 <strong></strong> 标签；
 * 2. 在非代码块区域，仅保留入库脚本生成的结构化 HTML 标签（<strong>、<br>、<p class="sub-point|img-caption">），
 *    其余尖括号（如 MyBatis 的 <select>/<insert>/<if>/<script>、JVM 的 <clinit>/<init>、Java 泛型 <T>/<String>）
 *    统一转义为 &lt;，防止被浏览器当作原生未闭合 HTML 标签（如 <select> 下拉框或 <script>）吞掉后续全部正文。
 */
function sanitizeMarkdownSource(src: string): string {
  const cleaned = src.replace(/<strong>\s*<\/strong>/g, '')
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
