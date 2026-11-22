# Abre, cada um na sua janela: o servidor Java, o cliente Python e o cliente web (JavaScript).
# powershell -ExecutionPolicy Bypass -File .\demo.ps1
param([int]$Porta = 8080, [int]$PortaWeb = 5500)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
& "$PSScriptRoot\servidor-java\compilar.ps1"

Start-Process powershell -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSScriptRoot\servidor-java\servidor.ps1`"", '-Porta', $Porta)
Start-Sleep -Seconds 3
Start-Process powershell -ArgumentList @('-NoExit', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSScriptRoot\cliente-python\cliente.ps1`"", '-Url', "http://localhost:$Porta")
Start-Process node -ArgumentList @("`"$PSScriptRoot\cliente-javascript\servir.js`"", $PortaWeb)
Start-Sleep -Seconds 1
Start-Process "http://localhost:$PortaWeb"
Write-Host "Servidor, cliente Python e cliente web abertos. Usuários: veterinario/vet123, recepcao/rec123, estoquista/est123."
