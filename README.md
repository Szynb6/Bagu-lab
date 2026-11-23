# Bagu Platform · 个人八股背诵与面试模拟平台

> 基于 [d:/八股](file:///d:/八股) 中 9 份 Java 后端核心面试笔记（JVM、并发、基础集合、Spring/MyBatis、MySQL、Redis、设计模式、计算机网络、操作系统，共约 **390+ 题 · 103 张高清原理图 · 180+ 对比表格**）打造的前后端分离背诵与模拟面试系统。

## 项目选型总览

| 维度 | 最终确认方案 |
| :--- | :--- |
| **架构模式** | 前后端分离（Monorepo：`D:\Java\Bagu`） |
| **前端技术栈** | **Vue 3** + Vite + TypeScript + Vue Router + Pinia + Tailwind CSS + Axios |
| **后端技术栈** | Java 17 + Spring Boot 3.x + MyBatis-Plus + **Sa-Token（集成 Redis）** + Knife4j |
| **数据库** | **PostgreSQL 17**（含 `pg_trgm` 模糊检索扩展） |
| **缓存与对象存储** | **Redis 7.4**（Sa-Token 会话 / Bitmap 打卡 / 热点缓存）+ **RustFS（S3 协议，独立 `bagu-images` 桶）** |
| **中间件部署策略** | **方案 A（复用服务器现有中间件，逻辑完全隔离）**：复用 `59.110.124.170` 上已运行的 `ragent-local-postgres-1`（新建 `bagu_db` + `bagu_user`）、`ragent-local-redis-1`（独立 `database: 2` + `bagu:` 前缀）、`ragent-local-rustfs-1`（独立 `bagu-images` 桶） |

## 规划目录结构（`D:\Java\Bagu`）

```text
D:\Java\Bagu\
├── README.md                                   # 项目总览与设计文档索引（本文件）
├── docs/
│   ├── 01-frontend-design.md                   # 前端页面完整设计方案（含前后端分离升级点）
│   ├── 02-architecture-and-database-pg.md      # 系统架构、方案 A 中间件规划、PG17 完整 DDL 与 API 规范
│   ├── 03-development-modules.md               # 全栈开发模块清单（不含 AI，22 个子模块与依赖排期）
│   └── 04-feature-scope-checklist.md           # 功能全景梳理与优先级裁剪勾选表
├── scripts/                                    #（待开发）docx_to_json.py：解析 d:\八股\*.docx 与图片
├── backend/                                    #（待开发）Spring Boot 3 后端工程
├── frontend/                                   #（待开发）前端工程
└── deploy/                                     #（待开发）docker-compose.yml & nginx.conf
```

## 设计文档导航

1. **[01-frontend-design.md](./docs/01-frontend-design.md)** — 内容盘点、视觉设计系统（P0/P1/P2 色彩体系与明暗双主题）、9 大页面线框图与交互规范（含登录页、在线编辑页、闪卡背诵、沉浸通读、模拟面试、原理图解画廊）、全键盘快捷键、移动端 PWA 适配。
2. **[02-architecture-and-database-pg.md](./docs/02-architecture-and-database-pg.md)** — 前后端分离总体架构、服务器方案 A 资源与网络隔离规划、**PostgreSQL 17 完整建表 DDL（8 张表 + 索引）**、Redis Key 设计、RESTful API 接口契约、Word 笔记入库流水线与分阶段开发计划。
3. **[03-development-modules.md](./docs/03-development-modules.md)** — 不含 AI 模块的全部待开发模块清单（基础设施/后端/前端/部署共 4 大类 22 个子模块、MVP 与 V1.1 优先级划分及模块依赖关系）。
4. **[04-feature-scope-checklist.md](./docs/04-feature-scope-checklist.md)** — **V1.0 功能范围最终定稿**（纯用户端：组 1~4 共 16 项首批开发，5 页面 · 6 张 PG 表 · 15 个接口；组 5 模拟面试、组 6 图解画廊、组 7 管理端留作后期迭代）。
