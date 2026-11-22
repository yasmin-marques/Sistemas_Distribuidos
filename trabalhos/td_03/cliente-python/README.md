# Cliente Python

Cliente de terminal. Só biblioteca padrão (Python 3.9+), nada para instalar.

```
cliente.ps1 [-Url http://localhost:8080]        (cliente.sh [url])      ou:  python -m cliente_estoque [url]
```

Digite `ajuda` para ver os comandos (`itens`, `saida 1 2`, `pedido abrir 4`, `demo-valor`, `demo-referencia`,
`protocolo on` para ver as requisições HTTP trocadas...). As notificações do grupo aparecem sozinhas, assim
que chegam, graças a uma segunda thread que fica em long polling.

Testes (precisam de um servidor no ar): `API_URL=http://localhost:8080 ./testes.sh`, ou use
`../testes-integrados.ps1`, que sobe o servidor sozinho.

Organização: `http.py` (requisição-resposta), `modelo.py` (entidades locais com herança), `proxies.py`
(um proxy por objeto distribuído), `ouvinte.py` (thread de notificações), `cli.py` (terminal).
