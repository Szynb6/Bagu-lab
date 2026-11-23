<#
.SYNOPSIS
  Bagu Lab 一键本地构建并发布到云服务器 (/opt/bagu)
.EXAMPLE
  .\deploy\deploy.ps1 -Target frontend   # 仅更新前端（约 5 秒，免重启 Nginx）
  .\deploy\deploy.ps1 -Target backend    # 仅更新后端（自动备份 app.jar.bak 并重启）
  .\deploy\deploy.ps1 -Target all        # 前后端全量更新
#>
param(
    [ValidateSet("all", "frontend", "backend")]
    [string]$Target = "all",
    [string]$Server = "root@59.110.124.170",
    [string]$RemoteDir = "/opt/bagu"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if ($env:Path -notlike "*C:\Program Files\nodejs*") {
    $env:Path = "C:\Program Files\nodejs;" + $env:Path
}

if ($Target -in @("all", "frontend")) {
    Write-Host "`n[1/2] 构建前端 dist..." -ForegroundColor Cyan
    Push-Location (Join-Path $Root "frontend")
    try {
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw "前端构建失败" }
    } finally {
        Pop-Location
    }

    Write-Host "上传前端 dist 至 ${Server}:${RemoteDir}/dist ..." -ForegroundColor Cyan
    & ssh $Server "mkdir -p $RemoteDir/dist && rm -rf $RemoteDir/dist/*"
    & scp -r (Join-Path $Root "frontend\dist\*") "${Server}:${RemoteDir}/dist/"
    Write-Host "前端发布完成（Nginx 静态挂载，已实时生效）" -ForegroundColor Green
}

if ($Target -in @("all", "backend")) {
    Write-Host "`n[2/2] 构建后端 Fat JAR..." -ForegroundColor Cyan
    Push-Location (Join-Path $Root "backend")
    try {
        & .\mvnw.cmd -B clean package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "后端构建失败" }
    } finally {
        Pop-Location
    }

    $Jar = Join-Path $Root "backend\target\bagu-backend-0.1.0-SNAPSHOT.jar"
    Write-Host "备份旧 JAR 并上传新 app.jar 至 ${Server}:${RemoteDir} ..." -ForegroundColor Cyan
    & ssh $Server "if [ -f $RemoteDir/app.jar ]; then cp -f $RemoteDir/app.jar $RemoteDir/app.jar.bak; fi"
    & scp $Jar "${Server}:${RemoteDir}/app.jar"

    Write-Host "重启后端容器 bagu-api ..." -ForegroundColor Cyan
    & ssh $Server "cd $RemoteDir && docker compose up -d bagu-api && docker compose restart bagu-api"
    Write-Host "后端发布完成！可用 'ssh $Server docker logs -f --tail 50 bagu-api' 查看启动日志" -ForegroundColor Green
}
