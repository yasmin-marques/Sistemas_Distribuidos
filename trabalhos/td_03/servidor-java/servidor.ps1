# Inicia o servidor REST do estoque veterinário (http://localhost:8080/api).
#   powershell -ExecutionPolicy Bypass -File .\servidor.ps1
#   powershell -ExecutionPolicy Bypass -File .\servidor.ps1 -Porta 9000
#   powershell -ExecutionPolicy Bypass -File .\servidor.ps1 -Local        (só aceita a própria máquina)
param(
    [int]$Porta = 8080,
    [int]$Monitor = 10,
    [switch]$Local,
    [switch]$SemConsole,
    [switch]$Detalhado
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
if (-not (Test-Path out\classes)) { & "$PSScriptRoot\compilar.ps1" }

# acentuação correta no console
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

$argumentos = @('--porta', $Porta, '--monitor', $Monitor)
if ($Local) { $argumentos += '--local' }
if ($SemConsole) { $argumentos += '--sem-console' }
if ($Detalhado) { $argumentos += '--detalhado' }

& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out\classes br.ufc.sd.estoque.servidor.ServidorEstoque @argumentos
