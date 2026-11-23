export function dateKey(d: Date | number = new Date()): string {
  const x = typeof d === 'number' ? new Date(d) : d
  const m = `${x.getMonth() + 1}`.padStart(2, '0')
  const day = `${x.getDate()}`.padStart(2, '0')
  return `${x.getFullYear()}-${m}-${day}`
}

export function endOfToday(): number {
  const d = new Date()
  d.setHours(23, 59, 59, 999)
  return d.getTime()
}

export function relativeTime(ts: number | null): string {
  if (!ts) return '从未'
  const diff = Date.now() - ts
  const abs = Math.abs(diff)
  const min = 60_000
  const hour = 60 * min
  const day = 24 * hour
  const suffix = diff >= 0 ? '前' : '后'
  if (abs < min) return diff >= 0 ? '刚刚' : '即将'
  if (abs < hour) return `${Math.round(abs / min)} 分钟${suffix}`
  if (abs < day) return `${Math.round(abs / hour)} 小时${suffix}`
  return `${Math.round(abs / day)} 天${suffix}`
}

export function dueLabel(ts: number | null): string {
  if (!ts) return '未安排'
  if (ts <= endOfToday()) return '今日待复习'
  return `${relativeTime(ts)}复习`
}

export function stripMarkdown(md: string): string {
  return md
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[#>*`|\-]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)
}

export function highlight(text: string, kw: string): string {
  const safe = escapeHtml(text)
  if (!kw.trim()) return safe
  const words = kw.trim().split(/\s+/).map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  return safe.replace(new RegExp(`(${words.join('|')})`, 'gi'), '<mark>$1</mark>')
}
