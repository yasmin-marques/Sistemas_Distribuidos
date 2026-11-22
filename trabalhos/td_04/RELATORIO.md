# Relatório técnico — Trabalho 4 (Comunicação Indireta)

**QXD0043 — Sistemas Distribuídos — UFC Quixadá** · Professor: Antonio Rafael Braga
**Dupla 26:** Milena Helen e Yasmin Marques · **Abordagem:** Opção C, Filas de Mensagens (RabbitMQ)

Código e instruções de execução: `trabalhos/td_04/` (README.md).

## 1. Arquitetura

O sistema é a clínica veterinária dos trabalhos anteriores. No TD02, ao registrar uma consulta que usava medicamento ou vacina, a clínica chamava o servidor de Estoque por RMI. Isso exigia o endereço do Estoque no cliente e o Estoque no ar naquele instante.

No TD04 a baixa de estoque passou a ser uma mensagem:

```text
┌──────────────────────┐
│   CONSULTAS (Java)   │
│    Máquina cliente   │
└──────────┬───────────┘
           │
           │ Publica mensagem JSON
           ▼
┌──────────────────────────┐
│         RabbitMQ         │
│   Fila: estoque.baixas   │
│      Máquina servidor    │
└────────────┬─────────────┘
             │
             │ Entrega mensagem
             ▼
┌──────────────────────────┐
│         ESTOQUE          │
│     Python + SQLite      │
│      Máquina servidor    │
└──────────────────────────┘
```

**Fluxo:** Consultas → RabbitMQ → Estoque

O cliente publica a solicitação no RabbitMQ, que a mantém na fila até o Estoque consumir e processar a baixa.

- **Consultas** registra a consulta num CSV local e publica um JSON com `id_mensagem`, `id_consulta`, `id_produto`, `quantidade` e `registrada_em`.
- **RabbitMQ** guarda a mensagem na fila durável, com volume Docker.
- **Estoque** consome a fila, aplica a baixa no SQLite e confirma (ACK) a mensagem.

A Consultas só tem em sua configuração o endereço do broker. Ela não conhece IP, porta nem banco do Estoque.

## 2. Justificativa da escolha

A baixa de estoque é um trabalho com **um único destinatário lógico**, que pode ser feito de forma assíncrona e que **não pode ser perdido** se o Estoque estiver parado. Isso é o que a fila oferece: persistência da mensagem até alguém consumi-la e entrega a um consumidor só.

As outras opções se encaixam pior:

- **Publicar-assinar** serve para difundir um evento a vários interessados. Aqui só o Estoque se interessa, então a abstração de tópico não agrega.
- **Comunicação em grupo** e **espaço de tuplas** teriam de ser forçados para um fluxo que é, na prática, "entregue este pedido àquele serviço".

## 3. Desacoplamento demonstrado

- **Espacial:** o cliente aponta para o broker. Se o Estoque mudar de computador, a Consultas continua igual.
- **Temporal:** com o Estoque parado, a Consultas publica e recebe o *publisher confirm*. A mensagem fica em `messages_ready = 1` até o Estoque voltar, quando a baixa é aplicada (saldo 48 → 43 no roteiro de demonstração).

## 4. Tratamento de falhas

| Falha | Comportamento |
|---|---|
| Estoque offline | A mensagem espera na fila durável e é entregue quando ele volta. |
| Estoque cai depois de gravar e antes do ACK | O broker reentrega. O `id_mensagem` já foi gravado na mesma transação SQLite da baixa, então a segunda entrega vira `DUPLICADA` e não altera o saldo. |
| Saldo insuficiente, produto inexistente | Registrado como `REJEITADA` e confirmado (ACK), para não gerar reentrega infinita. |
| Mensagem malformada | Descartada com `nack` sem reenfileirar. |
| Erro técnico do SQLite | Sem ACK: a mensagem volta para a fila e há nova tentativa. |
| RabbitMQ fora do ar | A Consultas não obtém o confirm, não registra a consulta e termina com código 2. Não há perda silenciosa. |

A publicação usa fila durável, mensagem persistente (`deliveryMode = 2`) e *publisher confirm* com `mandatory`. O consumo usa ACK manual e `prefetch = 1`.

## 5. Análise: overhead e complexidade

**Impacto no sistema.** Uma chamada RMI direta virou: cliente → broker, gravação em disco no broker, confirmação de volta, broker → consumidor e ACK. São mais saltos de rede, mais latência por consulta e uma gravação extra (a da tabela de mensagens processadas, que o Estoque mantém para evitar duplicatas). A CLI abre e autentica uma conexão AMQP a cada comando, o que é o maior custo percebido. Também surgiu algo novo a operar: o broker, seu volume e a fila. Um único nó RabbitMQ é ponto único de falha para consultas que usam produto.

**Semântica mais fraca.** O confirm do broker só diz que a mensagem entrou na fila. O saldo continua desatualizado até o Estoque consumir, e o veterinário não recebe o resultado da baixa. Além disso, CSV e SQLite não compartilham transação: se o CSV falhar depois do confirm, a mensagem já está na fila (`exit 3`).

**Como mitigar.**
- Num serviço de longa duração, reutilizar conexão e canal AMQP em vez de abrir um por registro.
- Mensagens pequenas (só ids e quantidade) e broker perto dos componentes.
- Em volume maior, confirms assíncronos ou em lote e processamento em lotes, mantendo a regra de só confirmar depois do commit.
- Monitorar a profundidade da fila e os consumidores pelo painel.
- Se o resultado da baixa for necessário, o Estoque pode publicar uma resposta em outra fila, correlacionada por `id_consulta`.
- Para fechar a lacuna entre CSV e fila, usar um *outbox* transacional.
- Para eliminar o ponto único de falha, um cluster RabbitMQ com filas replicadas (*quorum queues*).

## 6. Conclusão

A fila retirou o acoplamento direto entre Consultas e Estoque e deu resiliência à queda do receptor, que era o requisito da opção C. O custo foi latência, um componente a mais para operar e consistência apenas eventual entre o registro da consulta e o saldo.