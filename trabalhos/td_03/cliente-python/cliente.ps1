# Abre o cliente de terminal Python.
#   powershell -ExecutionPolicy Bypass -File .\cliente.ps1
#   powershell -ExecutionPolicy Bypass -File .\cliente.ps1 -Url http://192.168.0.10:8080
param([string]$Url = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
$env:PYTHONIOENCODING = 'utf-8'
$py = @('python', 'py', 'python3') | Where-Object { Get-Command $_ -ErrorAction SilentlyContinue } | Select-Object -First 1
if (-not $py) { Write-Error 'Python 3.9+ não encontrado no PATH.' }
& $py -m cliente_estoque $Url
