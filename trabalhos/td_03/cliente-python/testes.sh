#!/usr/bin/env bash
# Testes do cliente Python contra um servidor já em execução:  API_URL=http://localhost:8080 ./testes.sh
cd "$(dirname "$0")"
exec python3 -m unittest discover -s testes -t . -v
