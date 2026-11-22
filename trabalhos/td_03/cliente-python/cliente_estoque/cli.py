"""Cliente de terminal do estoque veterinário (TD03, Python).

Duas threads: esta, que interage com o usuário, e a do OuvinteNotificacoes, que mostra as
mensagens do grupo assim que chegam. Vários clientes podem rodar ao mesmo tempo.

    python -m cliente_estoque [URL]        (padrão: http://localhost:8080)
"""

from __future__ import annotations

import json
import sys
import threading
from datetime import date
from typing import Optional

from .http import ErroApi, FalhaComunicacao
from .modelo import Fornecedor, Insumo, ItemEstoque, Medicamento, Notificacao, Vacina
from .ouvinte import OuvinteNotificacoes
from .proxies import ConexaoClinica, PedidoReposicaoProxy

TIPOS_NOTIFICACAO = ("NOTIFICACAO", "ALERTA", "ATUALIZACAO")

AJUDA = """Comandos:
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
  protocolo on|off               mostra as requisições e respostas HTTP trocadas
  ajuda | sair"""


class ClienteEstoque:
    def __init__(self, conexao: ConexaoClinica):
        self.conexao = conexao
        self.estoque = conexao.estoque
        self.notificacoes = conexao.notificacoes
        self.sessao = None
        self.ouvinte: Optional[OuvinteNotificacoes] = None
        self.pedido: Optional[PedidoReposicaoProxy] = None
        self._saida = threading.Lock()

    # saída (compartilhada pelas duas threads)

    def imprimir(self, texto: str = "") -> None:
        with self._saida:
            print(texto, flush=True)

    def perguntar(self, rotulo: str) -> Optional[str]:
        with self._saida:
            print(rotulo, end="", flush=True)
        try:
            return input()
        except EOFError:
            return None

    # ciclo principal

    def executar(self) -> int:
        self.imprimir(f"Cliente do estoque veterinário (TD03 - API REST, Python) - servidor {self.conexao}")
        try:
            if not self.autenticar():
                return 1
            self.entrar_no_grupo()
            self.imprimir(AJUDA)
            while True:
                linha = self.perguntar("")
                if linha is None:
                    break
                try:
                    if not self.processar(linha.strip()):
                        break
                except ErroApi as e:
                    self.imprimir(f"Erro do servidor ({e.codigo}): {e.mensagem}")
                except FalhaComunicacao as e:
                    self.imprimir(f"Falha de comunicação: {e}")
                except ValueError as e:
                    self.imprimir(f"Entrada inválida: {e}")
        except FalhaComunicacao as e:
            self.imprimir(f"Não foi possível falar com o servidor: {e}")
            return 1
        self.encerrar()
        self.imprimir("Até logo!")
        return 0

    def autenticar(self) -> bool:
        for _ in range(3):
            usuario = self.perguntar("Usuário: ")
            if usuario is None:
                return False
            senha = self.perguntar("Senha: ")
            if senha is None:
                return False
            try:
                self.sessao = self.conexao.autenticacao.autenticar(usuario.strip(), senha)
                self.imprimir(f"Autenticado como {self.sessao.usuario}.")
                return True
            except ErroApi as e:
                self.imprimir(f"Autenticação recusada: {e.mensagem}")
        return False

    def entrar_no_grupo(self) -> None:
        if self.ouvinte and self.ouvinte.ativo:
            self.imprimir("Você já está no grupo.")
            return
        assinatura = self.notificacoes.entrar_grupo(self.sessao.token)
        self.ouvinte = OuvinteNotificacoes(assinatura, self.exibir, lambda aviso: self.imprimir(f"[aviso] {aviso}"))
        self.ouvinte.iniciar()
        self.imprimir(f"Você entrou no grupo de notificações (referência: {assinatura}).")

    def sair_do_grupo(self) -> None:
        if not self.ouvinte or not self.ouvinte.ativo:
            self.imprimir("Você não está no grupo.")
            return
        self.ouvinte.parar()
        self.notificacoes.sair_grupo(self.ouvinte.assinatura)
        self.imprimir("Você saiu do grupo (não recebe mais mensagens).")

    def encerrar(self) -> None:
        try:
            if self.ouvinte and self.ouvinte.ativo:
                self.sair_do_grupo()
        except (ErroApi, FalhaComunicacao):
            pass  # o servidor descarta a assinatura sozinho quando o prazo vence
        try:
            if self.sessao:
                self.conexao.autenticacao.encerrar_sessao(self.sessao.token)
        except (ErroApi, FalhaComunicacao):
            pass

    def exibir(self, n: Notificacao) -> None:
        self.imprimir(str(n))

    # comandos

    def processar(self, linha: str) -> bool:
        if not linha:
            return True
        p = linha.split()
        comando = p[0].lower()
        token = self.sessao.token
        if comando == "sair":
            return False
        elif comando == "ajuda":
            self.imprimir(AJUDA)
        elif comando == "itens":
            hoje = date.today()
            for i in self.estoque.listar_itens():
                self.imprimir(f"  {i}{i.situacao(hoje)}")
        elif comando == "item":
            self.imprimir(f"  {self.estoque.buscar_item(inteiro(p, 1, 'id'))}")
        elif comando == "fornecedores":
            for f in self.estoque.listar_fornecedores():
                self.imprimir(f"  Fornecedor #{f.id} {f.nome} CNPJ {f.cnpj} tel. {f.telefone}")
        elif comando == "movimentacoes":
            movs = self.estoque.listar_movimentacoes(inteiro(p, 1, "id") if len(p) > 1 else 0)
            if not movs:
                self.imprimir("  (nenhuma movimentação)")
            for m in movs:
                self.imprimir(f"  {m}")
        elif comando == "entrada":
            self.imprimir(f"Registrado: {self.estoque.registrar_entrada(token, inteiro(p, 1, 'id'), inteiro(p, 2, 'quantidade'))}")
        elif comando == "saida":
            self.imprimir("Registrado: " + str(self.estoque.registrar_saida(
                token, inteiro(p, 1, "id"), inteiro(p, 2, "quantidade"), resto(linha, 3))))
        elif comando == "cadastrar":
            self.cadastrar()
        elif comando == "publicar":
            if len(p) < 3 or p[1].upper() not in TIPOS_NOTIFICACAO:
                raise ValueError("uso: publicar <NOTIFICACAO|ALERTA|ATUALIZACAO> <mensagem>")
            self.notificacoes.publicar(token, p[1].upper(), resto(linha, 2))
        elif comando == "membros":
            self.imprimir(f"Membros do grupo: {self.notificacoes.membros()}")
        elif comando == "entrar":
            self.entrar_no_grupo()
        elif comando == "deixar":
            self.sair_do_grupo()
        elif comando == "status":
            self.imprimir(f"No grupo de notificações como {self.sessao.usuario}." if self.ouvinte and self.ouvinte.ativo
                          else "Fora do grupo.")
        elif comando == "pedido":
            self.comando_pedido(p)
        elif comando == "demo-valor":
            self.demo_valor(inteiro(p, 1, "id") if len(p) > 1 else 1)
        elif comando == "demo-referencia":
            self.demo_referencia()
        elif comando == "protocolo":
            ligar = len(p) > 1 and p[1].lower() == "on"
            self.conexao.http.rastro = (lambda msg: self.imprimir("    " + msg.replace("\n", "\n    "))) if ligar else None
            self.imprimir(f"Exibição das mensagens do protocolo {'ligada' if ligar else 'desligada'}.")
        else:
            self.imprimir("Comando desconhecido. Digite 'ajuda'.")
        return True

    def comando_pedido(self, p: list[str]) -> None:
        acao = p[1].lower() if len(p) > 1 else ""
        token = self.sessao.token
        if acao != "abrir" and self.pedido is None:
            raise ValueError("nenhum pedido aberto; use 'pedido abrir <idFornecedor>'")
        if acao == "abrir":
            self.pedido = self.estoque.abrir_pedido_reposicao(token, inteiro(p, 2, "idFornecedor"))
            self.imprimir(f"Pedido aberto. Referência recebida: {self.pedido}")
            self.imprimir(f"  {self.pedido.consultar()}")
        elif acao == "item":
            self.imprimir(f"  {self.pedido.adicionar_item(token, inteiro(p, 2, 'idItem'), inteiro(p, 3, 'quantidade'))}")
        elif acao == "ver":
            self.imprimir(f"  {self.pedido.consultar()}")
        elif acao == "confirmar":
            for m in self.pedido.confirmar_recebimento(token):
                self.imprimir(f"  {m}")
            self.pedido = None
        elif acao == "cancelar":
            self.imprimir(f"  {self.pedido.cancelar(token)}")
            self.pedido = None
        else:
            raise ValueError("uso: pedido abrir <idFornecedor> | item <idItem> <qtd> | ver | confirmar | cancelar")

    # cadastro (passagem por valor)

    def cadastrar(self) -> None:
        tipo = self.obrigatorio("Tipo (medicamento/vacina/insumo): ").lower()
        if tipo not in ("medicamento", "vacina", "insumo"):
            raise ValueError("tipo deve ser medicamento, vacina ou insumo")
        base = dict(
            nome=self.obrigatorio("Nome: "),
            fornecedor=Fornecedor(self.numero("Id do fornecedor (veja 'fornecedores'): ")),
            quantidade=self.numero("Quantidade inicial: "),
            estoque_minimo=self.numero("Estoque mínimo: "),
            preco_unitario=self.decimal("Preço unitário (ex.: 12.50): "),
            validade=self.data("Validade (AAAA-MM-DD, vazio = sem validade): "),
        )
        if tipo == "medicamento":
            item: ItemEstoque = Medicamento(**base, principio_ativo=self.obrigatorio("Princípio ativo: "),
                                            concentracao=self.obrigatorio("Concentração: "),
                                            controlado=self.sim_nao("Controlado (s/n): "))
        elif tipo == "vacina":
            item = Vacina(**base, doenca_alvo=self.obrigatorio("Doença-alvo: "),
                          doses_por_frasco=self.numero("Doses por frasco: "))
        else:
            item = Insumo(**base, unidade_medida=self.obrigatorio("Unidade de medida: "),
                          esteril=self.sim_nao("Estéril (s/n): "))
        self.imprimir(f"Objeto local criado no cliente: {item}")
        self.imprimir("Enviando por valor (JSON): " + json.dumps(item.para_json(), ensure_ascii=False))
        cadastrado = self.estoque.cadastrar_item(self.sessao.token, item)
        self.imprimir(f"Cópia devolvida pelo servidor: {cadastrado}")
        self.imprimir(f"O objeto local continua com id {item.id}: o servidor trabalhou sobre uma cópia.")

    # demonstrações

    def demo_valor(self, id_item: int) -> None:
        self.imprimir("== Passagem por VALOR ==")
        copia = self.estoque.buscar_item(id_item)
        self.imprimir(f"1) GET do item {id_item} devolveu uma cópia: quantidade={copia.quantidade}, nome='{copia.nome}'")
        copia.quantidade = 99_999
        copia.nome = "ALTERADO SÓ NO CLIENTE"
        self.imprimir(f"2) Alterei a cópia local: quantidade={copia.quantidade}, nome='{copia.nome}'")
        servidor = self.estoque.buscar_item(id_item)
        self.imprimir(f"3) GET de novo: quantidade={servidor.quantidade}, nome='{servidor.nome}'")
        self.imprimir("   O objeto do servidor não mudou: o cliente recebeu uma cópia serializada em JSON.")

    def demo_referencia(self) -> None:
        self.imprimir("== Passagem por REFERÊNCIA ==")
        token = self.sessao.token
        item = self.estoque.listar_itens()[0]
        p1 = self.estoque.abrir_pedido_reposicao(token, item.fornecedor.id)
        self.imprimir("1) abrir pedido devolveu uma REFERÊNCIA (o pedido ficou no servidor):")
        self.imprimir(f"   {p1}")
        p1.adicionar_item(token, item.id, 5)
        self.imprimir(f"2) POST pela referência alterou o objeto no servidor: {p1.consultar().itens}")
        p2 = PedidoReposicaoProxy(self.conexao.http, p1.referencia)
        self.imprimir(f"3) Um segundo proxy criado só com a referência vê o mesmo pedido: {p2.consultar().itens}")
        cancelado = p2.cancelar(token)
        self.imprimir(f"4) Cancelei pelo segundo proxy; o primeiro enxerga: status={p1.consultar().status} (pedido #{cancelado.id})")
        self.imprimir("   Os dois proxies apontam para o MESMO objeto remoto.")

    # entrada de dados

    def obrigatorio(self, rotulo: str) -> str:
        v = self.perguntar(rotulo)
        if v is None or not v.strip():
            raise ValueError("campo obrigatório")
        return v.strip()

    def numero(self, rotulo: str) -> int:
        try:
            return int(self.obrigatorio(rotulo))
        except ValueError:
            raise ValueError("esperava um número inteiro") from None

    def decimal(self, rotulo: str) -> float:
        try:
            return float(self.obrigatorio(rotulo).replace(",", "."))
        except ValueError:
            raise ValueError("esperava um número") from None

    def data(self, rotulo: str) -> Optional[str]:
        v = self.perguntar(rotulo)
        if v is None or not v.strip():
            return None
        try:
            return date.fromisoformat(v.strip()).isoformat()
        except ValueError:
            raise ValueError("data deve estar no formato AAAA-MM-DD") from None

    def sim_nao(self, rotulo: str) -> bool:
        return self.obrigatorio(rotulo).lower().startswith(("s", "y"))


def inteiro(partes: list[str], indice: int, nome: str) -> int:
    if len(partes) <= indice:
        raise ValueError(f"informe {nome}")
    try:
        return int(partes[indice])
    except ValueError:
        raise ValueError(f"{nome} deve ser um número inteiro") from None


def resto(linha: str, pular_palavras: int) -> Optional[str]:
    p = linha.split(None, pular_palavras)
    return p[pular_palavras] if len(p) > pular_palavras else None


def main(argv: Optional[list[str]] = None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    url = argv[0] if argv else "http://localhost:8080"
    if not url.startswith("http"):
        url = "http://" + url
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    try:
        conexao = ConexaoClinica(url)
    except FalhaComunicacao as e:
        print(f"Não foi possível conectar ao servidor em {url}: {e}")
        print("Confira se o servidor está rodando e se o endereço/porta estão certos.")
        return 1
    return ClienteEstoque(conexao).executar()


if __name__ == "__main__":
    sys.exit(main())
