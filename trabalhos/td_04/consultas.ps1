# Executa o componente Consultas (Java). Os argumentos são repassados ao programa:
#   .\consultas.ps1 registrar --animal Rex --especie Cachorro --diagnostico "Otite" --produto 1 --quantidade 2
#   .\consultas.ps1 listar
# Código de saída: 0 = ok, 1 = argumento inválido, 2 = mensagem não confirmada pelo broker.
$ErrorActionPreference = 'Stop'
$raiz = $PSScriptRoot
if (-not (Test-Path "$raiz\consultas\out")) { & "$raiz\compilar.ps1" }

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# erros do Java vão para o stderr e são tratados pelo código de saída, não como exceção do PowerShell
$ErrorActionPreference = 'Continue'

& java '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' "-Dconsultas.base=$raiz" `
    -cp "$raiz\consultas\out;$raiz\consultas\lib\*" br.ufc.sd.consultas.ClienteConsultas @args
exit $LASTEXITCODE
