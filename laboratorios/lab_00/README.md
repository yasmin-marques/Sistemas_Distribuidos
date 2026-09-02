# Lab 00 - UDP Ping

Implementação do laboratório de Redes utilizando UDP em Python.

## Estrutura

```

Lab_00_UDP_Ping

├── server
│   └── PingServer.py
│
├── client
│   └── PingClient.py
│
└── README.md

```

## Executando

### Servidor

Entre na pasta:

```

cd server

```

Execute:

```

python PingServer.py 5000

```


### Cliente

Abra outro terminal:

```

cd client

```

Execute:

```

python PingClient.py localhost 5000

```

## Funcionamento

O cliente envia 10 mensagens UDP no formato:

PING sequência timestamp

O servidor retorna a mensagem recebida.

O cliente calcula o RTT e informa:

- RTT mínimo
- RTT máximo
- RTT médio
- Pacotes perdidos