
# Trabalho 4 — Comunicação Indireta com RabbitMQ

**Universidade Federal do Ceará — Campus Quixadá**  
**Disciplina:** QXD0043 — Sistemas Distribuídos  
**Professor:** Antonio Rafael Braga  
**Dupla 26:** Milena Helen e Yasmin Marques  
**Abordagem:** Opção C — Filas de Mensagens

## 1. Visão geral

O sistema integra dois componentes de uma clínica veterinária usando uma fila de mensagens do RabbitMQ:

- **Consultas (Java):** registra consultas em um CSV local e publica solicitações de baixa de produtos.
- **RabbitMQ:** recebe as mensagens na fila durável `estoque.baixas`.
- **Estoque (Python + SQLite):** consome as mensagens e atualiza o saldo dos produtos.

Consultas se comunica com o RabbitMQ, sem acessar diretamente o processo ou o banco de dados do Estoque.

### Organização em dois computadores

| Computador | Componentes executados | Requisitos |
|---|---|---|
| **Servidor** | RabbitMQ no Docker e Estoque em Python | Docker Desktop com Compose v2 e Python 3.10+ |
| **Cliente** | Consultas em Java | JDK 17+ |

O RabbitMQ e o Estoque rodam no mesmo computador, mas em processos independentes. Os dois computadores devem estar na **mesma rede local**, cada um com uma cópia do projeto.

Todos os comandos abaixo foram preparados para **PowerShell no Windows** e devem ser executados dentro da pasta `td_04`.

## 2. Configuração do servidor

### 2.1. Preparar o ambiente

Abra o Docker Desktop e execute:

```powershell
docker compose version
python --version
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

No `.env` do servidor, confira:

```dotenv
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
```

Na configuração de exemplo, as credenciais são `RABBITMQ_USER=clinica` e `RABBITMQ_PASSWORD=clinica123`. Elas são criadas na primeira inicialização do RabbitMQ com um volume novo. O banco SQLite do Estoque é criado automaticamente em `estoque/dados/estoque.db`.

### 2.2. Instalar dependências e iniciar o RabbitMQ

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r estoque\requirements.txt
docker compose up -d --wait
docker compose ps
ipconfig
```

O contêiner `clinica-rabbitmq` deve aparecer como **healthy**.

Anote o **Endereço IPv4** do adaptador Wi-Fi ou Ethernet utilizado e informe à pessoa responsável pelo cliente. O painel de gerenciamento do RabbitMQ pode ser acessado no servidor em [http://localhost:15672](http://localhost:15672).

- **Porta 5672:** conexão AMQP do cliente.
- **Porta 15672:** painel web do RabbitMQ; não precisa ser liberada para o cliente.

### 2.3. Verificar problemas de conexão

Se o cliente não conseguir acessar o servidor, verifique o IP, a rede local e o funcionamento do Docker. Algumas redes de universidades isolam dispositivos; nesse caso, use outra rede local confiável.

Se o Firewall do Windows estiver bloqueando a porta em uma rede privada, execute no **servidor**, em um PowerShell como administrador:

```powershell
New-NetFirewallRule -DisplayName "RabbitMQ TD04 AMQP" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 5672 -Profile Private -RemoteAddress LocalSubnet
```

## 3. Configuração do cliente

### 3.1. Verificar o Java e configurar o broker

```powershell
java -version
javac -version
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

Altere `RABBITMQ_HOST` no `.env` do cliente para o IPv4 do **computador servidor**. Por exemplo:

```dotenv
RABBITMQ_HOST=192.168.0.10
```

O endereço acima é apenas um exemplo. Usuário, senha, porta, vhost e nome da fila devem corresponder aos valores do servidor. Não é necessário configurar o IP nem a porta do processo Estoque no cliente.

### 3.2. Testar a conexão e compilar

Use o mesmo IP configurado no `.env`:

```powershell
Test-NetConnection 192.168.0.10 -Port 5672
```

Se aparecer `TcpTestSucceeded : True`, execute:

```powershell
.\compilar.ps1
```

Na primeira compilação, as bibliotecas Java podem ser baixadas. Se o PowerShell bloquear a execução do script, rode na mesma janela:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\compilar.ps1
```

A alteração da política de execução vale apenas para a sessão atual.

## 4. Teste básico — registrar uma consulta

Os saldos abaixo consideram os dados iniciais do projeto e nenhuma mensagem antiga pendente. Se já tiver realizado testes, utilize o saldo atual ou consulte a restauração da seção 7.

**1. Servidor — iniciar o consumidor.** Deixe o terminal aberto e aguarde `Esperando solicitações...`.

```powershell
.\.venv\Scripts\python estoque\consumidor.py
```

**2. Servidor — consultar o saldo.** Em outro terminal, verifique o saldo do produto 1 (Amoxicilina 250mg). O valor inicial esperado é **50**.

```powershell
.\.venv\Scripts\python estoque\estoque_cli.py saldo 1
```

**3. Cliente — registrar a consulta.** Execute:

```powershell
.\consultas.ps1 registrar --animal Rex --especie Cachorro --diagnostico "Otite" --produto 1 --quantidade 2
```

Aguarde a mensagem `Broker confirmou`.

**4. Servidor — conferir a baixa.** Verifique `Baixa APLICADA` no terminal do consumidor e consulte novamente o saldo:

```powershell
.\.venv\Scripts\python estoque\estoque_cli.py saldo 1
```

O saldo esperado passa de **50 para 48**.

> `Broker confirmou` indica a confirmação da publicação pelo RabbitMQ. `Baixa APLICADA` indica que o Estoque registrou a baixa no SQLite.

## 5. Teste de funcionamento com o Estoque offline

Este teste verifica se Consultas consegue publicar uma baixa enquanto o consumidor está desligado.

**1. Servidor — parar somente o consumidor.** Pressione `Ctrl+C` no terminal de `estoque/consumidor.py`. Mantenha o Docker e o RabbitMQ ligados.

**2. Cliente — registrar uma nova consulta.**

```powershell
.\consultas.ps1 registrar --animal Thor --especie Cachorro --diagnostico "Ferida na pata" --produto 1 --quantidade 5
```

O RabbitMQ deve confirmar a publicação mesmo sem o Estoque em execução.

**3. Servidor — conferir a fila e o saldo.**

```powershell
docker compose exec rabbitmq rabbitmqctl -q list_queues name messages_ready messages_unacknowledged consumers
.\.venv\Scripts\python estoque\estoque_cli.py saldo 1
```

A mensagem deve permanecer aguardando na fila `estoque.baixas`, e o saldo ainda deve ser **48**. A fila também pode ser conferida no painel do RabbitMQ em **Queues and Streams**.

**4. Servidor — reiniciar o consumidor.**

```powershell
.\.venv\Scripts\python estoque\consumidor.py
```

Aguarde `Baixa APLICADA` e `ACK enviado`.

**5. Servidor — conferir o resultado.** Em outro terminal:

```powershell
.\.venv\Scripts\python estoque\estoque_cli.py saldo 1
.\.venv\Scripts\python estoque\estoque_cli.py historico
docker compose exec rabbitmq rabbitmqctl -q list_queues name messages_ready messages_unacknowledged consumers
```

| Momento | Saldo esperado | Mensagens prontas (Ready) | Consumidores |
|---|---:|---:|---:|
| Após o teste básico | 48 | 0 | 1 |
| Estoque offline, nova consulta publicada | 48 | 1 | 0 |
| Estoque reiniciado e baixa aplicada | 43 | 0 | 1 |

## 6. Testes adicionais

### 6.1. Reentrega sem baixa duplicada

No **servidor**, pare o consumidor e inicie-o com o parâmetro de simulação:

```powershell
.\.venv\Scripts\python estoque\consumidor.py --simular-queda-antes-do-ack
```

No **cliente**, registre uma nova baixa:

```powershell
.\consultas.ps1 registrar --animal Mel --especie Gato --diagnostico "Pos-operatorio" --produto 1 --quantidade 1
```

O Estoque aplica a baixa e encerra antes de enviar o ACK. Reinicie o consumidor no servidor, agora sem o parâmetro de simulação:

```powershell
.\.venv\Scripts\python estoque\consumidor.py
```

O consumidor deve indicar `REENTREGA` e `DUPLICADA` para a mesma mensagem, sem descontar o produto novamente. Se o saldo anterior era 43, ele passa para **42** na primeira execução e permanece em **42** após a reentrega.

### 6.2. RabbitMQ indisponível

No **servidor**, pare o broker:

```powershell
docker compose stop rabbitmq
```

No **cliente**, tente registrar uma consulta com produto e consulte o código de saída:

```powershell
.\consultas.ps1 registrar --animal Nina --produto 1 --quantidade 1
$LASTEXITCODE
```

A publicação deve falhar com código de saída **2**, sem registrar a consulta localmente. Para religar o broker, execute no servidor:

```powershell
docker compose up -d --wait
```

Se o consumidor continuar em execução, ele tentará restabelecer a conexão.

## 7. Encerramento e restauração

- **Cliente:** o programa Consultas termina após cada comando. Para listar consultas locais, use `.\consultas.ps1 listar`.
- **Servidor:** pare o consumidor com `Ctrl+C` e execute `docker compose down`. Os dados do RabbitMQ permanecem no volume Docker.

Se tiver criado a regra de firewall, remova-a em um PowerShell como administrador no servidor:

```powershell
Remove-NetFirewallRule -DisplayName "RabbitMQ TD04 AMQP"
```

### Restaurar os dados dos testes

**Atenção:** os comandos abaixo apagam mensagens pendentes e restauram o banco de dados do Estoque. Use-os **somente com dados de teste**. No servidor, deixe o RabbitMQ ligado e o consumidor parado.

```powershell
docker compose exec rabbitmq rabbitmqctl purge_queue estoque.baixas
.\.venv\Scripts\python estoque\estoque_cli.py restaurar
```

Se a fila ainda não existir, pule o primeiro comando. O histórico de Consultas no cliente não é alterado por essa restauração.

Evite `docker compose down -v` no encerramento normal, pois `-v` apaga o volume do RabbitMQ.

**Execução em um único computador:** mantenha `RABBITMQ_HOST=localhost`, utilize terminais separados para cliente, consumidor e consulta de saldo e pule as etapas de configuração de rede entre computadores.

## 8. Referência rápida

| Arquivo ou diretório | Função |
|---|---|
| `docker-compose.yml` | Inicializa o RabbitMQ com painel e volume persistente |
| `.env.example` | Modelo das variáveis de configuração |
| `compilar.ps1` e `consultas.ps1` | Compilação e execução do cliente Java |
| `consultas/dados/consultas.csv` | Registros locais de consultas |
| `estoque/consumidor.py` | Consumidor das mensagens de baixa |
| `estoque/estoque_cli.py` | Consulta saldos, histórico e restaura dados de teste |
| `estoque/dados/estoque.db` | Banco SQLite local do Estoque |
| `RELATORIO.md` | Relatório técnico do trabalho |

| Variável | Valor padrão ou configuração |
|---|---|
| `RABBITMQ_HOST` | `localhost` no servidor; IP do servidor no cliente remoto |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `clinica` / `clinica123` (ambiente de teste) |
| `RABBITMQ_VHOST` | `/` |
| `RABBITMQ_QUEUE` | `estoque.baixas` |
| `ESTOQUE_DB` | Opcional; altera o caminho do SQLite apenas no Estoque |

Variáveis de ambiente com o mesmo nome têm prioridade sobre o `.env`. Se o volume do RabbitMQ já existir, modificar usuário e senha no `.env` não altera automaticamente as credenciais salvas no broker.

## 9. Relatório técnico

A descrição da arquitetura, a justificativa da escolha do RabbitMQ, a análise do desacoplamento, o tratamento de falhas e os custos da solução estão documentados em **[RELATORIO.md](RELATORIO.md)**.