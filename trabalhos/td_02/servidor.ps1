# Inicia o servidor RMI do estoque veterinário.
#   powershell -ExecutionPolicy Bypass -File .\servidor.ps1
#   powershell -ExecutionPolicy Bypass -File .\servidor.ps1 -Porta 1099 -HostName 192.168.0.10
param(
    [int]$Porta = 1099,
    [string]$HostName = 'localhost',
    [int]$Monitor = 10,
    [switch]$SemConsole,
    [switch]$Detalhado
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
if (-not (Test-Path out\classes)) { & "$PSScriptRoot\compilar.ps1" }

# acentuação correta no console
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

$argumentos = @('--porta', $Porta, '--host', $HostName, '--monitor', $Monitor)
if ($SemConsole) { $argumentos += '--sem-console' }
if ($Detalhado) { $argumentos += '--detalhado' }

& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out\classes br.ufc.sd.estoque.servidor.ServidorEstoque @argumentos
