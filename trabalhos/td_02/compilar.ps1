# Compila o TD02 (código principal e testes) sem Maven/Gradle: só o JDK.
#   powershell -ExecutionPolicy Bypass -File .\compilar.ps1
$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    Write-Error 'javac não encontrado. Instale o JDK 17 ou superior e coloque-o no PATH.'
}

if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory -Force out\classes, out\test-classes | Out-Null

$principal = Get-ChildItem -Recurse -Path src\main\java -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -Xlint:all -Werror -d out\classes $principal
if ($LASTEXITCODE -ne 0) { Write-Error 'Falha ao compilar o código principal.' }

$testes = Get-ChildItem -Recurse -Path src\test\java -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -Xlint:all -Werror -cp out\classes -d out\test-classes $testes
if ($LASTEXITCODE -ne 0) { Write-Error 'Falha ao compilar os testes.' }

Write-Host "Compilado: $($principal.Count) arquivos em out\classes e $($testes.Count) em out\test-classes."
