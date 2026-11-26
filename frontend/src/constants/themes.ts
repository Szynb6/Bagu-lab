export type ThemeId =
  | 'paper'
  | 'matcha'
  | 'sepia'
  | 'slate'
  | 'graphite'
  | 'midnight'
  | 'forest'

export type ThemeMode = 'light' | 'dark'

export interface ThemeMeta {
  id: ThemeId
  name: string
  desc: string
  mode: ThemeMode
  metaColor: string
  /** 三色预览色点：[画布底色, 卡片纸面, 主强调色] */
  swatches: [string, string, string]
}

export const THEMES: ThemeMeta[] = [
  {
    id: 'paper',
    name: '暖纸朱砂',
    desc: '经典温润米纸 · 手稿默认',
    mode: 'light',
    metaColor: '#F7F6F2',
    swatches: ['#F7F6F2', '#FFFFFF', '#C84B31'],
  },
  {
    id: 'matcha',
    name: '青瓷护眼',
    desc: '淡雅豆沙绿 · 久读柔和护眼',
    mode: 'light',
    metaColor: '#EFF4F0',
    swatches: ['#EFF4F0', '#F9FCF9', '#236B56'],
  },
  {
    id: 'sepia',
    name: '羊皮古卷',
    desc: '低蓝光暖纸 · 仿纸质书阅读',
    mode: 'light',
    metaColor: '#F4ECD8',
    swatches: ['#F4ECD8', '#FBF6EA', '#A8541A'],
  },
  {
    id: 'slate',
    name: '海盐文档',
    desc: '清透冷白手册 · 适合读代码',
    mode: 'light',
    metaColor: '#F3F6FA',
    swatches: ['#F3F6FA', '#FFFFFF', '#2457C5'],
  },
  {
    id: 'graphite',
    name: '石墨朱砂',
    desc: '经典暖石墨 · 夜间专注背诵',
    mode: 'dark',
    metaColor: '#111110',
    swatches: ['#111110', '#1A1917', '#E06246'],
  },
  {
    id: 'midnight',
    name: '深海夜航',
    desc: 'IDE 深蓝灰 · 夜间代码友好',
    mode: 'dark',
    metaColor: '#0E141F',
    swatches: ['#0E141F', '#151D2C', '#5B9BF8'],
  },
  {
    id: 'forest',
    name: '松林墨绿',
    desc: '低对比墨松 · 深夜柔和防眩',
    mode: 'dark',
    metaColor: '#131A17',
    swatches: ['#131A17', '#1A231F', '#56B498'],
  },
]

export const THEME_MAP: Record<ThemeId, ThemeMeta> = Object.fromEntries(
  THEMES.map((t) => [t.id, t]),
) as Record<ThemeId, ThemeMeta>

export const LIGHT_THEMES: ThemeMeta[] = THEMES.filter((t) => t.mode === 'light')
export const DARK_THEMES: ThemeMeta[] = THEMES.filter((t) => t.mode === 'dark')

export function resolveThemeId(raw: string | null, prefersDark: boolean): ThemeId {
  if (raw === 'light') return 'paper'
  if (raw === 'dark') return 'graphite'
  if (raw && raw in THEME_MAP) return raw as ThemeId
  return prefersDark ? 'graphite' : 'paper'
}
