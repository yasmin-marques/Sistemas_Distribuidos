#!/usr/bin/env bash
# Sobe o servidor e o servidor de arquivos do cliente web; o cliente Python você abre em outro terminal (cliente-python/cliente.sh).
cd "$(dirname "$0")"
servidor-java/compilar.sh || exit 1
node cliente-javascript/servir.js 5500 &
trap 'kill $! 2>/dev/null' EXIT
echo "Cliente web em http://localhost:5500 — em outro terminal: cliente-python/cliente.sh"
servidor-java/servidor.sh "$@"
