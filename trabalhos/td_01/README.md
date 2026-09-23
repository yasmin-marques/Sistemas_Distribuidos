# Trabalho 1 - Comunicação entre Processos

## QXD0043 - Sistemas Distribuídos

**Universidade Federal do Ceará - Campus Quixadá**\
**Professor:** Antonio Rafael Braga

## Descrição do projeto

Este projeto implementa diferentes formas de comunicação entre processos
utilizando Java puro, explorando conceitos fundamentais de Sistemas
Distribuídos:

-   Comunicação via `InputStream` e `OutputStream`;
-   Comunicação através de arquivos;
-   Comunicação TCP utilizando sockets;
-   RPC (Remote Procedure Call) utilizando serialização Java;
-   Comunicação UDP multicast;
-   Sistema distribuído de votação utilizando comunicação híbrida TCP +
    UDP multicast.

O serviço remoto escolhido foi:

> **Biblioteca** - Sistema distribuído para gerenciamento de livros,
> empréstimos e devoluções.

# Requisitos e dependências

O projeto não utiliza Maven, Gradle ou bibliotecas externas.

Todas as funcionalidades foram implementadas utilizando apenas a
biblioteca padrão do Java.

## Dependência obrigatória

### Java Development Kit (JDK)

É necessário possuir o JDK 11 ou superior instalado.

Versão utilizada no desenvolvimento:

    JDK 25

Verifique a instalação:

``` bash
java -version
javac -version
```

O projeto foi testado utilizando OpenJDK 25.

## Compilação

Na raiz do projeto:

``` bash
./compilar.sh
```

O script gera as classes compiladas dentro da pasta:

    ./build

Após a compilação, os comandos de execução devem ser executados a partir
da raiz do projeto.

# Estrutura do projeto

    td_01/
        └──src/
            └── br/
                └── ufc/
                    └── quixada/
                        └── sd/
                            ├── biblioteca/
                            ├── multicast/
                            └── votacao/

    build/
    README.md
    compilar.sh

# Exercícios 1 e 2 - Streams e Sockets

Foram implementadas as classes:

-   `LivroOutputStream`
-   `LivroInputStream`

Elas permitem o envio e recebimento de objetos `Livro` utilizando
streams customizadas.

## Entrada e saída padrão

``` bash
java -cp build br.ufc.quixada.sd.biblioteca.TesteSaidaPadrao | java -cp build br.ufc.quixada.sd.biblioteca.TesteEntradaPadrao
```

## Arquivos

Escrita:

``` bash
java -cp build br.ufc.quixada.sd.biblioteca.TesteArquivoEscrita
```

Leitura:

``` bash
java -cp build br.ufc.quixada.sd.biblioteca.TesteArquivoLeitura
```

## TCP

Servidor:

``` bash
java -cp build br.ufc.quixada.sd.biblioteca.ServidorTCPStream
```

Cliente:

``` bash
java -cp build br.ufc.quixada.sd.biblioteca.ClienteTCPStream localhost
```

# Exercício 3 - RPC utilizando sockets

Foi implementada comunicação RPC utilizando:

-   `ObjectOutputStream`
-   `ObjectInputStream`

Mensagens:

-   `Requisicao`
-   `Resposta`

O servidor suporta múltiplos clientes através de threads independentes.

Servidor:

``` bash
java -cp build br.ufc.quixada.sd.biblioteca.ServidorBiblioteca
```

Cliente:

``` bash
java -cp build br.ufc.quixada.sd.biblioteca.ClienteBiblioteca localhost
```

Funcionalidades:

-   Cadastrar livro;
-   Buscar livro;
-   Listar livros;
-   Remover livro;
-   Emprestar;
-   Devolver.

# Exercício 4 - UDP Multicast

Implementação utilizando:

-   TCP para autenticação;
-   UDP multicast para notificações.

Configuração:

    Grupo multicast: 230.0.0.1:4446
    Porta TCP: 5002

Execução:

Servidor:

``` bash
java -cp build br.ufc.quixada.sd.multicast.ServidorMulticastBiblioteca
```

Cliente:

``` bash
java -cp build br.ufc.quixada.sd.multicast.ClienteMulticastBiblioteca localhost
```

# Questão extra - Sistema de votação distribuído

Implementa:

-   TCP unicast para login e votação;
-   UDP multicast para mensagens administrativas.

Configuração:

    Grupo multicast: 230.0.0.2:4447
    Porta TCP: 5003

Servidor:

``` bash
java -cp build br.ufc.quixada.sd.votacao.ServidorVotacao 120
```

Eleitor:

``` bash
java -cp build br.ufc.quixada.sd.votacao.ClienteEleitor localhost
```

Administrador:

``` bash
java -cp build br.ufc.quixada.sd.votacao.ClienteAdmin localhost
```