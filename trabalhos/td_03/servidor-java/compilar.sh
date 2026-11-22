#!/usr/bin/env bash
# Compila o servidor do TD03 (código principal e testes) só com o JDK 17+.
set -euo pipefail
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo "javac não encontrado. Instale o JDK 17 ou superior." >&2; exit 1; }
rm -rf out && mkdir -p out/classes out/test-classes
javac -encoding UTF-8 -Xlint:all -Werror --release 17 -d out/classes $(find src/main/java -name '*.java')
javac -encoding UTF-8 -Xlint:all -Werror --release 17 -cp out/classes -d out/test-classes $(find src/test/java -name '*.java')
echo "Compilado em out/classes e out/test-classes."
