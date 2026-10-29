# Compila e executa todos os testes automatizados.
#   powershell -ExecutionPolicy Bypass -File .\testes.ps1           (todos)
#   powershell -ExecutionPolicy Bypass -File .\testes.ps1 -Rapido   (sem o teste com processos separados)
param([switch]$Rapido)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
& "$PSScriptRoot\compilar.ps1"

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$opcoes = @()
if ($Rapido) { $opcoes += '--rapido' }
& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp 'out\classes;out\test-classes' br.ufc.sd.estoque.teste.ExecutarTestes @opcoes
exit $LASTEXITCODE
