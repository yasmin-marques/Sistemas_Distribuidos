"""Proxies (stubs) dos objetos distribuídos do servidor.

Cada proxy tem os mesmos métodos do objeto remoto; por baixo, cada método vira uma requisição
HTTP. Objetos que ficam no servidor (pedido de reposição, assinatura) são usados POR REFERÊNCIA:
o proxy guarda só o endereço (href) que o servidor devolveu e toda chamada atua no objeto original.
"""

from __future__ import annotations

from typing import Optional

from .http import ClienteHttp
from .modelo import (ItemEstoque, Fornecedor, Movimentacao, Notificacao, PedidoReposicao, Sessao,
                     item_de_json)


class ProxyBase:
    def __init__(self, http: ClienteHttp, href: str):
        self.http = http
        self.href = href

    def __repr__(self) -> str:
        return f"{type(self).__name__} -> {self.href}"


class ProxyPorReferencia(ProxyBase):
    """Proxy de objeto criado em tempo de execução, identificado pela referência recebida."""

    def __init__(self, http: ClienteHttp, referencia: dict):
        super().__init__(http, referencia["href"])
        self.referencia = referencia

    @property
    def objeto(self) -> str:
        return self.referencia["objeto"]

    def __repr__(self) -> str:
        return f"{self.objeto} ({self.referencia['interface']}) @ {self.href}"

    def __eq__(self, outro: object) -> bool:
        return isinstance(outro, ProxyPorReferencia) and outro.href == self.href

    def __hash__(self) -> int:
        return hash(self.href)


class AutenticacaoProxy(ProxyBase):
    def autenticar(self, usuario: str, senha: str) -> Sessao:
        return Sessao.de_json(self.http.do_operation("POST", self.href + "/sessoes",
                                                     {"usuario": usuario, "senha": senha}))

    def encerrar_sessao(self, token: str) -> None:
        self.http.do_operation("DELETE", self.href + "/sessoes/atual", token=token)


class PedidoReposicaoProxy(ProxyPorReferencia):
    def consultar(self) -> PedidoReposicao:
        return PedidoReposicao.de_json(self.http.do_operation("GET", self.href))

    def adicionar_item(self, token: str, id_item: int, quantidade: int) -> PedidoReposicao:
        return PedidoReposicao.de_json(self.http.do_operation(
            "POST", self.href + "/itens", {"idItem": id_item, "quantidade": quantidade}, token))

    def confirmar_recebimento(self, token: str) -> list[Movimentacao]:
        return [Movimentacao.de_json(m) for m in self.http.do_operation("POST", self.href + "/recebimento", token=token)]

    def cancelar(self, token: str) -> PedidoReposicao:
        return PedidoReposicao.de_json(self.http.do_operation("POST", self.href + "/cancelamento", token=token))


class AssinaturaProxy(ProxyPorReferencia):
    def aguardar(self, espera_ms: int) -> list[Notificacao]:
        """Long polling: a resposta só volta quando há mensagem ou a espera termina."""
        corpo = self.http.do_operation("GET", f"{self.href}/mensagens?espera={int(espera_ms)}",
                                       tempo_limite=espera_ms / 1000 + 15)
        return [Notificacao.de_json(n) for n in corpo]

    def usuario(self) -> str:
        return self.http.do_operation("GET", self.href)["usuario"]


class EstoqueProxy(ProxyBase):
    def listar_itens(self) -> list[ItemEstoque]:
        return [item_de_json(i) for i in self.http.do_operation("GET", self.href + "/itens")]

    def buscar_item(self, id_item: int) -> ItemEstoque:
        return item_de_json(self.http.do_operation("GET", f"{self.href}/itens/{id_item}"))

    def listar_fornecedores(self) -> list[Fornecedor]:
        return [Fornecedor.de_json(f) for f in self.http.do_operation("GET", self.href + "/fornecedores")]

    def cadastrar_item(self, token: str, item: ItemEstoque) -> ItemEstoque:
        """Passagem por VALOR: o item local vai como JSON; volta outra cópia, com o id gerado."""
        return item_de_json(self.http.do_operation("POST", self.href + "/itens", item.para_json(), token))

    def registrar_entrada(self, token: str, id_item: int, quantidade: int) -> Movimentacao:
        return Movimentacao.de_json(self.http.do_operation(
            "POST", f"{self.href}/itens/{id_item}/entradas", {"quantidade": quantidade}, token))

    def registrar_saida(self, token: str, id_item: int, quantidade: int, motivo: Optional[str] = None) -> Movimentacao:
        corpo = {"quantidade": quantidade}
        if motivo:
            corpo["motivo"] = motivo
        return Movimentacao.de_json(self.http.do_operation("POST", f"{self.href}/itens/{id_item}/saidas", corpo, token))

    def listar_movimentacoes(self, id_item: int = 0) -> list[Movimentacao]:
        sufixo = f"?item={id_item}" if id_item else ""
        return [Movimentacao.de_json(m) for m in self.http.do_operation("GET", self.href + "/movimentacoes" + sufixo)]

    def abrir_pedido_reposicao(self, token: str, id_fornecedor: int) -> PedidoReposicaoProxy:
        """Passagem por REFERÊNCIA: o pedido fica no servidor; recebemos o endereço dele."""
        ref = self.http.do_operation("POST", self.href + "/pedidos", {"idFornecedor": id_fornecedor}, token)
        return PedidoReposicaoProxy(self.http, ref)


class NotificacoesProxy(ProxyBase):
    def entrar_grupo(self, token: str) -> AssinaturaProxy:
        """joinGroup: devolve a referência da assinatura criada no servidor."""
        ref = self.http.do_operation("POST", self.href + "/assinaturas", token=token)
        return AssinaturaProxy(self.http, ref)

    def sair_grupo(self, assinatura: AssinaturaProxy) -> None:
        """leaveGroup: a referência vai como ARGUMENTO e o servidor a converte no objeto real."""
        self.http.do_operation("POST", self.href + "/saida", {"assinatura": assinatura.referencia})

    def publicar(self, token: str, tipo: str, mensagem: str) -> Notificacao:
        return Notificacao.de_json(self.http.do_operation(
            "POST", self.href + "/mensagens", {"tipo": tipo, "mensagem": mensagem}, token))

    def membros(self) -> list[str]:
        return self.http.do_operation("GET", self.href + "/membros")


class ConexaoClinica:
    """Descobre os objetos distribuídos do servidor (como o lookup do registro RMI do TD02)."""

    def __init__(self, base_url: str):
        self.http = ClienteHttp(base_url)
        indice = self.http.do_operation("GET", "/api")
        enderecos = {o["objeto"]: o["href"] for o in indice["objetos"]}
        self.autenticacao = AutenticacaoProxy(self.http, enderecos["Autenticacao"])
        self.estoque = EstoqueProxy(self.http, enderecos["Estoque"])
        self.notificacoes = NotificacoesProxy(self.http, enderecos["Notificacoes"])

    def __str__(self) -> str:
        return self.http.base_url
