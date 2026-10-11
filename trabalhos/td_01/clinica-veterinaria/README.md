# Trabalho 1 - Comunicação entre Processos

## QXD0043 - Sistemas Distribuídos

**Universidade Federal do Ceará - Campus Quixadá**\
**Professor:** Antonio Rafael Braga\
**Dupla 26:** Milena Helen e Yasmin Marques

## Descrição do projeto

O projeto explora formas de comunicação entre processos usando só Java puro:

-   Comunicação via `InputStream` e `OutputStream`;
-   Comunicação através de arquivos;
-   Comunicação TCP utilizando sockets;
-   Serialização de objetos e chamadas remotas (cliente-servidor);
-   Comunicação UDP multicast;
-   Sistema distribuído de votação utilizando TCP + UDP multicast.

O serviço remoto escolhido foi:

> **Clínica Veterinária** - Sistema para cadastro de animais, consultas e
> controle do estoque de medicamentos.

| Item        | Definição                                       |
|-------------|-------------------------------------------------|
| Aplicação   | Sistema Clínica Veterinária                     |
| Superclasse | `Animal` (abstrata)                             |
| Subclasses  | `Cachorro`, `Gato`, `Coelho`                    |
| Agregação   | `Estoque` agrega `Medicamento`                  |
| Interface   | `Consulta` (implementada por `ConsultaServico`) |

# Requisitos e dependências

O projeto não utiliza Maven, Gradle ou bibliotecas externas. Tudo foi feito
com a biblioteca padrão do Java, inclusive o JSON (classe `clinica.util.Json`).

## Dependência obrigatória

### Java Development Kit (JDK)

É necessário o JDK 11 ou superior. Para conferir:

``` bash
java -version
javac -version
```

## Compilação

Na raiz do projeto:

``` bash
./compilar.sh          # Linux/macOS
bash compilar.sh          # Windows (no Git Bash)
```

As classes compiladas vão para `./out`. Todos os comandos de execução abaixo
devem ser rodados a partir da raiz do projeto.

> No Windows, rode `chcp 65001` (no PowerShell) inicialmente para acentuação das palavras ocorrer corretamente.

# Estrutura do projeto

    src/clinica/
    ├── modelo/            POJOs
    │   ├── Animal, Cachorro, Gato, Coelho
    │   ├── Medicamento, Estoque
    │   └── RegistroConsulta
    ├── servico/           Interface e classes de serviço
    │   ├── Consulta, ConsultaServico
    │   ├── EstoqueServico
    │   └── DadosIniciais
    ├── stream/            Questões 1 e 2
    │   ├── EstoqueOutputStream, EstoqueInputStream, CodecMedicamento
    │   └── teste/         programas Q1b..Q2d
    ├── serializacao/      Questão 3
    │   ├── Operacao, Requisicao, Resposta, Empacotador
    │   └── ServidorVeterinaria, ClienteVeterinaria, DemoCliente
    ├── multicast/         Questão 4
    │   ├── ServidorMulticast, ClienteMulticast, MonitorEstoque
    │   └── Notificacao, Grupo, OuvinteMulticast, RedeMulticast
    ├── votacao/           Questão extra
    │   ├── ServidorVotacao, ClienteEleitor, ClienteAdmin, ClienteVotacao
    │   └── Urna, Candidato, Resultado, Contas, Canal, RespostaVotacao
    ├── util/Json
    └── teste/             TesteAutomatizado, TesteMulticastVotacao

    compilar.sh
    compilar.bat
    README.md

Os POJOs são `Animal` (com as três subclasses), `Medicamento`, `Estoque` e
`RegistroConsulta`. As classes de serviço são `ConsultaServico` e
`EstoqueServico`.

# Exercícios 1 e 2 - Streams e Sockets

Foram implementadas as classes:

-   `EstoqueOutputStream(OutputStream destino, Medicamento[] array, int n)`
-   `EstoqueInputStream(InputStream origem)`

A primeira envia os `n` primeiros medicamentos do array para o destino. A
segunda lê esses bytes e reconstrói o array.

O formato é texto UTF-8, uma informação por linha, para dar para ler a saída
no terminal e também digitar o conteúdo na entrada padrão:

    ESTOQUE-V1                                       <- cabeçalho
    N                                                <- número de objetos
    id;nome;fabricante;quantidade;preco;validade     <- N linhas

Campos com `;`, `\` ou quebra de linha são escapados. O leitor para logo
depois do último objeto anunciado, sem esperar o fim do stream, então
funciona com teclado e com sockets que continuam abertos.

## Saída e entrada padrão

``` bash
java -cp out clinica.stream.teste.Q1bEnviarParaSaidaPadrao 3
java -cp out clinica.stream.teste.Q1bEnviarParaSaidaPadrao | java -cp out clinica.stream.teste.Q2bLerDaEntradaPadrao
```

O argumento opcional é o número de objetos enviados.

## Arquivos

Escrita:

``` bash
java -cp out clinica.stream.teste.Q1cEnviarParaArquivo estoque.txt
```

Leitura:

``` bash
java -cp out clinica.stream.teste.Q2cLerDeArquivo estoque.txt
```

## TCP

Servidor (lê do cliente):

``` bash
java -cp out clinica.stream.teste.Q2dReceberDeClienteTcp 5000
```

Cliente (envia):

``` bash
java -cp out clinica.stream.teste.Q1dEnviarParaServidorTcp localhost 5000
```

# Exercício 3 - Serialização cliente-servidor

Comunicação por sockets TCP trocando mensagens serializadas:

-   `Requisicao` e `Resposta` são `Serializable` e passam por `ObjectOutputStream` / `ObjectInputStream` dentro da classe `Empacotador`;
-   o cliente empacota o request e desempacota o reply; o servidor faz o inverso;
-   como o TCP não tem fronteira de mensagem, cada mensagem vai com 4 bytes de tamanho na frente;
-   o desempacotamento usa `ObjectInputFilter`, aceitando só classes do pacote `clinica` e tipos básicos do JDK;
-   o servidor atende cada cliente em uma thread e os serviços são `synchronized`.

Operações remotas: registrar, buscar e listar animais; realizar consulta
(dá baixa no estoque); histórico do animal; cadastrar e listar medicamentos.
Erros de negócio (animal inexistente, estoque insuficiente, medicamento
vencido) voltam na `Resposta` e viram `ErroRemotoException` no cliente.

Servidor:

``` bash
java -cp out clinica.serializacao.ServidorVeterinaria 6000
```

Cliente de demonstração:

``` bash
java -cp out clinica.serializacao.DemoCliente localhost 6000
```

# Exercício 4 - UDP Multicast

Implementação utilizando:

-   TCP para autenticação (usuário e senha);
-   UDP multicast para as mensagens `NOTIFICACAO`, `ALERTA` e `ATUALIZACAO`.

Configuração:

    Grupo multicast: 230.0.0.1:4446
    Porta TCP (autenticação): 5002

Usuários cadastrados:

| Usuário       | Senha    |
|---------------|----------|
| `veterinario` | `vet123` |
| `recepcao`    | `rec123` |
| `estoquista`  | `est123` |

Formato das mensagens (JSON):

``` json
{"tipo":"NOTIFICACAO","mensagem":"Novo dado disponível","timestamp":123456789}
```

Como funciona:

-   O cliente se autentica por TCP e o servidor responde com o endereço e a porta do grupo;
-   o cliente faz `joinGroup` e fica com **duas threads**: uma escuta o multicast (`OuvinteMulticast`) e a principal lê os comandos do usuário;
-   o cliente pode sair (`leaveGroup`) e voltar ao grupo quando quiser;
-   o servidor envia por um pool de threads, então console e monitor de estoque podem mandar mensagens ao mesmo tempo;
-   o `MonitorEstoque` confere o estoque a cada 10 s e manda um `ALERTA` quando um medicamento está com 10 unidades ou menos ou vencido (uma vez por problema).

Servidor:

``` bash
java -cp out clinica.multicast.ServidorMulticast
```

Cliente (abra em quantos terminais quiser):

``` bash
java -cp out clinica.multicast.ClienteMulticast localhost
```

Comandos do servidor:

    NOTIFICACAO <mensagem>      envia ao grupo (também ALERTA e ATUALIZACAO)
    baixar <id> <qtd>           dá baixa no estoque
    repor <id> <qtd>            repõe o estoque e envia uma ATUALIZACAO
    estoque | clientes | ajuda | sair

Comandos do cliente: `entrar`, `deixar`, `status`, `sair`.

Sugestão de demonstração: abrir o servidor e dois clientes, mandar uma
mensagem de cada tipo, usar `deixar` em um dos clientes e mandar outra
mensagem (só o outro recebe), depois `entrar` de novo. Em seguida, `baixar 3 3`
no servidor: em até 10 s os clientes recebem o alerta de estoque baixo.

> **Rede:** o programa escolhe sozinho uma interface de rede com suporte a
> multicast (dando preferência às físicas). Se as mensagens não chegarem,
> principalmente com VPN ou máquinas virtuais ativas, escolha a interface
> manualmente, nos dois lados:
> `java -Dclinica.interface=<nome> -cp out ...` (ex.: `eth0`, `wlan0`, `en0`).
> Para rodar em máquinas diferentes, o firewall precisa liberar a porta TCP
> 5002 e a porta UDP 4446.

# Questão extra - Sistema de votação distribuído

Tema da demonstração: **Pet do Mês** da clínica. Os candidatos são os pets e
os eleitores são os tutores.

Implementa:

-   TCP unicast para login, lista de candidatos e votos;
-   UDP multicast **somente** para as notas informativas dos administradores;
-   representação externa em JSON (uma mensagem por linha);
-   servidor multithreading (uma thread por cliente).

Configuração:

    Porta TCP: 5003
    Grupo multicast: 230.0.0.2:4447
    Prazo da votação: argumento do servidor, em segundos (padrão 120)

Contas de demonstração:

| Papel         | Usuários                                   | Senha      |
|---------------|--------------------------------------------|------------|
| Eleitor       | `carlos`, `ana`, `beatriz`, `paula`, `rui` | `1234`     |
| Administrador | `admin`                                    | `admin123` |

Regras:

-   o login do eleitor já devolve a lista de candidatos, o tempo restante e o grupo multicast;
-   cada eleitor vota uma única vez (vale por usuário, não por conexão) e administradores não votam;
-   o administrador adiciona e remove candidatos e envia notas; se remover um candidato que já tinha votos, esses eleitores podem votar de novo;
-   passado o prazo, o servidor não aceita mais votos e calcula o total, o percentual de cada candidato e o vencedor (ou o empate). O resultado aparece no console do servidor e no menu "Ver resultado" dos clientes;
-   o resultado só é divulgado depois do fim da votação.

Exemplo de mensagens trocadas:

``` json
{"op":"LOGIN","usuario":"carlos","senha":"1234"}
{"sucesso":true,"mensagem":"Bem-vindo, carlos.","candidatos":[{"id":1,"nome":"Rex","descricao":"Cachorro, Labrador"}],"restante":110,"papel":"ELEITOR","grupo":"230.0.0.2","porta":4447}
{"op":"VOTAR","candidato":1}
{"sucesso":true,"mensagem":"Voto registrado em Rex."}
```

Servidor (votação aberta por 120 s):

``` bash
java -cp out clinica.votacao.ServidorVotacao 120
```

Eleitor:

``` bash
java -cp out clinica.votacao.ClienteEleitor localhost
```

Administrador:

``` bash
java -cp out clinica.votacao.ClienteAdmin localhost
```

O eleitor também tem duas threads: uma para o menu e outra escutando as notas
do administrador, que aparecem na tela na hora, mesmo no meio do menu. Vale a
mesma observação de rede da questão 4.

# Testes automatizados

Não precisam de nenhuma biblioteca:

``` bash
java -cp out clinica.teste.TesteAutomatizado
java -cp out clinica.teste.TesteMulticastVotacao
```

`TesteAutomatizado` (questões 1 a 3) cobre ida e volta por `System.out` →
`System.in`, arquivo e TCP; subconjunto de objetos; array vazio; validação do
construtor; stream truncado ou corrompido; preservação das subclasses;
consulta com baixa de estoque; erros de negócio; 8 clientes simultâneos e
rejeição de classes não permitidas. Saída esperada:

    Resultado: 14 passou, 0 falhou.

`TesteMulticastVotacao` (questão 4 e extra) cobre JSON e formato da mensagem;
autenticação; vários clientes recebendo os três tipos de mensagem;
`joinGroup`/`leaveGroup`; envio concorrente de 4 threads; alertas do monitor;
regras da urna (voto único, remoção, empate, percentuais); permissões de
eleitor e administrador; prazo; 40 eleitores votando ao mesmo tempo e nota do
administrador chegando por multicast. Saída esperada:

    Resultado: 15 passou, 0 falhou.

Se a máquina não tiver nenhuma interface com multicast, os testes que dependem
dele aparecem como `[PULOU]` em vez de falhar.
