# Contrato da API

Base: `http://<servidor>:8080`. Corpo e respostas em JSON (UTF-8). Operações que alteram o estoque exigem
`Authorization: Bearer <token>` (o token vem de `POST /api/autenticacao/sessoes`). `GET /api` descreve o serviço e
lista o endereço de cada objeto distribuído fixo; `GET` no endereço de um objeto devolve a sua descrição e as
operações que ele aceita.

## Objetos distribuídos e operações

| Objeto | Método e caminho | Operação (método remoto) | Passagem |
|---|---|---|---|
| **Autenticacao** `/api/autenticacao` | `POST /sessoes` | `autenticar(usuario, senha)` → `Sessao` (201) | valor |
| | `DELETE /sessoes/atual` | `encerrarSessao(token)` → 204 | — |
| **Estoque** `/api/estoque` | `GET /itens` | `listarItens()` | valor |
| | `GET /itens/{id}` | `buscarItem(id)` | valor |
| | `POST /itens` | `cadastrarItem(item)` → 201 + `Location` | **valor** (argumento e resultado) |
| | `GET /fornecedores` | `listarFornecedores()` | valor |
| | `POST /itens/{id}/entradas` | `registrarEntrada(id, quantidade)` → `Movimentacao` (201) | valor |
| | `POST /itens/{id}/saidas` | `registrarSaida(id, quantidade, motivo)` → `Movimentacao` (201) | valor |
| | `GET /movimentacoes[?item=id]` | `listarMovimentacoes(id)` | valor |
| | `POST /pedidos` | `abrirPedidoReposicao(idFornecedor)` → referência (201 + `Location`) | **referência** (resultado) |
| **PedidoReposicao-xxxxxxxx** `/api/pedidos/{objeto}` | `GET` | `consultar()` | valor |
| | `POST /itens` | `adicionarItem(idItem, quantidade)` | valor |
| | `POST /recebimento` | `confirmarRecebimento()` → lista de `Movimentacao` | valor |
| | `POST /cancelamento` | `cancelar()` | valor |
| **Notificacoes** `/api/notificacoes` | `POST /assinaturas` | `entrarGrupo()` (joinGroup) → referência (201) | **referência** (resultado) |
| | `POST /saida` | `sairGrupo(assinatura)` (leaveGroup) → 204 | **referência** (argumento) |
| | `POST /mensagens` | `publicar(tipo, mensagem)` → `Notificacao` (201) | valor |
| | `GET /membros` | `membros()` | valor |
| **Assinatura-xxxxxxxx** `/api/notificacoes/assinaturas/{objeto}` | `GET` | `usuario()` | valor |
| | `GET /mensagens?espera=ms` | `aguardar(ms)` (long polling, até 30 000 ms) | valor |
| | `DELETE` | `sairGrupo` pelo próprio endereço | — |

Pedidos e assinaturas são criados em tempo de execução. O nome (`PedidoReposicao-ab12cd34`) é aleatório; quem não
recebeu a referência não consegue adivinhar o endereço. Eles saem da tabela quando o ciclo de vida acaba
(pedido concluído ou cancelado continua consultável até o servidor reiniciar; assinatura que saiu ou expirou passa a responder 404).

## Referência remota

```json
{ "objeto": "PedidoReposicao-ab12cd34", "interface": "PedidoReposicaoRemoto",
  "href": "http://localhost:8080/api/pedidos/PedidoReposicao-ab12cd34" }
```

Vai no corpo da resposta (e no cabeçalho `Location`) quando uma operação devolve um objeto remoto, e é reenviada
como argumento (`POST /api/notificacoes/saida` com `{"assinatura": <referência>}`).

## Representação das entidades

Item (campo `classe` discriminando a subclasse):

```json
{ "classe": "MEDICAMENTO", "id": 1, "nome": "Amoxicilina 250mg",
  "fornecedor": { "id": 1, "nome": "VetPharma", "cnpj": "11.111.111/0001-11", "telefone": "(85) 3333-1001" },
  "quantidade": 50, "estoqueMinimo": 10, "precoUnitario": 35.9, "validade": "2030-06-30",
  "principioAtivo": "Amoxicilina", "concentracao": "250 mg", "controlado": false }
```

`VACINA` acrescenta `doencaAlvo` e `dosesPorFrasco`; `INSUMO` acrescenta `unidadeMedida` e `esteril`. Datas em
`AAAA-MM-DD`; instantes em milissegundos desde 1970.

## Erros

Status HTTP + corpo `{"erro": {"codigo": "...", "mensagem": "..."}}`.

| Status | Código | Quando |
|---|---|---|
| 400 | `REQUISICAO_INVALIDA` | JSON malformado, corpo maior que 1 MB |
| 400 | `ARGUMENTO_INVALIDO` | campo ausente/inválido, referência que não aponta para um objeto ativo |
| 401 | `NAO_AUTENTICADO` | token ausente, inválido ou encerrado; credenciais erradas |
| 404 | `OBJETO_INEXISTENTE` | caminho, item ou objeto remoto que não existe (ou já foi encerrado) |
| 405 | `METODO_INEXISTENTE` | método HTTP não aceito nesse endereço (cabeçalho `Allow`) |
| 409 | `OPERACAO_NEGADA` | regra de negócio (estoque insuficiente, controlado sem motivo, item vencido, pedido já encerrado, nome duplicado) |
| 500 | `ERRO_INTERNO` | falha inesperada (a mensagem não expõe detalhes) |

Toda resposta traz `X-Request-Id` (o cliente pode mandar o seu) e `Access-Control-Allow-Origin: *`.
