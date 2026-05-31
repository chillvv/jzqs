# 微信小程序统一认证 API 测试脚本（PowerShell）
# 使用方法：.\test-auth-api.ps1

$BaseUrl = "http://localhost:8081"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "微信小程序统一认证 API 测试" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# 测试 1: 静默登录（顾客）
Write-Host "测试 1: 静默登录（顾客）" -ForegroundColor Yellow
Write-Host "POST /api/auth/login"
$body1 = @{
    code = "test123"
    userType = "customer"
} | ConvertTo-Json

try {
    $response1 = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $body1 -ContentType "application/json"
    $response1 | ConvertTo-Json -Depth 10
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}
Write-Host ""

# 测试 2: 静默登录（骑手）
Write-Host "测试 2: 静默登录（骑手）" -ForegroundColor Yellow
Write-Host "POST /api/auth/login"
$body2 = @{
    code = "test456"
    userType = "rider"
} | ConvertTo-Json

try {
    $response2 = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $body2 -ContentType "application/json"
    $response2 | ConvertTo-Json -Depth 10
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}
Write-Host ""

# 测试 3: 绑定手机号（顾客）
Write-Host "测试 3: 绑定手机号（顾客）" -ForegroundColor Yellow
Write-Host "POST /api/auth/bind-phone"
$body3 = @{
    code = "13800138000"
    userType = "customer"
} | ConvertTo-Json

try {
    $response3 = Invoke-RestMethod -Uri "$BaseUrl/api/auth/bind-phone" -Method Post -Body $body3 -ContentType "application/json"
    $response3 | ConvertTo-Json -Depth 10
    
    $token = $response3.data.token
    
    if ($token) {
        Write-Host ""
        Write-Host "获取到 Token: $($token.Substring(0, [Math]::Min(20, $token.Length)))..." -ForegroundColor Green
        Write-Host ""
        
        # 测试 4: 验证 token
        Write-Host "测试 4: 验证 token" -ForegroundColor Yellow
        Write-Host "GET /api/auth/verify"
        $headers = @{
            Authorization = "Bearer $token"
        }
        
        try {
            $response4 = Invoke-RestMethod -Uri "$BaseUrl/api/auth/verify" -Method Get -Headers $headers
            $response4 | ConvertTo-Json -Depth 10
        } catch {
            Write-Host "错误: $_" -ForegroundColor Red
        }
        Write-Host ""
        
        # 测试 5: 退出登录
        Write-Host "测试 5: 退出登录" -ForegroundColor Yellow
        Write-Host "POST /api/auth/logout"
        
        try {
            $response5 = Invoke-RestMethod -Uri "$BaseUrl/api/auth/logout" -Method Post -Headers $headers
            $response5 | ConvertTo-Json -Depth 10
        } catch {
            Write-Host "错误: $_" -ForegroundColor Red
        }
    } else {
        Write-Host "未能获取 Token，跳过后续测试" -ForegroundColor Red
    }
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "测试完成" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
