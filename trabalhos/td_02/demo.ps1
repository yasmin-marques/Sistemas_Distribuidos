# Abre o servidor e N clientes, cada um na sua janela do PowerShell (processos independentes).
#   powershell -ExecutionPolicy Bypass -File .\demo.ps1
#   powershell -ExecutionPolicy Bypass -File .\demo.ps1 -Clientes 3 -Porta 1099
param(
    [int]$Clientes = 2,
    [int]$Porta = 1099
)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
& "$PSScriptRoot\compilar.ps1"

Start-Process powershell -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-File',
    "`"$PSScriptRoot\servidor.ps1`"", '-Porta', $Porta)
Start-Sleep -Seconds 3   # tempo para o registro RMI subir

for ($i = 1; $i -le $Clientes; $i++) {
    Start-Process powershell -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-File',
        "`"$PSScriptRoot\cliente.ps1`"", '-Porta', $Porta)
}
Write-Host "Servidor e $Clientes cliente(s) abertos. Usuários: veterinario/vet123, recepcao/rec123, estoquista/est123."
