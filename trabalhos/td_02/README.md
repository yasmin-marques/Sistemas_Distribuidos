# Trabalho 2 - RPC/RMI: estoque da clínica veterinária

## QXD0043 - Sistemas Distribuídos

**Universidade Federal do Ceará - Campus Quixadá**\
**Professor:** Antonio Rafael Braga\
**Dupla 26:** Milena Helen e Yasmin Marques

O relatório técnico está em doc/relatorio.txt

---

## 1. Apresentação

O sistema gerencia o **estoque de uma clínica veterinária**: medicamentos, vacinas e insumos,
seus fornecedores, as entradas e saídas, os pedidos de reposição e os avisos para a equipe
(estoque baixo, validade próxima, item vencido, movimentações).

No TD01 (questão 4), os avisos eram enviados por **UDP multicast** (grupo `230.0.0.1:4446`)
e a autenticação usava **TCP**. No TD02, a mesma funcionalidade foi reimplementada como
**cliente-servidor com Java RMI**, organizada no **protocolo requisição-resposta** da seção 5.2
do livro (`doOperation` / `getRequest` / `sendReply`). **Nenhum socket é criado pela aplicação**:
toda a comunicação passa pelo RMI.

## 2. Objetivos do TD02

| Exigência do enunciado | Como foi atendida |
|---|---|
| Reimplementar a questão 4 do TD01 em cliente-servidor | Grupo de notificações (`NOTIFICACAO`, `ALERTA`, `ATUALIZACAO`), entrar/sair do grupo, monitor de estoque, autenticação e console do servidor, agora por RMI |
| Protocolo requisição-resposta (seção 5.2) | `ProtocoloCliente.doOperation`, `ProtocoloServidor.getRequest` e `ProtocoloServidor.sendReply`, com mensagens de 5 campos |
| Comunicação via RPC/RMI, sem sockets | Interface remota `PortaServidora` exportada pelo Java RMI; teste automático confere fonte e bytecode |
| ≥ 4 entidades, ≥ 2 agregações, ≥ 2 heranças | 11 entidades; 4 agregações; 3 heranças (`Medicamento`, `Vacina`, `Insumo` estendem `ItemEstoque`) |
| ≥ 4 métodos remotos | 20 métodos remotos em 5 interfaces |
| Passagem por referência e por valor | Objetos remotos viajam como `RemoteObjectRef`; entidades viajam como cópias em JSON |
| Representação externa de dados | JSON (mensagens, argumentos e resultados) |

## 3. Tecnologias e versões

| Item | Versão |
|---|---|
| Linguagem | Java (recursos do Java 17: records, `switch` com `->`, *pattern matching*) |
| JDK usado nos testes | Eclipse Temurin **21.0.5** (o código também compila com `--release 17`) |
| Comunicação | Java RMI (`java.rmi`, parte do JDK) |
| Representação externa | JSON, com leitor/escritor próprio (`br.ufc.sd.estoque.json.Json`); a escolha está justificada no relatório, seção 11 |
| Build | `javac` puro, através do script `compilar.ps1` (sem Maven/Gradle) |
| Testes | Executor próprio (`ExecutarTestes`), sem bibliotecas externas |
| Sistema | Windows 11 + PowerShell 5.1 (os comandos `java` também funcionam em Linux/macOS) |

Não há dependências externas: basta o JDK.

## 4. Pré-requisitos

1. **JDK 17 ou superior** no `PATH`. Confira no PowerShell:

   ```powershell
   java -version
   javac -version
   ```

   Se não estiver instalado: `winget install EclipseAdoptium.Temurin.21.JDK` e abra um novo terminal.

2. **Permissão para rodar scripts `.ps1`** na sessão atual (não altera a configuração da máquina):

   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   ```

   Outra opção é chamar cada script com `powershell -ExecutionPolicy Bypass -File .\script.ps1`.

3. Na primeira execução do servidor, o **Firewall do Windows** pode perguntar se o Java pode
   abrir a porta. Para uso só na própria máquina, basta permitir em "Redes privadas".

## 5. Estrutura do projeto

```
trabalhos/td_02/
├── README.md                 este guia
├── RELATORIO.md              relatório técnico
├── compilar.ps1              compila código e testes para .\out
├── servidor.ps1              inicia o servidor RMI
├── cliente.ps1               inicia um cliente de terminal
├── testes.ps1                compila e roda todos os testes
├── demo.ps1                  abre servidor + N clientes em janelas separadas
└── src/
    ├── main/java/br/ufc/sd/estoque/
    │   ├── modelo/      entidades: ItemEstoque (abstrata), Medicamento, Vacina, Insumo,
    │   │                Fornecedor, Estoque, Movimentacao, PedidoReposicao, ItemPedido,
    │   │                Notificacao, Sessao + enums (TipoNotificacao, TipoMovimentacao, StatusPedido)
    │   ├── json/        representação externa: Json, ObjetoJson, CodecEntidades
    │   ├── protocolo/   requisição-resposta: Mensagem, TipoMensagem, RemoteObjectRef,
    │   │                CodecMensagem, CargaResposta, PortaServidora (interface RMI),
    │   │                ProtocoloCliente (doOperation), ProtocoloServidor (getRequest/sendReply),
    │   │                RequisicaoRecebida, CodigoErro, ExcecaoRemota, FalhaComunicacaoException
    │   ├── api/         interfaces remotas: AutenticacaoRemota, EstoqueRemoto,
    │   │                PedidoReposicaoRemoto, NotificacoesRemotas, AssinaturaRemota,
    │   │                ObjetoRemoto (marcador de passagem por referência), NaoAutenticadoException
    │   ├── servico/     regras de negócio (servos): AutenticacaoServico, EstoqueServico,
    │   │                PedidoReposicaoServico, CentralNotificacoes, Assinatura,
    │   │                MonitorEstoque, Publicador, DadosIniciais
    │   ├── servidor/    ServidorEstoque (main), Despachante, TabelaObjetosRemotos,
    │   │                Esqueleto + 5 esqueletos, ConsoleServidor
    │   └── cliente/     ClienteEstoque (main), ConexaoClinica, OuvinteNotificacoes,
    │                    ProxyBase + 5 proxies
    └── test/java/br/ufc/sd/estoque/teste/
        ├── ExecutarTestes.java          executor de todos os testes
        ├── TesteRepresentacaoExterna    JSON, entidades e mensagens
        ├── TesteProtocolo               getRequest/sendReply
        ├── TesteIntegracaoRmi           servidor RMI real + clientes
        ├── TesteProcessos               servidor e 3 clientes em JVMs separadas
        ├── TesteRestricoes              ausência de sockets e independência do TD01
        └── Teste, Apoio                 mini-framework e utilitários
```

A pasta `out/` é gerada pela compilação (está no `.gitignore`). O TD02 não usa nenhum
arquivo do TD01 (`trabalhos/td_01`), que foi mantido como estava.

## 6. Arquitetura

```
 CLIENTE (JVM 1..N)                                   SERVIDOR (JVM única)
 ┌──────────────────────────┐                         ┌────────────────────────────────────────┐
 │ ClienteEstoque (menu)    │                         │ ServidorEstoque (main + console)       │
 │ OuvinteNotificacoes ──┐  │                         │                                        │
 │        │ (thread 2)   │  │                         │ Registro RMI :1099                     │
 │ Proxies (EstoqueProxy, │  │                         │   "ClinicaVeterinaria" → PortaServidora│
 │  NotificacoesProxy...) │  │                         │                                        │
 │        │ JSON          │  │   Java RMI              │ ProtocoloServidor  (exportado no RMI)  │
 │ ProtocoloCliente       │  │   entregar(byte[])      │   fila ← requisição  ── getRequest()   │
 │   doOperation() ───────┼──┼────────────────────────▶│   resposta → cliente ◀─ sendReply()    │
 │                        │  │◀────────────────────────│         │                              │
 └────────────────────────┴──┘   byte[] (JSON)         │ Despachante (laço + threads)           │
                                                       │   TabelaObjetosRemotos → Esqueleto     │
                                                       │       → Servo (EstoqueServico,         │
                                                       │         CentralNotificacoes, ...)      │
                                                       │ MonitorEstoque (thread periódica)      │
                                                       └────────────────────────────────────────┘
```

| Camada | Componentes | Responsabilidade |
|---|---|---|
| Transporte | `PortaServidora` (interface `java.rmi.Remote`), registro RMI | Levar os bytes da requisição e trazer os da resposta. É o único objeto exportado pelo RMI |
| Protocolo | `ProtocoloCliente`, `ProtocoloServidor`, `Mensagem`, `CodecMensagem`, `RemoteObjectRef`, `CargaResposta` | `doOperation`/`getRequest`/`sendReply`, montagem e conferência das mensagens, códigos de erro |
| Referência remota e despacho | `TabelaObjetosRemotos`, `Despachante`, `Esqueleto*` | Selecionar o objeto pela referência, desempacotar os argumentos, chamar o método, empacotar o resultado |
| Stubs do cliente | `ProxyBase`, `*Proxy`, `ConexaoClinica` | Implementam as mesmas interfaces remotas; para quem usa, é uma chamada de método comum |
| Negócio | `servico.*` | Regras do estoque e do grupo de notificações, com controle de concorrência |
| Domínio | `modelo.*` | Entidades, herança e agregações |
| Representação externa | `json.*` | Entidades ↔ JSON |

## 7. Métodos remotos

Todos são chamados pelo cliente via `doOperation`. `objectReference.objeto` e `methodId` são Strings.

| Objeto (`objeto`) | Interface | Método | Passagem |
|---|---|---|---|
| `Autenticacao` | `AutenticacaoRemota` | `autenticar(usuario, senha)` → `Sessao` | valor |
| | | `encerrarSessao(token)` | valor |
| `Estoque` | `EstoqueRemoto` | `listarItens()` → `List<ItemEstoque>` | valor |
| | | `buscarItem(idItem)` → `ItemEstoque` | valor |
| | | `listarFornecedores()` → `List<Fornecedor>` | valor |
| | | `cadastrarItem(token, item)` → `ItemEstoque` | **valor** (objeto local enviado ao servidor) |
| | | `registrarEntrada(token, idItem, quantidade)` → `Movimentacao` | valor |
| | | `registrarSaida(token, idItem, quantidade, motivo)` → `Movimentacao` | valor |
| | | `listarMovimentacoes(idItem)` → `List<Movimentacao>` | valor |
| | | `abrirPedidoReposicao(token, idFornecedor)` → `PedidoReposicaoRemoto` | **referência** (resultado) |
| `PedidoReposicao-xxxxxxxx` | `PedidoReposicaoRemoto` | `consultar()`, `adicionarItem(token, idItem, qtd)`, `confirmarRecebimento(token)`, `cancelar(token)` | invocados pela referência |
| `Notificacoes` | `NotificacoesRemotas` | `entrarGrupo(token)` → `AssinaturaRemota` (*joinGroup*) | **referência** (resultado) |
| | | `sairGrupo(assinatura)` (*leaveGroup*) | **referência** (argumento) |
| | | `publicar(token, tipo, mensagem)` → `Notificacao` | valor |
| | | `membros()` → `List<String>` | valor |
| `Assinatura-xxxxxxxx` | `AssinaturaRemota` | `aguardar(esperaMaximaMs)` → `List<Notificacao>`, `usuario()` | invocados pela referência |

Regras de negócio aplicadas no servidor:

- a saída é recusada se o saldo for insuficiente ou o item estiver vencido;
- medicamento controlado só sai com justificativa;
- não é possível cadastrar item vencido, nem duplicado (mesmo tipo e nome);
- cada subclasse valida os próprios campos (o medicamento exige princípio ativo, a vacina exige doença-alvo, e assim por diante);
- num pedido só entram itens do fornecedor do pedido;
- a confirmação do pedido dá entrada em todos os itens de uma vez (tudo ou nada).

## 8. Protocolo requisição-resposta

```
Cliente                                         Servidor
doOperation(ref, "registrarSaida", args)
  ├─ monta Mensagem(0, requestId, ref, método, args)
  ├─ empacota em JSON
  └─ PortaServidora.entregar(bytes) ── RMI ──▶  ProtocoloServidor.entregar: põe na fila e espera
                                                Despachante: req = getRequest()
                                                  seleciona o objeto (TabelaObjetosRemotos)
                                                  executa o método (Esqueleto → servo)
     (espera)                                     sendReply(resposta, clientHost, clientPort)
  ◀── RMI ── bytes da resposta  ◀───────────────  entregar devolve a resposta
  ├─ desempacota, confere messageType = 1 e requestId
  └─ devolve o resultado (ou lança ExcecaoRemota)
```

Mensagem de **requisição** (o que de fato trafega; veja com `protocolo on` no cliente):

```json
{"messageType":0,"requestId":10,
 "objectReference":{"host":"localhost","porta":1099,"servico":"ClinicaVeterinaria","objeto":"Estoque","interface":"EstoqueRemoto"},
 "methodId":"buscarItem",
 "arguments":{"idItem":1}}
```

Mensagem de **resposta**:

```json
{"messageType":1,"requestId":10,
 "objectReference":{...mesma referência...},
 "methodId":"buscarItem",
 "arguments":{"status":"OK","resultado":{"classe":"MEDICAMENTO","id":1,"nome":"Amoxicilina 250mg",
   "fornecedor":{"id":1,"nome":"VetPharma","cnpj":"11.111.111/0001-11","telefone":"(85) 3333-1001"},
   "quantidade":50,"estoqueMinimo":10,"precoUnitario":35.9,"validade":"2030-06-30",
   "principioAtivo":"Amoxicilina","concentracao":"250 mg","controlado":false}}}
```

Em caso de erro, a resposta traz `{"status":"ERRO","erro":{"codigo":"OPERACAO_NEGADA","mensagem":"..."}}`.
Códigos: `REQUISICAO_INVALIDA`, `OBJETO_INEXISTENTE`, `METODO_INEXISTENTE`,
`ARGUMENTO_INVALIDO`, `OPERACAO_NEGADA`, `NAO_AUTENTICADO`, `ERRO_INTERNO`.

**Adaptações de assinatura** (permitidas pelo enunciado):

| Livro | TD02 | Motivo |
|---|---|---|
| `byte[] doOperation(RemoteObjectRef o, int methodId, byte[] arguments)` | `byte[] doOperation(RemoteObjectRef o, String methodId, byte[] arguments)` | o método é identificado pelo nome (String) |
| `byte[] getRequest()` | `RequisicaoRecebida getRequest()` | devolve os bytes **e** o endereço de retorno (no livro ele vem do datagrama) |
| `void sendReply(byte[] reply, InetAddress clientHost, int clientPort)` | `boolean sendReply(byte[] reply, InetAddress clientHost, int clientPort)` | indica se o cliente ainda aguardava. `clientHost` é o IP real do cliente (`RemoteServer.getClientHost()`); `clientPort` identifica o **canal de retorno**, ou seja, a chamada RMI daquele cliente que espera a resposta, já que o RMI não expõe a porta TCP |

## 9. Passagem por valor e por referência

- **Por valor:** entidades (`ItemEstoque`, `Fornecedor`, `Movimentacao`, `PedidoReposicao`, `Notificacao`, `Sessao`)
  são convertidas em JSON e reconstruídas do outro lado, então cada lado tem a **sua cópia**.
  - O cliente altera a cópia e o servidor não muda (comando `demo-valor`).
  - No `cadastrar`, o cliente cria um objeto `Medicamento`/`Vacina`/`Insumo` local e o envia: o
    servidor executa sobre a cópia desempacotada, gera o id e devolve outra cópia, enquanto o objeto
    local continua com id 0.
- **Por referência:** objetos que implementam `ObjetoRemoto` (`PedidoReposicaoRemoto`, `AssinaturaRemota`)
  **não saem do servidor**. O que viaja é uma `RemoteObjectRef`, e o cliente recebe um proxy que invoca os métodos
  no objeto original.
  - **Como resultado:** `abrirPedidoReposicao` e `entrarGrupo` devolvem referências. Dois proxies com a mesma
    referência alteram o mesmo pedido (comando `demo-referencia`).
  - **Como argumento:** `sairGrupo(assinatura)` recebe a referência e o servidor a converte de volta no objeto real.
  - **Ciclo de vida:** os objetos referenciados entram na tabela de objetos remotos quando a referência sai pela
    primeira vez e saem dela quando o membro deixa o grupo ou o prazo (*lease*) vence. A partir daí, a
    referência dá `OBJETO_INEXISTENTE`.
- **No próprio Java RMI:** o stub de `PortaServidora` obtido no registro é uma referência remota RMI (um proxy
  dinâmico), enquanto os `byte[]` que ele transporta são passados por valor.

## 10. Notificações

O servidor não "empurra" mensagens: cada cliente tem no servidor uma **assinatura** (fila de
mensagens do membro). A thread `OuvinteNotificacoes` do cliente chama `aguardar(10000)` em laço.
Se não há mensagem, a resposta fica retida no servidor até chegar uma (*long polling*). Assim:

- a entrega é em tempo real e segue o modelo requisição-resposta (o cliente sempre inicia);
- o cliente não precisa abrir porta nem ser servidor RMI, o que funciona mesmo atrás de firewall/NAT;
- todos os membros recebem as mensagens na **mesma ordem** (sequência global);
- o cliente tem duas threads (escuta + interação), como exigido no TD01;
- os alertas automáticos são os do monitor: estoque baixo, validade próxima e vencido, enviados uma vez por problema.
  As movimentações geram `ATUALIZACAO`.

## 11. Compilação

No PowerShell, dentro de `trabalhos\td_02`:

```powershell
cd C:\caminho\para\Sistemas_Distribuidos\trabalhos\td_02
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\compilar.ps1
```

Saída esperada:

```
Compilado: 64 arquivos em out\classes e 8 em out\test-classes.
```

Sem o script (comandos equivalentes):

```powershell
New-Item -ItemType Directory -Force out\classes, out\test-classes | Out-Null
javac -encoding UTF-8 -d out\classes (Get-ChildItem -Recurse src\main\java -Filter *.java).FullName
javac -encoding UTF-8 -cp out\classes -d out\test-classes (Get-ChildItem -Recurse src\test\java -Filter *.java).FullName
```

## 12. Executando o servidor

```powershell
.\servidor.ps1
```

Opções: `-Porta 1099` (registro RMI e objetos usam a mesma porta), `-HostName localhost`,
`-Monitor 10` (segundos entre verificações), `-SemConsole`, `-Detalhado`.

Equivalente sem script:

```powershell
java "-Dstdout.encoding=UTF-8" -cp out\classes br.ufc.sd.estoque.servidor.ServidorEstoque --porta 1099
```

Saída:

```
Servidor do estoque veterinário (TD02 - Java RMI)
Registro RMI: localhost:1099  |  serviço: ClinicaVeterinaria
Objetos remotos: [Autenticacao, Estoque, Notificacoes]
Monitor de estoque a cada 10 s.
Servidor pronto.
Comandos do servidor:
  NOTIFICACAO|ALERTA|ATUALIZACAO <mensagem>  envia ao grupo
  baixar <id> <qtd> [motivo]   dá baixa no estoque (o monitor avisa se ficar baixo)
  repor <id> <qtd>             repõe o estoque (envia ATUALIZACAO)
  estoque | movimentacoes | clientes | objetos | verificar | ajuda | sair
```

O servidor registra cada requisição recebida, por exemplo
`[127.0.0.1 canal 5] Request #5 Estoque.registrarSaida`, e cada mensagem publicada, por exemplo `[Grupo] [ALERTA] ...`.

## 13. Executando o cliente

Em **outro** terminal (o servidor deve estar rodando):

```powershell
cd C:\caminho\para\Sistemas_Distribuidos\trabalhos\td_02
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\cliente.ps1
```

Opções: `-Servidor localhost -Porta 1099`. Sem script:

```powershell
java "-Dstdout.encoding=UTF-8" -cp out\classes br.ufc.sd.estoque.cliente.ClienteEstoque localhost 1099
```

Usuários (os mesmos do TD01):

| Usuário | Senha |
|---|---|
| `veterinario` | `vet123` |
| `recepcao` | `rec123` |
| `estoquista` | `est123` |

Comandos do cliente:

```
itens                          lista o estoque
item <id>                      detalhes de um item
fornecedores                   lista os fornecedores
movimentacoes [id]             histórico (de um item ou de todos)
entrada <id> <qtd>             registra entrada no estoque
saida <id> <qtd> [motivo]      registra saída (controlados exigem motivo)
cadastrar                      cria um item no cliente e envia POR VALOR
pedido abrir <idFornecedor>    abre um pedido de reposição (recebe uma REFERÊNCIA)
pedido item <idItem> <qtd> | pedido ver | pedido confirmar | pedido cancelar
publicar <TIPO> <mensagem>     envia NOTIFICACAO, ALERTA ou ATUALIZACAO ao grupo
membros | entrar | deixar | status
demo-valor [id] | demo-referencia
protocolo on|off               mostra as mensagens JSON trocadas
ajuda | sair
```

## 14. Vários clientes

Abra um terminal por cliente e rode `.\cliente.ps1` em cada um (cada cliente é um processo Java
independente). Ou, para abrir tudo de uma vez:

```powershell
.\demo.ps1 -Clientes 3
```

O script abre o servidor e três clientes, cada um na sua janela do PowerShell.

**Em máquinas diferentes:** no servidor, use o IP da máquina, que é o endereço gravado nas referências
remotas:

```powershell
.\servidor.ps1 -HostName 192.168.0.10
```

e nos clientes:

```powershell
.\cliente.ps1 -Servidor 192.168.0.10
```

O firewall do servidor precisa liberar a porta TCP 1099.

## 15. Roteiro de demonstração

Com o servidor e dois clientes abertos (A = `estoquista`, B = `veterinario`):

1. **Tempo real:** em A, `saida 2 21 campanha de vermifugação`. Os dois clientes mostram
   `[ATUALIZACAO] Saída de 21 un. de 'Vermífugo Oral' por estoquista. Saldo: 9.` e, em seguida,
   `[ALERTA] Estoque baixo de medicamento 'Vermífugo Oral' (#2): 9 unidade(s), mínimo 10.`
2. **Mensagens do grupo:** em B, `publicar ALERTA Falta luva no centro cirúrgico`. A recebe na hora.
   No console do servidor, `NOTIFICACAO Reunião às 14h` chega aos dois.
3. **joinGroup/leaveGroup:** em B, `deixar`. Em A, `publicar NOTIFICACAO teste`: só A recebe.
   Em B, `entrar` e B volta a receber.
4. **Regras:** `saida 9 1` é recusada (o Tramadol é controlado); `saida 9 1 dor pós-operatória` é aceita;
   `saida 1 9999` é recusada por estoque insuficiente.
5. **Por valor:** `demo-valor 1`, e depois `cadastrar` (por exemplo: `insumo`, `Gaze`, `4`, `10`, `2`, `1.5`, *vazio*, `pacote`, `n`).
6. **Por referência:** `demo-referencia`; depois `pedido abrir 4`, `pedido item 7 100`, `pedido confirmar`.
   O estoque de seringas sobe 100 e todos recebem `ATUALIZACAO`.
7. **Protocolo:** `protocolo on`, `item 1` (mostra as mensagens JSON de ida e volta) e `protocolo off`.
8. **Monitor:** a primeira verificação ocorre 10 s depois de o servidor iniciar. Os clientes que já
   estiverem no grupo recebem os alertas da carga inicial (estoque baixo do anti-inflamatório,
   validade próxima da pomada e da vacina V10). Cada problema é avisado uma única vez, como no
   TD01: quem entrar depois só verá alertas de problemas novos, como o do passo 1.

## 16. Testes

```powershell
.\testes.ps1           # todos (cerca de 6 s)
.\testes.ps1 -Rapido   # sem o teste com processos separados
```

Sem script:

```powershell
java "-Dstdout.encoding=UTF-8" -cp "out\classes;out\test-classes" br.ufc.sd.estoque.teste.ExecutarTestes
```

O que é testado (45 casos):

| Grupo | Casos |
|---|---|
| Representação externa | JSON de ida e volta, JSON inválido, limite de aninhamento, herança e agregação preservadas, entidades inválidas |
| Mensagens | 5 campos, referência remota, mensagens malformadas, carga de sucesso/erro |
| `getRequest`/`sendReply` | entrega da resposta, recusa de endereço errado, 20 chamadas simultâneas sem troca de respostas |
| Servidor RMI | registro e *lookup*, servidor fora do ar, servidor reiniciado |
| Protocolo sobre RMI | `requestId`, bytes malformados, objeto/método/interface inexistente, argumentos inválidos |
| Métodos remotos | autenticação, consultas, cadastro das 3 subclasses, entradas/saídas e regras, pedido completo |
| Valor × referência | cópia não altera o servidor, objeto local enviado, duas referências ao mesmo pedido, referência como argumento, referências forjadas |
| Notificações | três tipos para dois clientes, sair/entrar do grupo, alertas automáticos, tempo real, monitor (com relógio adiantado), expiração por *lease* |
| Concorrência | 30 clientes disputando 12 unidades; 8 clientes com entradas e saídas; 10 membros e 5 publicadores (mesma ordem para todos) |
| Processos | servidor + 3 clientes em JVMs separadas, usando os programas reais |
| Restrições | nenhum socket no fonte nem no bytecode; nenhuma dependência do TD01 |

Saída esperada (resumo):

```
== Representação externa de dados (JSON) ==
  [OK]     JSON: ida e volta com acentos, aspas, escapes e tipos variados (8 ms)
  ...
== Restrições do enunciado ==
  [OK]     Nenhuma classe de socket aparece no código-fonte do TD02 (64 ms)
  [OK]     Nenhum .class compilado referencia classes de socket (o RMI as usa internamente, a aplicação não) (41 ms)
  [OK]     Independência do TD01: nenhum import do pacote 'clinica' e TD01 fora do classpath (30 ms)

Resultado: 45 passou, 0 falhou (5,9 s).
```

O processo termina com código 0 se tudo passar e 1 se algo falhar.

## 17. Exemplos de entrada e saída

Cliente (entradas digitadas após `>`; as linhas `[hh:mm:ss]` são notificações que chegam pela thread de escuta):

```
Cliente do estoque veterinário (TD02 - Java RMI) - servidor localhost:1099/ClinicaVeterinaria
Usuário: estoquista
Senha: est123
Autenticado como estoquista.
Você entrou no grupo de notificações (referência: Assinatura-a1e9f77e (AssinaturaRemota) @ localhost:1099/ClinicaVeterinaria).
> saida 3 2 consulta
[21:26:47] [ATUALIZACAO] Saída de 2 un. de 'Anti-inflamatório 10mg' por estoquista. Saldo: 3.
Registrado: Mov. #1 [28/10 21:26:47] SAIDA de 2 un. de 'Anti-inflamatório 10mg' (item #3) saldo=3 por estoquista | motivo: consulta
> saida 9 1
Erro do servidor (OPERACAO_NEGADA): 'Tramadol 50mg' é controlado: informe a justificativa da saída.
> demo-valor 1
== Passagem por VALOR ==
1) buscarItem(1) devolveu uma cópia: quantidade=50, nome='Amoxicilina 250mg'
2) Alterei a cópia local: quantidade=99999, nome='ALTERADO SÓ NO CLIENTE'
3) buscarItem(1) de novo: quantidade=50, nome='Amoxicilina 250mg'
   O objeto do servidor não mudou: o cliente recebeu uma cópia serializada em JSON.
> demo-referencia
== Passagem por REFERÊNCIA ==
1) abrirPedidoReposicao devolveu uma REFERÊNCIA (o pedido ficou no servidor):
   PedidoReposicao-5e63c6c5 (PedidoReposicaoRemoto) @ localhost:1099/ClinicaVeterinaria
2) adicionarItem pela referência alterou o objeto no servidor: [5x 'Amoxicilina 250mg' (item #1)]
3) Um segundo proxy criado só com a referência (como se outro cliente a recebesse) vê o mesmo pedido: [5x 'Amoxicilina 250mg' (item #1)]
4) Cancelei pelo segundo proxy; o primeiro enxerga: status=CANCELADO (pedido #1)
   Os dois proxies apontam para o MESMO objeto remoto.
> pedido abrir 4
> pedido item 1 5
Erro do servidor (OPERACAO_NEGADA): 'Amoxicilina 250mg' é fornecido por VetPharma, não por MedCirurgica.
```

`cadastrar`, com o objeto criado no cliente e enviado por valor:

```
> cadastrar
Tipo (medicamento/vacina/insumo): insumo
Nome: Gaze
Id do fornecedor (veja 'fornecedores'): 4
Quantidade inicial: 10
Estoque mínimo: 2
Preço unitário (ex.: 12.50): 1.5
Validade (AAAA-MM-DD, vazio = sem validade):
Unidade de medida: pacote
Estéril (s/n): n
Objeto local criado no cliente: Insumo #0 'Gaze' qtd=10 (mín. 2) R$ 1.50 val=- fornecedor=#4 [unidade: pacote]
Enviando por valor (JSON): {"classe":"INSUMO","id":0,"nome":"Gaze","fornecedor":{"id":4,"nome":null,"cnpj":null,"telefone":null},"quantidade":10,"estoqueMinimo":2,"precoUnitario":1.5,"validade":null,"unidadeMedida":"pacote","esteril":false}
[21:36:49] [NOTIFICACAO] recepcao cadastrou insumo 'Gaze' (#10) com 10 unidade(s).
Cópia devolvida pelo servidor: Insumo #10 'Gaze' qtd=10 (mín. 2) R$ 1.50 val=- fornecedor=MedCirurgica [unidade: pacote]
O objeto local continua com id 0: o servidor trabalhou sobre uma cópia.
```

## 18. Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `não pode ser carregado porque a execução de scripts foi desabilitada` | Rode `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` ou use `powershell -ExecutionPolicy Bypass -File .\servidor.ps1` |
| `'java'/'javac' não é reconhecido` | JDK fora do `PATH`. Instale o JDK 17+ e abra um novo terminal |
| `Não foi possível iniciar o servidor na porta 1099: ... Port already in use` | Outro programa (ou outro servidor) usa a porta. Rode `.\servidor.ps1 -Porta 1199` e `.\cliente.ps1 -Porta 1199` |
| Cliente: `Registro RMI inacessível em localhost:1099` | O servidor não está rodando, ou a porta/host está errada |
| Cliente em outra máquina não conecta, ou conecta e falha nas chamadas | Inicie o servidor com `-HostName <IP do servidor>` (o RMI grava esse endereço nas referências) e libere a porta TCP no firewall |
| Acentos aparecem como `?` ou `Ã©` | Use os scripts `.ps1`, que configuram UTF-8, ou rode `chcp 65001` antes e passe `"-Dstdout.encoding=UTF-8"` ao `java` |
| `-Dstdout.encoding=UTF-8` dá erro no PowerShell | No PowerShell, coloque o argumento entre aspas: `"-Dstdout.encoding=UTF-8"` |
| Classpath com `;` quebra o comando | No PowerShell, coloque entre aspas: `-cp "out\classes;out\test-classes"` |
| `[aviso] Assinatura encerrada pelo servidor` | O cliente ficou mais de 30 s sem contato (por exemplo, o computador suspendeu) e a assinatura expirou. Digite `entrar` |
| `NAO_AUTENTICADO: Sessão inválida` | O servidor foi reiniciado e as sessões ficam em memória. Saia e entre de novo no cliente |
