# Inicia um cliente de terminal (abra quantos quiser, cada um no seu terminal).
#   powershell -ExecutionPolicy Bypass -File .\cliente.ps1
#   powershell -ExecutionPolicy Bypass -File .\cliente.ps1 -Servidor 192.168.0.10 -Porta 1099
param(
    [string]$Servidor = 'localhost',
    [int]$Porta = 1099
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
if (-not (Test-Path out\classes)) { & "$PSScriptRoot\compilar.ps1" }

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp out\classes br.ufc.sd.estoque.cliente.ClienteEstoque $Servidor $Porta
