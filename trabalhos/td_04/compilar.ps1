# Compila o componente Consultas (Java). Na primeira vez, baixa do Maven Central o cliente
# AMQP do RabbitMQ e o SLF4J (dependência dele), conferindo o SHA-256 de cada arquivo.
#   powershell -ExecutionPolicy Bypass -File .\compilar.ps1
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Error 'javac não encontrado. Instale o JDK 17 ou superior e coloque-o no PATH.'
}

$dependencias = @(
    @{ Caminho = 'com/rabbitmq/amqp-client/5.21.0/amqp-client-5.21.0.jar'; Sha256 = 'ff0286ffa02a97e94a91728c4db79bc8d4b91a3479849f443a87e4e8ecb9de74' },
    @{ Caminho = 'org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar';        Sha256 = 'd3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0' },
    @{ Caminho = 'org/slf4j/slf4j-nop/1.7.36/slf4j-nop-1.7.36.jar';        Sha256 = 'c214958b07816cb4412b30c7bdbd4308ffdc6ba2a83767b8f3a9229cbd9274d6' }
)
New-Item -ItemType Directory -Force consultas\lib | Out-Null
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
foreach ($d in $dependencias) {
    $destino = Join-Path 'consultas\lib' (Split-Path $d.Caminho -Leaf)
    if (-not (Test-Path $destino)) {
        Write-Host "Baixando $(Split-Path $d.Caminho -Leaf)..."
        Invoke-WebRequest -UseBasicParsing -Uri "https://repo1.maven.org/maven2/$($d.Caminho)" -OutFile $destino
    }
    $hash = (Get-FileHash -Algorithm SHA256 $destino).Hash.ToLower()
    if ($hash -ne $d.Sha256) {
        Remove-Item $destino
        Write-Error "SHA-256 inesperado em $destino. O arquivo foi apagado; rode o script de novo."
    }
}

if (Test-Path consultas\out) { Remove-Item -Recurse -Force consultas\out }
New-Item -ItemType Directory -Force consultas\out | Out-Null
$fontes = Get-ChildItem -Recurse -Path consultas\src -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 --release 17 -Xlint:all -cp 'consultas\lib\*' -d consultas\out $fontes
if ($LASTEXITCODE -ne 0) { Write-Error 'Falha ao compilar o componente Consultas.' }
Write-Host "Consultas compilado: $($fontes.Count) arquivos em consultas\out."
