# Relatório técnico - Trabalho 2 (RPC/RMI)

**QXD0043 - Sistemas Distribuídos - UFC Quixadá**\
**Professor:** Antonio Rafael Braga\
**Dupla 26:** Milena Helen e Yasmin Marques\
**Serviço:** estoque da clínica veterinária

Instruções de compilação e execução: [README.md](README.md).

---

## 1. Introdução e objetivos

O Trabalho 1 definiu o serviço "Clínica Veterinária". Na questão 4, ele implementou uma
comunicação **multicast**: os clientes se autenticavam por TCP, entravam no grupo UDP
`230.0.0.1:4446` e recebiam em tempo real mensagens `NOTIFICACAO`, `ALERTA` e `ATUALIZACAO`
enviadas pelo servidor, inclusive os alertas automáticos de um monitor de estoque.

O Trabalho 2 pede a reimplementação dessa questão:

1. em comunicação **cliente-servidor** via **RPC ou RMI**, sem criar sockets;
2. organizada no **protocolo requisição-resposta** da seção 5.2 do livro, com os métodos
   `doOperation`, `getRequest` e `sendReply` e mensagens de cinco campos;
3. com no mínimo 4 entidades, 2 agregações, 2 heranças e 4 métodos remotos;
4. usando passagem **por referência** (objetos remotos) e **por valor** (objetos locais), esta
   última com **representação externa de dados**.

A tecnologia escolhida foi **Java RMI**, por ser Java como o TD01, fazer parte do JDK (sem dependências) e
oferecer o registro de nomes e as referências remotas que o protocolo precisa.

## 2. Descrição do serviço remoto

O servidor mantém o estoque da clínica:

- **itens:** medicamentos, vacinas e insumos;
- **fornecedores;**
- **histórico de movimentações;**
- **pedidos de reposição;**
- **grupo de notificações.**

Os clientes, que representam veterinário, recepção e estoquista, podem:

- autenticar-se;
- consultar o estoque e os fornecedores;
- cadastrar itens;
- registrar entradas e saídas;
- abrir, montar, confirmar e cancelar pedidos de reposição;
- entrar e sair do grupo de notificações;
- publicar mensagens para o grupo.

O servidor envia ao grupo:

- `ATUALIZACAO` a cada movimentação;
- `NOTIFICACAO` a cada cadastro, abertura ou cancelamento de pedido;
- `ALERTA` quando o monitor detecta estoque baixo, validade próxima ou item vencido;
- as mensagens digitadas no console do servidor, como no TD01.

## 3. Arquitetura adotada

A arquitetura segue o modelo de invocação remota do livro (cap. 5): proxy → módulo de
comunicação → módulo de referência remota → despachante → esqueleto → servo. A camada de
transporte é o Java RMI.

```
CLIENTE                                               SERVIDOR
ClienteEstoque ─┐                                     ServidorEstoque (montagem, console)
OuvinteNotif. ──┤ chamadas de método                  Registro RMI ("ClinicaVeterinaria")
                ▼                                            │
          Proxies (EstoqueProxy, ...)                        ▼
   (empacotam argumentos em JSON)                     ProtocoloServidor  ◀── exportado pelo RMI
                ▼                                       entregar(byte[]) → fila
          ProtocoloCliente.doOperation  ═══ RMI ═══▶    getRequest()  /  sendReply()
                                                             ▼
                                                      Despachante (laço + threads)
                                                             ▼
                                                      TabelaObjetosRemotos → Esqueleto
                                                             ▼
                                                      Servos: AutenticacaoServico, EstoqueServico,
                                                      PedidoReposicaoServico, CentralNotificacoes,
                                                      Assinatura;  MonitorEstoque (thread)
```

| Pacote | Papel (livro) | Classes |
|---|---|---|
| `protocolo` | módulo de comunicação | `ProtocoloCliente`, `ProtocoloServidor`, `PortaServidora`, `Mensagem`, `CodecMensagem`, `CargaResposta`, `RemoteObjectRef` |
| `servidor` | módulo de referência remota, despachante e esqueletos | `TabelaObjetosRemotos`, `Despachante`, `Esqueleto*`, `ServidorEstoque`, `ConsoleServidor` |
| `cliente` | proxies e aplicação cliente | `ProxyBase`, `*Proxy`, `ConexaoClinica`, `OuvinteNotificacoes`, `ClienteEstoque` |
| `api` | interfaces remotas, compartilhadas por proxies e servos | `EstoqueRemoto`, `PedidoReposicaoRemoto`, `NotificacoesRemotas`, `AssinaturaRemota`, `AutenticacaoRemota` |
| `servico` | servos (regras de negócio) | `EstoqueServico`, `PedidoReposicaoServico`, `CentralNotificacoes`, `Assinatura`, `AutenticacaoServico`, `MonitorEstoque` |
| `modelo` | entidades | ver seção 8 |
| `json` | representação externa | `Json`, `ObjetoJson`, `CodecEntidades` |

Decisões de projeto:

- **Uma única interface RMI** (`PortaServidora.entregar(byte[]) : byte[]`). Se cada serviço fosse uma
  interface RMI comum, o RMI faria sozinho todo o trabalho de requisição-resposta, e `doOperation`,
  `getRequest` e `sendReply` seriam decorativos. Com uma porta que só transporta bytes, o RMI
  cuida da conexão, e o empacotamento, a seleção do objeto, o despacho e a resposta ficam a cargo
  do protocolo implementado no trabalho.
- **Proxies e servos implementam a mesma interface** (por exemplo, `EstoqueRemoto`). O cliente chama métodos
  normais, e os servos podem ser testados sem rede.
- **Baixo acoplamento:** `EstoqueServico` não conhece a central de notificações, só a interface
  `Publicador`; o monitor é avisado por um observador (`setObservador`); a tabela de objetos é
  avisada do fim das assinaturas por um *callback* (`setAoEncerrar`).
- **Erros como códigos:** exceções de negócio viram erros tipados na resposta
  (`IllegalArgumentException` → `ARGUMENTO_INVALIDO`, `IllegalStateException` → `OPERACAO_NEGADA` etc.)
  e voltam a ser exceções (`ExcecaoRemota`) no cliente. Falhas de rede viram
  `FalhaComunicacaoException`.

## 4. Relação entre TD01 e TD02

| Aspecto | TD01 - questão 4 | TD02 |
|---|---|---|
| Autenticação | TCP (`ServerSocket` porta 5002), JSON por linha | método remoto `Autenticacao.autenticar`, que devolve uma `Sessao` com token |
| Entrar no grupo | `MulticastSocket.joinGroup(230.0.0.1)` | `Notificacoes.entrarGrupo(token)` devolve a **referência** de uma `Assinatura` |
| Sair do grupo | `leaveGroup` | `Notificacoes.sairGrupo(assinatura)`, com a referência como argumento |
| Envio das mensagens | datagrama UDP para o grupo, pool de 4 threads | mensagem colocada na fila de cada assinatura, sob uma trava (ordem total) |
| Recepção | thread `OuvinteMulticast` bloqueada em `receive` | thread `OuvinteNotificacoes` bloqueada em `aguardar` (*long polling*) |
| Formato | `{"tipo","mensagem","timestamp"}` | o mesmo, mais `id` (número de sequência) |
| Monitor de estoque | a cada 10 s; estoque baixo e vencido; uma vez por problema | igual, mais "validade próxima" (antecedência por subclasse) e verificação imediata após cada alteração |
| Operações dos clientes | nenhuma (só escutavam) | 20 métodos remotos: estoque, pedidos, grupo |
| Console do servidor | `NOTIFICACAO/ALERTA/ATUALIZACAO <msg>`, `baixar`, `repor`, `estoque`, `clientes` | os mesmos, mais `movimentacoes`, `objetos`, `verificar` |
| Usuários | `veterinario`, `recepcao`, `estoquista` | os mesmos |
| Entidades | `Medicamento`, `Estoque` (+ `Animal`... de outras questões) | `ItemEstoque` com 3 subclasses, `Fornecedor`, `Estoque`, `Movimentacao`, `PedidoReposicao`, `ItemPedido`, `Notificacao`, `Sessao` |
| Rede | escolha de interface multicast, TTL, firewall UDP | uma porta TCP (registro e objetos na mesma porta), funciona sem multicast na rede |

**O que foi preservado do TD01:** os quatro primeiros medicamentos da carga inicial; as regras de
baixa (saldo insuficiente, item vencido); o limite de estoque baixo; o envio de cada alerta uma vez
por problema; os três tipos de mensagem e seu formato JSON; as duas threads do cliente e os
comandos dos consoles.

**Independência:** o TD02 está em `trabalhos/td_02`, usa outro pacote (`br.ufc.sd.estoque`) e
não importa nada do TD01. O teste `TesteRestricoes` confere isso (e que as classes do TD01 não
estão no classpath). Os arquivos do TD01 não foram alterados.

## 5. Implementação com Java RMI

- O servidor cria o registro com `LocateRegistry.createRegistry(porta)` e exporta o
  `ProtocoloServidor` com `UnicastRemoteObject.exportObject(this, porta)`, na **mesma porta** do
  registro. Depois registra o stub com `rebind("ClinicaVeterinaria", stub)` (`ServidorEstoque.iniciar`).
- O cliente localiza a porta servidora com `LocateRegistry.getRegistry(host, porta).lookup(servico)`
  e guarda o stub em cache (`ProtocoloCliente.localizar`). Se o servidor for reiniciado, o stub antigo
  gera `ConnectException`/`NoSuchObjectException`; o cliente o descarta, localiza de novo e repete
  **uma vez**. Isso é seguro porque, nesses dois casos, a requisição não chegou a ser executada.
- O stub é gerado dinamicamente pelo RMI (proxy dinâmico, sem `rmic`), o que o teste verifica com
  `Proxy.isProxyClass`.
- Como a interface remota só transporta `byte[]`, o RMI nunca desserializa objetos da aplicação. Isso
  elimina o risco de desserialização insegura do Java: os dados da aplicação chegam como JSON e são
  validados campo a campo.
- `java.rmi.server.hostname` recebe o valor de `--host`, que também é o endereço gravado nas
  referências remotas devolvidas aos clientes.
- O endereço do cliente vem de `RemoteServer.getClientHost()`, chamado dentro da thread do RMI.

## 6. Protocolo requisição-resposta

### Cliente: `ProtocoloCliente.doOperation(RemoteObjectRef o, String methodId, byte[] arguments)`

1. gera um `requestId` novo (`AtomicInteger`);
2. monta `Mensagem(REQUEST, requestId, o, methodId, arguments)` e empacota em JSON (`CodecMensagem.empacotar`);
3. localiza a `PortaServidora` de `o` e chama `entregar(bytes)` via RMI (a thread fica esperando);
4. desempacota a resposta e confere se `messageType == REPLY` e se o `requestId` é o mesmo;
5. devolve `arguments` da resposta, que o proxy interpreta (`CargaResposta.ler`): o resultado, ou `ExcecaoRemota`.

### Servidor: `getRequest` e `sendReply` (`ProtocoloServidor`)

- `entregar(bytes)`, executado na thread do RMI:
  1. valida o tamanho (máximo de 1 MiB);
  2. obtém o IP do cliente;
  3. reserva um **canal de retorno** (número único);
  4. coloca `RequisicaoRecebida(bytes, clientHost, canal)` numa fila;
  5. espera a resposta desse canal (até 60 s).
- `getRequest()`: tira a próxima requisição da fila (bloqueante).
- `sendReply(reply, clientHost, clientPort)`:
  - entrega a resposta à chamada que espera no canal `clientPort`;
  - recusa (`IllegalArgumentException`) se `clientHost` não for o IP de quem fez a requisição;
  - devolve `false` se o cliente já desistiu.

### Despachante (`Despachante`): o laço da figura do enunciado

```java
while (ativo) {
    RequisicaoRecebida req = protocolo.getRequest();     // getRequest
    trabalhadores.execute(() -> {
        byte[] resposta = processar(req);                 // seleciona o objeto e executa o método
        protocolo.sendReply(resposta, req.clientHost(), req.clientPort());   // sendReply
    });
}
```

`processar` faz o seguinte:

1. desempacota a mensagem;
2. exige `messageType == 0`;
3. seleciona o esqueleto pela referência (`TabelaObjetosRemotos.localizar`), conferindo a interface;
4. executa `esqueleto.invocar(methodId, argumentos)`;
5. empacota a resposta com o mesmo `requestId`, `objectReference` e `methodId`.

Qualquer falha vira uma resposta de erro: o despachante nunca deixa o cliente sem resposta.

As requisições são executadas por um *pool* de até 256 threads. Assim, clientes diferentes são
atendidos em paralelo, e uma chamada `aguardar` que fica retida não bloqueia as demais.

### Adaptação das assinaturas

| Livro | Implementado | Justificativa |
|---|---|---|
| `doOperation(RemoteObjectRef, int methodId, byte[])` | `methodId` é `String` | o enunciado permite nomes |
| `byte[] getRequest()` | `RequisicaoRecebida getRequest()` | no UDP o endereço de retorno vem do datagrama; aqui vem junto com os bytes |
| `void sendReply(byte[], InetAddress, int)` | `boolean sendReply(byte[], InetAddress, int)` | `clientPort` é o canal de retorno, porque o RMI não expõe a porta TCP do cliente; o `boolean` informa se o cliente ainda aguardava |

**Semântica de invocação:** a chamada RMI é sobre TCP (entrega confiável e ordenada), e o cliente só
repete uma requisição quando tem certeza de que ela não foi entregue. Por isso, cada operação é
executada **no máximo uma vez** (*at-most-once*), sem precisar de histórico de respostas no
servidor. Se o servidor cair durante a execução, o cliente recebe `FalhaComunicacaoException` e
não repete a operação.

## 7. Estrutura das mensagens

| Campo | Tipo | Conteúdo |
|---|---|---|
| `messageType` | int | `0` = Request, `1` = Reply (`TipoMensagem`) |
| `requestId` | int | identificador da requisição, crescente por cliente; a resposta repete o valor |
| `objectReference` | `RemoteObjectRef` | `{host, porta, servico, objeto, interface}`: onde está a porta servidora, qual objeto da tabela e qual interface |
| `methodId` | String | nome do método (`"registrarSaida"`) |
| `arguments` | bytes (JSON) | na requisição, os argumentos nomeados; na resposta, `{"status":"OK","resultado":...}` ou `{"status":"ERRO","erro":{"codigo","mensagem"}}` |

Exemplo real capturado com `protocolo on` no cliente:

```
--> {"messageType":0,"requestId":10,"objectReference":{"host":"localhost","porta":15099,"servico":"ClinicaVeterinaria","objeto":"Estoque","interface":"EstoqueRemoto"},"methodId":"buscarItem","arguments":{"idItem":1}}
<-- {"messageType":1,"requestId":10,"objectReference":{"host":"localhost","porta":15099,"servico":"ClinicaVeterinaria","objeto":"Estoque","interface":"EstoqueRemoto"},"methodId":"buscarItem","arguments":{"status":"OK","resultado":{"classe":"MEDICAMENTO","id":1,"nome":"Amoxicilina 250mg","fornecedor":{"id":1,"nome":"VetPharma","cnpj":"11.111.111/0001-11","telefone":"(85) 3333-1001"},"quantidade":50,"estoqueMinimo":10,"precoUnitario":35.9,"validade":"2030-06-30","principioAtivo":"Amoxicilina","concentracao":"250 mg","controlado":false}}}
```

Como os argumentos são sempre JSON, o campo `arguments` é embutido como JSON (e não como base64),
o que deixa o tráfego legível. `CodecMensagem` converte nos dois sentidos, e `Mensagem` guarda o
campo como `byte[]`, como na figura.

## 8. Entidades e relacionamentos

```
                    ┌──────────────┐ 1   tem-um   1 ┌────────────┐
                    │ ItemEstoque  │───────────────▶│ Fornecedor │◀─────────────┐
                    │ (abstrata)   │                └────────────┘              │ tem-um
                    └──────▲───────┘                                            │
          é-um ┌───────────┼───────────┐ é-um                         ┌─────────┴────────┐
        ┌──────┴─────┐ ┌───┴────┐ ┌────┴───┐                          │ PedidoReposicao  │
        │Medicamento │ │ Vacina │ │ Insumo │                          │                  │
        └────────────┘ └────────┘ └────────┘                          └─────────┬────────┘
                    ▲                                                           │ tem-vários
     ┌─────────┐    │ tem-vários                                        ┌───────▼──────┐
     │ Estoque │────┘                                                   │  ItemPedido  │
     └─────────┘                                                        └──────────────┘
     Movimentacao (histórico)   Notificacao (grupo)   Sessao (autenticação)
```

| Requisito | Implementação |
|---|---|
| **≥ 4 entidades** | 11: `ItemEstoque`, `Medicamento`, `Vacina`, `Insumo`, `Fornecedor`, `Estoque`, `Movimentacao`, `PedidoReposicao`, `ItemPedido`, `Notificacao`, `Sessao` |
| **≥ 2 agregações ("tem-um")** | `ItemEstoque` tem um `Fornecedor`; `PedidoReposicao` tem um `Fornecedor`; `Estoque` tem vários `ItemEstoque`; `PedidoReposicao` tem vários `ItemPedido` |
| **≥ 2 heranças ("é-um")** | `Medicamento`, `Vacina` e `Insumo` são `ItemEstoque` |

As relações têm comportamento, não são só estrutura:

- Cada subclasse define a própria validação (`validar()`).
  - **Medicamento:** exige princípio ativo e validade.
  - **Vacina:** exige doença-alvo, doses por frasco e validade.
  - **Insumo:** exige unidade de medida (a validade é opcional).
- Cada subclasse define a antecedência do aviso de validade (`getDiasAvisoValidade()`): 30, 60 e 15 dias.
- Só o medicamento controlado exige justificativa na saída (`exigeJustificativaSaida()`).
- Na agregação com `Fornecedor`:
  - ao cadastrar, o servidor substitui o fornecedor enviado pelo cliente pelo fornecedor cadastrado com aquele id;
  - num pedido só entram itens do mesmo fornecedor;
  - o fornecedor existe independentemente dos itens e dos pedidos.
- A herança atravessa a rede: o JSON leva o discriminador `"classe"`, e o `CodecEntidades` recria a subclasse certa.

## 9. Métodos remotos

20 métodos em 5 interfaces remotas (o mínimo exigido é 4):

| Interface | Métodos |
|---|---|
| `AutenticacaoRemota` | `autenticar`, `encerrarSessao` |
| `EstoqueRemoto` | `listarItens`, `buscarItem`, `listarFornecedores`, `cadastrarItem`, `registrarEntrada`, `registrarSaida`, `listarMovimentacoes`, `abrirPedidoReposicao` |
| `PedidoReposicaoRemoto` | `consultar`, `adicionarItem`, `confirmarRecebimento`, `cancelar` |
| `NotificacoesRemotas` | `entrarGrupo`, `sairGrupo`, `publicar`, `membros` |
| `AssinaturaRemota` | `aguardar`, `usuario` |

As operações que alteram dados exigem o token da sessão; o responsável pela movimentação vem da sessão,
nunca do cliente.

## 10. Passagem por valor e por referência

**Por valor (objetos locais executados no servidor).** Toda entidade é empacotada em JSON por
`CodecEntidades` e reconstruída do outro lado. São objetos independentes, e o servidor sempre trabalha
sobre cópias (`ItemEstoque.copiar()`). O exemplo principal é `cadastrarItem(token, item)`:

1. o cliente cria um `Medicamento`, `Vacina` ou `Insumo` local;
2. o proxy o converte em JSON (`EstoqueProxy.cadastrarItem`);
3. o esqueleto o reconstrói no servidor (`EsqueletoEstoque`, caso `cadastrarItem`);
4. o servidor valida, gera o id e guarda a cópia;
5. o servidor devolve outra cópia;
6. o objeto original do cliente continua com `id = 0`.

O mesmo vale para os resultados: alterar o `ItemEstoque` devolvido por `buscarItem` não altera o servidor.

**Por referência (objetos remotos).** Interfaces que estendem o marcador `ObjetoRemoto` (papel
análogo ao de `java.rmi.Remote`) são passadas por referência:

- **Como resultado:** quando um servo devolve um `PedidoReposicaoServico` ou uma `Assinatura`, o esqueleto
  chama `TabelaObjetosRemotos.exportar`. Esse método registra o objeto com um nome aleatório
  (`PedidoReposicao-5e63c6c5`) e devolve só a `RemoteObjectRef`. O proxy do cliente
  (`EstoqueProxy.abrirPedidoReposicao`) transforma a referência num `PedidoReposicaoProxy`. Todas as chamadas
  feitas por ele executam no objeto que ficou no servidor.
- **Como argumento:** `sairGrupo(assinatura)` envia a referência (`ProxyBase.referenciaDe`); o
  esqueleto a resolve no objeto local (`TabelaObjetosRemotos.resolver`). Uma referência forjada ou de
  outro tipo é recusada.
- **Identidade compartilhada:** dois clientes com a mesma referência atuam sobre o mesmo pedido
  (teste "duas referências ao mesmo pedido" e comando `demo-referencia`).
- **Ciclo de vida:** o objeto entra na tabela quando a referência sai pela primeira vez. Ele sai da tabela
  no `sairGrupo` ou quando o *lease* expira (assinatura sem contato por mais de 30 s), e a partir daí a
  referência gera `OBJETO_INEXISTENTE`. É a mesma ideia do coletor de lixo distribuído do RMI.
- **No nível do RMI:** o stub de `PortaServidora` obtido no registro é uma referência remota Java RMI.

**Requisito "a passagem por valor deve usar a representação externa de dados":** atendido. Nenhuma entidade
trafega como objeto serializado do Java: só JSON.

## 11. Representação externa de dados: por que JSON

| Critério | Protocol Buffers | XML | JSON (escolhido) |
|---|---|---|---|
| Dependências | `protobuf-java` + compilador `protoc` | JAXB foi removido do JDK 11+ (seria preciso adicionar) | nenhuma: leitor/escritor próprio de cerca de 230 linhas |
| Legibilidade para demonstrar | binário | legível, verboso | legível e compacto |
| Polimorfismo | `oneof` | atributos/tipos | campo discriminador `"classe"` |
| Continuidade com o TD01 | não | não | o TD01 já usava JSON nas notificações |
| Tamanho/desempenho | o melhor | o pior | intermediário, suficiente aqui |

O projeto não usa Maven/Gradle (como o TD01) e precisa rodar no Windows só com o JDK. Por isso,
adotar Protocol Buffers obrigaria a distribuir *jars* e o `protoc`, ou a exigir instalação. JSON
cumpre a exigência (aceito pelo enunciado), deixa as mensagens legíveis no console (`protocolo on`)
e mantém o formato `{"tipo","mensagem","timestamp"}` do TD01.

Robustez do leitor JSON:

- rejeita JSON malformado e campos repetidos;
- limita o aninhamento a 64 níveis, para que uma mensagem maliciosa não estoure a pilha;
- `ObjetoJson` valida tipo e presença de cada argumento;
- datas usam ISO-8601;
- instantes usam milissegundos desde 1970.

## 12. Estratégia de notificações

As alternativas consideradas para levar mensagens do servidor aos clientes, sem sockets, foram:

1. **Callback RMI:** o cliente exporta um objeto remoto e o servidor o chama. Isso inverte o papel cliente/servidor
   (o servidor passa a iniciar requisições), exige que cada cliente abra uma porta (bloqueada por firewall/NAT)
   e foge do fluxo `doOperation → getRequest → sendReply`.
2. **Consulta periódica (polling):** simples, mas com atraso de até um período e tráfego inútil.
3. **Long polling (escolhida):** o cliente pergunta "há mensagens?" (`Assinatura.aguardar(10000)`) e o
   servidor **segura a resposta** até chegar uma mensagem ou passar o tempo.

O long polling mantém o modelo requisição-resposta (o cliente sempre inicia), entrega em tempo real
(o teste mede menos de 3 s, e na prática alguns milissegundos) e funciona com uma única porta no servidor.

Funcionamento (`CentralNotificacoes`, `Assinatura`, `OuvinteNotificacoes`):

- `entrarGrupo` cria uma `Assinatura` (fila do membro) e devolve sua referência.
- `publicar(tipo, mensagem)` atribui um número de sequência e coloca a mensagem na fila de todas as
  assinaturas, **sob uma trava**. Assim, todos os membros recebem as mensagens na mesma ordem, mesmo com
  console, monitor e clientes publicando ao mesmo tempo (ordem total verificada com 5 publicadores e 10 membros).
- `aguardar` espera com `wait/notifyAll` e devolve tudo o que estiver pendente.
- No cliente, a thread `thread-escuta-notificacoes` (`OuvinteNotificacoes`) mostra as mensagens na hora,
  enquanto a thread principal atende o usuário (duas threads, como no TD01).
- Como no multicast, quem não está no grupo não recebe, e quem entra depois não recebe as mensagens antigas.
- A fila de cada membro guarda no máximo 500 mensagens (as mais antigas são descartadas).
- Uma assinatura sem contato por mais de 30 s é encerrada (*lease*). Isso evita acúmulo de mensagens para
  clientes que caíram sem chamar `sairGrupo`.

## 13. Testes realizados

Testes automatizados (`src/test/java`), executados com `.\testes.ps1`. São 45 casos em 11 grupos:

| Requisito de teste | Caso(s) |
|---|---|
| Inicialização do servidor RMI | "Servidor inicia, registra a porta servidora no registro RMI..." |
| Registro e localização | idem: `reg.list()`, `lookup`, stub dinâmico; "Servidor reiniciado na mesma porta" |
| Comunicação cliente-servidor | todos os casos de integração (RMI real em loopback) |
| Protocolo requisição-resposta | "entregar → getRequest → sendReply", "20 chamadas simultâneas...", "doOperation: cada requisição tem requestId novo..." |
| Os métodos remotos | grupo "Métodos remotos" (autenticação, consultas, cadastro, movimentações, pedido) e "Notificações" |
| Passagem por valor | "Por valor (resultado)", "Por valor (argumento)" |
| Passagem por referência | "Por referência (resultado)", "Por referência (argumento)", "Referências forjadas..." |
| Serialização/desserialização | grupo "Representação externa" (8 casos) e "Mensagens do protocolo" (4 casos) |
| Notificações | "Dois clientes...", "leaveGroup...", "Operações de estoque geram...", "Entrega em tempo real", "Monitor...", "Lease..." |
| Múltiplos clientes | concorrência (30, 8 e 15 clientes) e "Processos independentes" (3 JVMs de cliente + 1 de servidor) |
| Concorrência e consistência | "30 clientes disputam 12 caixas...", "8 clientes... saldo final bate com o histórico", "ordem total" |
| Erros e requisições inválidas | "Bytes malformados...", "Objeto inexistente, método inexistente...", regras de negócio, autenticação |
| Ausência de sockets | varredura do código-fonte e do *constant pool* de todos os `.class` |
| Independência do TD01 | nenhum `import clinica.*`; classes do TD01 ausentes do classpath |

Além disso, foram feitas execuções manuais do servidor e do cliente pelos scripts `.ps1`, com
entrada digitada e redirecionada, cobrindo login (inclusive senha errada), todos os comandos, o
`cadastrar` interativo com entrada inválida, os comandos do console do servidor, a porta já em uso
e o servidor fora do ar.

## 14. Resultados observados

Execução em Windows 11, JDK Temurin 21.0.5 (saída real de `ExecutarTestes`):

```
== Representação externa de dados (JSON) ==            8 OK
== Mensagens do protocolo requisição-resposta ==       4 OK
== Protocolo: getRequest / sendReply na porta servidora ==  4 OK
== Servidor RMI: inicialização, registro e localização ==   3 OK
== Protocolo requisição-resposta sobre RMI ==          3 OK
== Métodos remotos ==                                  5 OK
== Passagem por valor e por referência ==              5 OK
== Notificações (substituto do multicast do TD01) ==   6 OK
== Vários clientes e concorrência ==                   3 OK
== Processos independentes: 1 servidor e 3 clientes em JVMs separadas ==  1 OK (3,2 s)
== Restrições do enunciado ==                          3 OK

Resultado: 45 passou, 0 falhou (5,9 s).
```

A suíte foi executada cinco vezes seguidas, todas com 45/45, sem falhas intermitentes. O código também
compila com `javac --release 17`.

Pontos observados durante o desenvolvimento:

- A primeira execução teve 3 falhas, todas **nos próprios testes**, e foram corrigidas:
  - o utilitário de espera descartava notificações que chegavam no mesmo lote;
  - o `javac` juntava as constantes de texto do teste de bytecode, que acabava acusando a si mesmo.
- Na disputa de 30 clientes por 12 unidades, exatamente 12 saídas foram aceitas, 18 recusadas com
  `OPERACAO_NEGADA`, e o saldo final foi 0.
- Com 8 clientes alternando entradas e saídas, o saldo final (`200 + 208 − 288 = 120`) bateu com o
  histórico, e o saldo registrado em cada movimentação foi consistente.
- No teste entre processos, uma saída feita pelo estoquista apareceu nos três terminais. A
  recepção, depois de `deixar`, não recebeu a mensagem seguinte, e voltou a receber após `entrar`.

## 15. Associação requisito → implementação

| Requisito do enunciado | Arquivo / classe | Método(s) |
|---|---|---|
| Reimplementar a questão 4 do TD01 | `servico/CentralNotificacoes`, `servico/Assinatura`, `servico/MonitorEstoque`, `cliente/OuvinteNotificacoes`, `servidor/ConsoleServidor` | `entrarGrupo`, `sairGrupo`, `publicar`, `aguardar`, `verificar`, `verificarTodos` |
| Cliente-servidor via RMI | `protocolo/PortaServidora`, `servidor/ServidorEstoque`, `protocolo/ProtocoloCliente` | `entregar`, `iniciar` (createRegistry/exportObject/rebind), `localizar` (getRegistry/lookup) |
| Não criar sockets | todo o projeto; verificado por `teste/TesteRestricoes` | — |
| `doOperation` | `protocolo/ProtocoloCliente` | `doOperation(RemoteObjectRef, String, byte[])` |
| `getRequest` | `protocolo/ProtocoloServidor` | `getRequest()` |
| `sendReply` | `protocolo/ProtocoloServidor` | `sendReply(byte[], InetAddress, int)` |
| Selecionar objeto / executar método | `servidor/Despachante`, `servidor/TabelaObjetosRemotos`, `servidor/Esqueleto*` | `processar`, `localizar`, `invocar` |
| Mensagem com 5 campos | `protocolo/Mensagem`, `protocolo/TipoMensagem`, `protocolo/CodecMensagem` | `empacotar`, `desempacotar` |
| objectReference/methodId como String | `protocolo/RemoteObjectRef` (`objeto`, `interfaceRemota`), `Mensagem.methodId` | — |
| ≥ 4 entidades | `modelo/*` | — |
| ≥ 2 agregações | `ItemEstoque.fornecedor`, `PedidoReposicao.fornecedor`, `Estoque.itens`, `PedidoReposicao.itens` | `getFornecedor`, `adicionar` |
| ≥ 2 heranças | `Medicamento`, `Vacina`, `Insumo` `extends ItemEstoque` | `validar`, `getDiasAvisoValidade`, `exigeJustificativaSaida`, `copiar` |
| ≥ 4 métodos remotos | `api/*Remot*` (20 métodos) | ver seção 9 |
| Passagem por referência | `api/ObjetoRemoto`, `servidor/EsqueletoBase`, `servidor/TabelaObjetosRemotos`, `cliente/ProxyBase` | `porReferencia`, `exportar`, `resolver`, `referenciaDe`, `abrirPedidoReposicao`, `entrarGrupo`, `sairGrupo` |
| Passagem por valor com representação externa | `json/CodecEntidades`, `cliente/EstoqueProxy`, `servidor/EsqueletoEstoque`, `servico/EstoqueServico` | `itemParaJson`/`itemDeJson`, `cadastrarItem`, `buscarItem` |
| Representação externa (métodos, argumentos, resultados) | `json/Json`, `json/ObjetoJson`, `protocolo/CargaResposta` | `escrever`, `ler`, `sucesso`, `erro`, `ler` |
| Vários clientes | `servidor/Despachante` (pool), servos thread-safe, `teste/TesteProcessos` | `executarLaco`, `atender` |
| Modo texto | `cliente/ClienteEstoque`, `servidor/ConsoleServidor` | `executar`, `processar` |
| Relatório e repositório | `RELATORIO.md`, `README.md` | — |

## 16. Limitações

- **Estado em memória:** reiniciar o servidor apaga movimentações, pedidos e sessões (o
  estoque volta à carga inicial). Não havia exigência de persistência.
- **Segurança básica:**
  - usuários e senhas fixos no código, como no TD01;
  - senhas comparadas em texto (com comparação de tempo constante);
  - tokens sem expiração;
  - sem perfis de permissão;
  - sem criptografia: o RMI padrão não usa TLS.
- **`clientPort` é lógico:** o RMI não expõe a porta TCP do cliente, então `sendReply` endereça o
  canal de retorno da chamada pendente. O IP (`clientHost`) é real e conferido.
- **Sem retransmissão com histórico:** o cliente só repete quando sabe que a requisição não foi
  entregue. Se a conexão cair no meio de uma operação, o resultado fica desconhecido para o
  cliente (*at-most-once*).
- **Long polling ocupa uma thread do servidor** por cliente escutando (até 256 requisições simultâneas).
  É adequado para a escala de uma clínica, não para milhares de clientes.
- **Notificações não são persistidas:** quem está fora do grupo perde as mensagens (mesma semântica do
  multicast). Cada fila guarda no máximo 500 mensagens.
- **Rede:** para clientes em outras máquinas, o servidor deve ser iniciado com `-HostName <IP>`.
  Os testes foram executados em uma única máquina (loopback): a execução entre computadores diferentes
  não foi testada.
- **Testes sem JUnit:** foi usado um executor próprio (como no TD01), para manter o projeto sem dependências.

## 17. Conclusão

O TD02 reimplementa a questão 4 do TD01 como um serviço cliente-servidor sobre Java RMI. As peças
do livro estão separadas em classes próprias:

- `doOperation` envia e espera;
- `getRequest` e `sendReply` alimentam o laço do despachante;
- a tabela de objetos remotos seleciona o objeto;
- os esqueletos executam os métodos;
- as mensagens têm os cinco campos da figura.

O RMI faz o transporte, e a aplicação nunca cria sockets.

O domínio do estoque veterinário dá significado aos requisitos de orientação a objetos:

- três tipos de item, cada um com as próprias regras;
- fornecedores agregados a itens e pedidos;
- pedidos e assinaturas que, por terem estado e ciclo de vida no servidor, são naturalmente passados
  por referência;
- entidades que trafegam como cópias em JSON.

As notificações em tempo real do multicast foram mantidas com *long polling*, sem abandonar o
modelo requisição-resposta.

A implementação foi validada por 45 testes automatizados, todos aprovados, incluindo concorrência,
erros, a verificação das restrições do enunciado e uma execução com servidor e clientes em processos
separados.
