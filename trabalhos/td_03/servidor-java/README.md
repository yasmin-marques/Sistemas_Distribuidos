# Servidor (Java)

API REST do estoque veterinário. Só usa o JDK 17+ (servidor HTTP `com.sun.net.httpserver`), sem Maven nem bibliotecas.

```
servidor.ps1 [-Porta 8080] [-Local] [-Monitor 10] [-SemConsole] [-Detalhado]     (servidor.sh --porta 8080 ...)
testes.ps1 [-Rapido]                                                             (testes.sh)
```

Pacotes (`src/main/java/br/ufc/sd/estoque`):

| Pacote | Conteúdo |
|---|---|
| `modelo` | Entidades: `ItemEstoque` ← `Medicamento`/`Vacina`/`Insumo`, `Fornecedor`, `Movimentacao`, `PedidoReposicao`, `ItemPedido`, `Notificacao`, `Sessao` |
| `api` | Contratos dos objetos distribuídos (`EstoqueRemoto`, `AutenticacaoRemota`, `NotificacoesRemotas`, `PedidoReposicaoRemoto`, `AssinaturaRemota`) |
| `servico` | Implementação das regras de negócio (não sabe nada de HTTP) |
| `json` | Representação externa: leitor/gerador JSON e `CodecEntidades` |
| `web` | Camada HTTP: `ServidorHttp`, `Roteador`, `Requisicao`, `Resposta`, `TabelaObjetosDistribuidos`, `ReferenciaRemota` |
| `servidor` | `ServidorEstoque` (main) e um controlador por objeto distribuído |

Comandos do console do servidor: `NOTIFICACAO|ALERTA|ATUALIZACAO <mensagem>` (publica no grupo), `baixar <id> <qtd> [motivo]`,
`repor <id> <qtd>`, `estoque`, `movimentacoes`, `clientes`, `objetos`, `verificar`, `ajuda`, `sair`. Descrição completa dos endereços em [`../doc/api.md`](../doc/api.md).
