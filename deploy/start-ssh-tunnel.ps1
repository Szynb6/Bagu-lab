# 一键开启本地开发 SSH 端口转发隧道（将服务器 59.110.124.170 的 PG17 / Redis / RustFS 映射到本地）
# 映射关系：
#   localhost:5432 -> 127.0.0.1:5432 (PostgreSQL 17 · bagu_db)
#   localhost:6379 -> 127.0.0.1:6379 (Redis 7.4 · db=2)
#   localhost:9000 -> 127.0.0.1:9000 (RustFS S3 API · bagu-images)
#   localhost:9001 -> 127.0.0.1:9001 (RustFS Web Console)

$Server = "root@59.110.124.170"
Write-Host "正在建立 SSH 隧道连接 $Server ..." -ForegroundColor Cyan
Write-Host "映射端口: PG(5432) | Redis(6379) | RustFS(9000/9001)" -ForegroundColor Green
Write-Host "保持本窗口打开即可在本地启动 Spring Boot 后端；按 Ctrl+C 断开隧道。" -ForegroundColor Yellow

ssh -N `
  -L 5432:127.0.0.1:5432 `
  -L 6379:127.0.0.1:6379 `
  -L 9000:127.0.0.1:9000 `
  -L 9001:127.0.0.1:9001 `
  -o ServerAliveInterval=30 `
  -o ServerAliveCountMax=3 `
  $Server
