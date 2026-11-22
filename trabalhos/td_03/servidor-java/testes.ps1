# Compila e executa os testes automatizados do servidor (sobem servidores reais e os chamam por HTTP).
# powershell -ExecutionPolicy Bypass -File .\testes.ps1           (todos)
# powershell -ExecutionPolicy Bypass -File .\testes.ps1 -Rapido   (sem o teste com processo separado)
param([switch]$Rapido)
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot
& "$PSScriptRoot\compilar.ps1"

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$opcoes = @()
if ($Rapido) { $opcoes += '--rapido' }
& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' '-Dtd03.raiz=.' -cp 'out\classes;out\test-classes' br.ufc.sd.estoque.teste.ExecutarTestes @opcoes
exit $LASTEXITCODE
