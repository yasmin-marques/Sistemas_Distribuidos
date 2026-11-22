#!/usr/bin/env bash
# Teste de ponta a ponta: sobe o servidor Java (processo separado) e roda contra ele
# os testes do cliente Python e do cliente JavaScript.
# ./testes-integrados.sh [porta]
set -uo pipefail
cd "$(dirname "$0")"
PORTA="${1:-18080}"
export API_URL="http://localhost:$PORTA"
LOG="$(mktemp)"

servidor-java/compilar.sh || exit 1
java -Dstdout.encoding=UTF-8 -cp servidor-java/out/classes br.ufc.sd.estoque.servidor.ServidorEstoque \
    --porta "$PORTA" --sem-console --monitor 3600 --local > "$LOG" 2>&1 &
SERVIDOR=$!
trap 'kill $SERVIDOR 2>/dev/null' EXIT
for _ in $(seq 1 40); do curl -fs "$API_URL/api" >/dev/null 2>&1 && break; sleep 0.5; done
curl -fs "$API_URL/api" >/dev/null || { echo "O servidor não subiu:"; cat "$LOG"; exit 1; }
echo "Servidor Java no ar em $API_URL (PID $SERVIDOR)"

falhas=0
echo; echo "=== Cliente Python ==="
(cd cliente-python && python3 -m unittest discover -s testes -t .) || falhas=$((falhas + 1))
echo; echo "=== Cliente JavaScript ==="
(cd cliente-javascript && node --test teste/api.test.js) || falhas=$((falhas + 1))
echo
if [ "$falhas" -eq 0 ]; then echo "Todos os testes de integração passaram."; else echo "Houve falhas ($falhas suíte(s))."; exit 1; fi
