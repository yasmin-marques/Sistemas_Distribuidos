# TD03 — Estoque veterinário como API REST (Sistemas Distribuídos, QXD0043)

Reimplementação do serviço remoto do Trabalho 2 (RMI) como **API REST sobre HTTP com corpo JSON**.
O serviço continua sendo o estoque de uma clínica veterinária: itens (medicamentos, vacinas e insumos),
movimentações, pedidos de reposição ao fornecedor e um grupo de notificações em tempo real.

Três projetos independentes, cada um na sua pasta:

| Projeto | Linguagem | O que é |
|---|---|---|
| [`servidor-java/`](servidor-java) | Java 17 (só JDK, sem dependências) | O serviço: objetos distribuídos expostos pela API |
| [`cliente-python/`](cliente-python) | Python 3.9+ (só biblioteca padrão) | Cliente de terminal |
| [`cliente-javascript/`](cliente-javascript) | JavaScript (navegador, sem build) | Cliente web com interface gráfica |

O servidor não conhece os clientes e os clientes não compartilham código com o servidor: a única ligação
é o contrato HTTP/JSON descrito em [`doc/api.md`](doc/api.md). O relatório está em
[`doc/relatorio.md`](doc/relatorio.md) e o roteiro do vídeo em [`doc/roteiro-video.md`](doc/roteiro-video.md).

## Como rodar

Requisitos: JDK 17+, Python 3.9+, Node 18+ (só para os testes do cliente JS e para servir a página; dá para
abrir `index.html` direto no navegador).

```powershell
# Windows (PowerShell)

# talvez seja necessário rodar antes dos comandos que vêm a seguir
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process 

 # abre servidor, cliente Python e cliente web de uma vez
.\demo.ps1                                  

# ou separado:
servidor-java\servidor.ps1                   # http://localhost:8080/api
cliente-python\cliente.ps1                   # cliente de terminal
node cliente-javascript\servir.js            # cliente web em http://localhost:5500
```

```bash
# Linux / macOS
servidor-java/servidor.sh
cliente-python/cliente.sh
node cliente-javascript/servir.js
```

Usuários de demonstração: `veterinario/vet123`, `recepcao/rec123`, `estoquista/est123`.

Para rodar em duas máquinas (servidor numa, clientes na outra) basta apontar o cliente para o IP do
servidor: `cliente.ps1 -Url http://192.168.0.10:8080`; no cliente web, o endereço é um campo da tela de login.
O servidor escuta em todas as interfaces por padrão (`-Local` restringe à própria máquina); lembre de liberar a
porta 8080 no firewall.

## Testes

```powershell
servidor-java\testes.ps1          # 40 testes do servidor (sobem servidores reais e chamam por HTTP)
.\testes-integrados.ps1           # sobe o servidor e roda os testes do cliente Python (14) e JavaScript (13)
```

(`.sh` equivalentes para Linux/macOS.)
