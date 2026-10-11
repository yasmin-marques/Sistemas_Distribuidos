#!/bin/bash
# Compila todo o projeto para ./out
set -e
rm -rf out && mkdir out
javac -encoding UTF-8 -Xlint:all -d out $(find src -name '*.java')
echo "Compilado em ./out"
