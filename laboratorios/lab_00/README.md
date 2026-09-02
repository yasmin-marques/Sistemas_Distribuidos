# Lab 06 - UDP Ping (Pinger UDP)

Implementação do laboratório de Redes utilizando UDP em Python.

## Estrutura

```text
├── laboratorios
│   │
│   ├── lab_00
│   │   │
│   │   ├── client
│   │   │   └── PingClient.py
│   │   │
│   │   ├── exercicio_2
│   │   │   └── PingClientTimer.py
│   │   │
│   │   ├── exercicio_3
│   │   │   ├── ReliableUdpSender.py
│   │   │   └── ReliableUdpReceiver.py
│   │   │
│   │   ├── server
│   │   │   └── PingServer.py
│   │   │
│   │   └── README.md
│   │
│   └── README.md
│
└── README.md
```

## Executando

### Servidor

Entre na pasta:

```bash
cd server
```

Execute:

```python
python PingServer.py 5000
```

### Cliente

Abra outro terminal:

```bash
cd client
```

Execute:

```python
python PingClient.py localhost 5000
```

## Funcionamento

O cliente envia 10 mensagens UDP no formato:

```python
PING sequência timestamp
```

O servidor retorna a mensagem recebida.

O cliente calcula o RTT e informa:

- RTT mínimo
- RTT máximo
- RTT médio
- Pacotes perdidos

## Exercícios

### Exercício 01

Implementado no `client/PingClient.py`: ao final das 10 requisições, o
cliente imprime `RTT mínimo`, `RTT máximo` e `RTT médio`, além do total de
pacotes perdidos.

### Exercício 02

Pasta: `exercicio_2/PingClientTimer.py`

Os 10 envios são agendados com `threading.Timer` para instantes fixos (0s,
1s, 2s, ..., 9s a partir do início), enquanto uma *thread* separada fica só
recebendo as respostas e calculando o RTT de cada uma. Envio e recebimento
ficam desacoplados, exatamente como um `ping` de sistema operacional.

```bash
cd server
python PingServer.py 5000

cd exercicio_2
python PingClientTimer.py localhost 5000
```

### Exercício 03

Pasta: `exercicio_3/`
- `ReliableUdpSender.py`
- `ReliableUdpReceiver.py`

Implementa um protocolo simples do tipo *Stop-and-Wait* para transporte
**unidirecional e confiável** de dados sobre UDP:

- os dados são divididos em pedaços numerados (`DATA <seq> <tamanho>`);
- o `Sender` envia um pedaço e espera um `ACK <seq>` correspondente; se o
  `ACK` não chegar dentro do timeout, o pedaço é retransmitido (até um
  número máximo de tentativas);
- ao final, um pacote `FIN` (também confirmado por `ACK`) sinaliza o fim da
  transferência;
- o `Receiver` descarta duplicatas (mas reenvia o `ACK`, caso o `ACK`
  anterior tenha se perdido) e entrega os dados em ordem;
- como a rede local normalmente não perde pacotes, tanto o `Sender` quanto
  o `Receiver` simulam perda artificial (parâmetro `loss_rate`) dos pacotes
  que cada um envia, permitindo testar a retransmissão sem depender de
  perda real na rede.

```bash
cd exercicio_3
python ReliableUdpReceiver.py 6000 0.3

cd exercicio_3
python ReliableUdpSender.py localhost 6000 "mensagem de teste" 0.3
```