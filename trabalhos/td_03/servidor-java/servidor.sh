#!/usr/bin/env bash
# Inicia o servidor REST do estoque veterinário (http://localhost:8080/api).
#   ./servidor.sh [--porta 8080] [--local] [--monitor 10] [--sem-console] [--detalhado]
set -euo pipefail
cd "$(dirname "$0")"
[ -d out/classes ] || ./compilar.sh
exec java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp out/classes br.ufc.sd.estoque.servidor.ServidorEstoque "$@"
