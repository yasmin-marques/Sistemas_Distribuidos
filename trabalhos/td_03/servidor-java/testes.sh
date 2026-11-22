#!/usr/bin/env bash
# Compila e executa os testes automatizados do servidor. Use --rapido para pular o teste com processo separado.
set -euo pipefail
cd "$(dirname "$0")"
./compilar.sh
exec java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dtd03.raiz=. -cp out/classes:out/test-classes br.ufc.sd.estoque.teste.ExecutarTestes "$@"
