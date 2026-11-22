# Teste de ponta a ponta: sobe o servidor Java (processo separado) e roda contra ele
# os testes do cliente Python e do cliente JavaScript.
# powershell -ExecutionPolicy Bypass -File .\testes-integrados.ps1 [-Porta 18080]
param([int]$Porta = 18080)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
$env:API_URL = "http://localhost:$Porta"
$env:PYTHONIOENCODING = 'utf-8'

& "$PSScriptRoot\servidor-java\compilar.ps1"

$log = Join-Path $env:TEMP 'td03-servidor.log'
$servidor = Start-Process java -PassThru -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
    -ArgumentList @('-Dstdout.encoding=UTF-8', '-cp', 'servidor-java\out\classes', 'br.ufc.sd.estoque.servidor.ServidorEstoque',
                    '--porta', $Porta, '--sem-console', '--monitor', 3600, '--local')
$falhas = 0
try {
    $no_ar = $false
    for ($i = 0; $i -lt 40 -and -not $no_ar; $i++) {
        try { Invoke-WebRequest -UseBasicParsing "$($env:API_URL)/api" | Out-Null; $no_ar = $true } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $no_ar) { Get-Content $log, "$log.err"; Write-Error 'O servidor não subiu.' }
    Write-Host "Servidor Java no ar em $($env:API_URL) (PID $($servidor.Id))"

    $py = @('python', 'py', 'python3') | Where-Object { Get-Command $_ -ErrorAction SilentlyContinue } | Select-Object -First 1
    if (-not $py) { Write-Error 'Python 3.9+ não encontrado no PATH.' }

    Write-Host "`n=== Cliente Python ==="
    Push-Location cliente-python
    & $py -m unittest discover -s testes -t .
    if ($LASTEXITCODE -ne 0) { $falhas++ }
    Pop-Location

    Write-Host "`n=== Cliente JavaScript ==="
    Push-Location cliente-javascript
    & node --test teste/api.test.js
    if ($LASTEXITCODE -ne 0) { $falhas++ }
    Pop-Location
} finally {
    if (-not $servidor.HasExited) { Stop-Process -Id $servidor.Id -Force }
}
if ($falhas -eq 0) { Write-Host "`nTodos os testes de integração passaram." } else { Write-Host "`nHouve falhas ($falhas suíte(s))."; exit 1 }
