#!/bin/bash
# Compila todas as classes do projeto em ./build
set -e
find src -name "*.java" > .sources.txt
javac -d build @.sources.txt
rm .sources.txt
echo "Compilado com sucesso em ./build"
echo "Execute com: java -cp build <pacote.completo.Classe> [args]"
