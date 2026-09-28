<#
.SYNOPSIS
    glimmer 一键部署脚本（Windows 本地构建 -> 上传服务器 -> 重启 -> 健康检查）

.DESCRIPTION
    后端：mvn 打包 -> scp 上传 app.jar.new -> 原子替换 app.jar -> systemctl 重启
          -> actuator 健康检查，失败自动回滚上一版本（app.jar.bak）
    前端：npm run build -> scp 上传 dist -> 与 /var/www/glimmer 原子切换
          -> 线上首页校验，失败自动回滚（/var/www/glimmer-old）

.PARAMETER App
    部署目标：frontend（仅前端）/ backend（仅后端）/ all（前后端，默认）

.PARAMETER SkipBuild
    跳过本地构建，直接上传 target/ 和 dist/ 下已有的构建产物

.EXAMPLE
    .\deploy.ps1
    部署前后端（完整构建）

.EXAMPLE
    .\deploy.ps1 -App frontend
    只部署前端

.EXAMPLE
    .\deploy.ps1 -App backend -SkipBuild
    只重传并重启后端（用现有 target jar，不重新打包）
#>
param(
    [ValidateSet('frontend', 'backend', 'all')]
    [string]$App = 'all',

    [switch]$SkipBuild
)

# ==================== 服务器配置（按需修改） ====================
$Server       = '43.129.213.98'
$User         = 'ubuntu'
$Key          = 'D:\.ssh\glimmer.pem'
$Domain       = 'glimmer.wang'
$RemoteAppDir = '/home/ubuntu/app'
# ================================================================

# deploy.ps1 位于项目 deploy/ 目录，仓库根目录是上一级
$RepoRoot = Split-Path -Parent $PSScriptRoot
$FrontDir = Join-Path $RepoRoot 'frontend'
$BackDir  = Join-Path $RepoRoot 'backend'

# ssh/scp 公共参数（数组方式传递，避免引号问题）
$Target    = "$User@$Server"
$SshCommon = @('-i', $Key, '-o', 'StrictHostKeyChecking=accept-new', '-o', 'ConnectTimeout=15', $Target)

function Write-Step([string]$msg) { Write-Host "`n==== $msg ====" -ForegroundColor Cyan }
function Write-Ok([string]$msg)   { Write-Host $msg -ForegroundColor Green }
function Write-Warn2([string]$msg){ Write-Host $msg -ForegroundColor Yellow }

# 执行远程命令，非零退出码直接抛错终止
function Invoke-Remote([string]$Cmd) {
    & ssh @SshCommon $Cmd
    if ($LASTEXITCODE -ne 0) {
        throw "远程命令失败 (exit=$LASTEXITCODE)：$Cmd"
    }
}

# 部署前检查：确认服务器已完成 systemd 一次性初始化
function Assert-ServerReady {
    $out = & ssh @SshCommon 'systemctl list-unit-files glimmer-backend.service 2>/dev/null | tail -n +2'
    if ($out -notmatch 'glimmer-backend') {
        throw '服务器尚未安装 glimmer-backend 服务，请先在服务器执行 deploy/server-setup.sh（一次性初始化）'
    }
}

# ==================== 后端部署 ====================
function Deploy-Backend {
    Write-Step '后端 1/5：本地 Maven 打包'
    if (-not $SkipBuild) {
        Push-Location $BackDir
        try {
            & mvn clean package -DskipTests -q
            if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败，请在本地先 mvn package 排查' }
        }
        finally { Pop-Location }
        Write-Ok '    Maven 打包完成'
    } else {
        Write-Warn2 '    -SkipBuild：跳过构建，使用 target/ 下已有 jar'
    }

    # 定位产物 jar（排除 sources/javadoc 等附属包）
    $jar = Get-ChildItem (Join-Path $BackDir 'target\backend-*.jar') -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch 'sources|javadoc' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $jar) { throw '未找到 backend/target/backend-*.jar，请去掉 -SkipBuild 重新执行' }
    Write-Host "    产物：$($jar.Name)（$([math]::Round($jar.Length/1MB,1)) MB）"

    Write-Step '后端 2/5：上传 jar（先传为 app.jar.new，不碰线上文件）'
    & scp @SshCommon $jar.FullName "${Target}:$RemoteAppDir/app.jar.new"
    if ($LASTEXITCODE -ne 0) { throw 'jar 上传失败，请检查网络和密钥' }

    Write-Step '后端 3/5：原子替换 app.jar 并重启服务'
    # 当前版本备份为 app.jar.bak，新版本从 .new 改名，同目录 mv 是原子操作
    Invoke-Remote 'set -e; cd /home/ubuntu/app; test -f app.jar.new; cp -f app.jar app.jar.bak; mv -f app.jar.new app.jar; sudo systemctl restart glimmer-backend'

    Write-Step '后端 4/5：健康检查（systemctl active + actuator UP，最多等 90 秒）'
    $healthy = $false
    for ($i = 1; $i -le 30; $i++) {
        Start-Sleep -Seconds 3
        $out = & ssh @SshCommon 'systemctl is-active glimmer-backend; wget -qO- http://127.0.0.1:8080/actuator/health 2>/dev/null || true'
        if ($out -match 'active' -and $out -match 'UP') { $healthy = $true; break }
        Write-Host "    等待后端启动... 第 $($i*3) 秒"
    }

    Write-Step '后端 5/5：结果处理'
    if (-not $healthy) {
        Write-Warn2 '    新版本未通过健康检查，自动回滚 app.jar.bak ...'
        Invoke-Remote 'set -e; cd /home/ubuntu/app; mv -f app.jar.bak app.jar; sudo systemctl restart glimmer-backend'
        throw '后端部署失败，已回滚旧版本。排查日志：ssh 登录后执行 sudo journalctl -u glimmer-backend -n 100 --no-pager'
    }
    Write-Ok '    后端部署成功，健康检查 UP'
}

# ==================== 前端部署 ====================
function Deploy-Frontend {
    Write-Step '前端 1/4：本地 Vite 构建（npm run build，走 .env.production 相对路径 /api）'
    if (-not $SkipBuild) {
        Push-Location $FrontDir
        try {
            & npm run build
            if ($LASTEXITCODE -ne 0) { throw '前端构建失败，请在本地先 npm run build 排查' }
        }
        finally { Pop-Location }
        Write-Ok '    Vite 构建完成'
    } else {
        Write-Warn2 '    -SkipBuild：跳过构建，使用 dist/ 下已有产物'
    }

    $dist = Join-Path $FrontDir 'dist'
    if (-not (Test-Path (Join-Path $dist 'index.html'))) {
        throw 'frontend/dist/index.html 不存在，请去掉 -SkipBuild 重新执行'
    }

    Write-Step '前端 2/4：上传 dist 到临时目录（不碰线上目录）'
    Invoke-Remote 'rm -rf /home/ubuntu/glimmer-dist'
    # scp -r 本地 dist 为远程 glimmer-dist（目标不存在时即拷贝目录本身）
    & scp -r @SshCommon $dist "${Target}:/home/ubuntu/glimmer-dist"
    if ($LASTEXITCODE -ne 0) { throw 'dist 上传失败，请检查网络和密钥' }
    # 上传完整性校验：index.html 必须存在
    Invoke-Remote 'test -f /home/ubuntu/glimmer-dist/index.html'

    Write-Step '前端 3/4：原子切换站点目录（旧版本保留为 /var/www/glimmer-old）'
    # 同分区 mv 是原子操作，Nginx 不会读到"传了一半"的文件
    Invoke-Remote 'set -e; sudo rm -rf /var/www/glimmer-old; sudo mv /var/www/glimmer /var/www/glimmer-old; sudo mv /home/ubuntu/glimmer-dist /var/www/glimmer'

    Write-Step '前端 4/4：线上 HTTPS 首页校验'
    $check = & ssh @SshCommon "wget -qO- https://$Domain/ | grep -c id=.app."
    if ($check -notmatch '^[1-9]') {
        Write-Warn2 '    线上首页校验失败，自动回滚 ...'
        Invoke-Remote 'set -e; sudo rm -rf /var/www/glimmer; sudo mv /var/www/glimmer-old /var/www/glimmer'
        throw '前端部署失败，已回滚旧版本'
    }
    # 校验通过，清理旧版本
    Invoke-Remote 'rm -rf /home/ubuntu/glimmer-dist; sudo rm -rf /var/www/glimmer-old'
    Write-Ok "    前端部署成功，线上校验通过：https://$Domain/"
}

# ==================== 主流程 ====================
$ErrorActionPreference = 'Stop'

Write-Host 'glimmer 部署开始' -ForegroundColor White
Write-Host "  目标服务器：$Target    部署内容：$App    跳过构建：$([bool]$SkipBuild)"

# 密钥存在性检查
if (-not (Test-Path $Key)) { throw "SSH 密钥不存在：$Key" }

if ($App -in @('backend', 'all')) {
    Assert-ServerReady
    Deploy-Backend
}
if ($App -in @('frontend', 'all')) {
    Deploy-Frontend
}

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host " 全部完成！线上地址：https://$Domain/" -ForegroundColor Green
Write-Host " 后端日志：ssh $Target 'sudo journalctl -u glimmer-backend -f'" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
