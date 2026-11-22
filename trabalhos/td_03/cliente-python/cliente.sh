#!/usr/bin/env bash
# Abre o cliente de terminal Python:  ./cliente.sh [http://localhost:8080]
cd "$(dirname "$0")"
exec python3 -m cliente_estoque "${1:-http://localhost:8080}"
