# Cliente JavaScript (web)

Página única sem build e sem dependências. Para abrir:

```
node servir.js [porta]        # http://localhost:5500   (ou abra index.html direto, ou python -m http.server 5500)
```

Na tela de login informe o endereço da API (padrão `http://localhost:8080`). Abas: Estoque (busca, entrada/saída,
novo item enviado por valor), Movimentações, Pedidos (referência remota), Notificações (tempo real), Valor × referência
(demonstração) e Protocolo (mensagens HTTP trocadas). O servidor libera CORS, então a página pode ser servida de
qualquer origem.

`js/api.js` é a biblioteca de acesso à API (funciona no navegador e no Node); `js/app.js` é só a tela.
Testes da biblioteca contra um servidor no ar: `API_URL=http://localhost:8080 npm test`.
