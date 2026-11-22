"""Entidades do lado do cliente.

São as cópias locais dos objetos do servidor: o que chega em JSON vira objeto Python e o que
vai ao servidor por valor é montado aqui. A herança (Medicamento, Vacina e Insumo são
ItemEstoque) e a agregação (todo item tem um Fornecedor) seguem o modelo do servidor.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from typing import Optional


@dataclass
class Fornecedor:
    id: int = 0
    nome: Optional[str] = None
    cnpj: Optional[str] = None
    telefone: Optional[str] = None

    def para_json(self) -> dict:
        return {"id": self.id, "nome": self.nome, "cnpj": self.cnpj, "telefone": self.telefone}

    @staticmethod
    def de_json(d: dict) -> "Fornecedor":
        return Fornecedor(d["id"], d.get("nome"), d.get("cnpj"), d.get("telefone"))


@dataclass
class ItemEstoque:
    """Superclasse de tudo o que é guardado no estoque."""

    id: int = 0
    nome: str = ""
    fornecedor: Optional[Fornecedor] = None  # agregação ("tem-um")
    quantidade: int = 0
    estoque_minimo: int = 0
    preco_unitario: float = 0.0
    validade: Optional[str] = None  # AAAA-MM-DD

    classe = "ITEM"
    categoria = "Item"
    dias_aviso_validade = 30

    def detalhes(self) -> str:
        return ""

    def vencido(self, hoje: date) -> bool:
        return self.validade is not None and date.fromisoformat(self.validade) < hoje

    def vence_em_breve(self, hoje: date) -> bool:
        if self.validade is None or self.vencido(hoje):
            return False
        return date.fromisoformat(self.validade) <= hoje + timedelta(days=self.dias_aviso_validade)

    def abaixo_do_minimo(self) -> bool:
        return self.quantidade <= self.estoque_minimo

    def situacao(self, hoje: date) -> str:
        """Avisos exibidos na listagem (os mesmos que o monitor do servidor usa nos alertas)."""
        texto = ""
        if self.vencido(hoje):
            texto += " <VENCIDO>"
        elif self.vence_em_breve(hoje):
            texto += " <vence em breve>"
        if self.abaixo_do_minimo():
            texto += " <estoque baixo>"
        return texto

    def campos_proprios(self) -> dict:
        return {}

    def para_json(self) -> dict:
        d = {
            "classe": self.classe,
            "id": self.id,
            "nome": self.nome,
            "fornecedor": self.fornecedor.para_json() if self.fornecedor else None,
            "quantidade": self.quantidade,
            "estoqueMinimo": self.estoque_minimo,
            "precoUnitario": self.preco_unitario,
            "validade": self.validade,
        }
        d.update(self.campos_proprios())
        return d

    def __str__(self) -> str:
        forn = "-"
        if self.fornecedor:
            forn = self.fornecedor.nome or f"#{self.fornecedor.id}"
        return (f"{self.categoria} #{self.id} '{self.nome}' qtd={self.quantidade} (mín. {self.estoque_minimo}) "
                f"R$ {self.preco_unitario:.2f} val={self.validade or '-'} fornecedor={forn} [{self.detalhes()}]")


@dataclass
class Medicamento(ItemEstoque):
    principio_ativo: str = ""
    concentracao: str = ""
    controlado: bool = False

    classe = "MEDICAMENTO"
    categoria = "Medicamento"

    def campos_proprios(self) -> dict:
        return {"principioAtivo": self.principio_ativo, "concentracao": self.concentracao,
                "controlado": self.controlado}

    def detalhes(self) -> str:
        return f"princípio ativo: {self.principio_ativo}, {self.concentracao}" + (", CONTROLADO" if self.controlado else "")


@dataclass
class Vacina(ItemEstoque):
    doenca_alvo: str = ""
    doses_por_frasco: int = 1

    classe = "VACINA"
    categoria = "Vacina"
    dias_aviso_validade = 60

    def campos_proprios(self) -> dict:
        return {"doencaAlvo": self.doenca_alvo, "dosesPorFrasco": self.doses_por_frasco}

    def detalhes(self) -> str:
        return f"doença-alvo: {self.doenca_alvo}, {self.doses_por_frasco} dose(s) por frasco"


@dataclass
class Insumo(ItemEstoque):
    unidade_medida: str = ""
    esteril: bool = False

    classe = "INSUMO"
    categoria = "Insumo"
    dias_aviso_validade = 15

    def campos_proprios(self) -> dict:
        return {"unidadeMedida": self.unidade_medida, "esteril": self.esteril}

    def detalhes(self) -> str:
        return f"unidade: {self.unidade_medida}" + (", estéril" if self.esteril else "")


def item_de_json(d: dict) -> ItemEstoque:
    """Reconstrói o item na subclasse indicada pelo campo discriminador 'classe'."""
    base = dict(
        id=d["id"], nome=d.get("nome") or "",
        fornecedor=Fornecedor.de_json(d["fornecedor"]) if d.get("fornecedor") else None,
        quantidade=d["quantidade"], estoque_minimo=d["estoqueMinimo"],
        preco_unitario=float(d["precoUnitario"]), validade=d.get("validade"),
    )
    classe = d.get("classe")
    if classe == "MEDICAMENTO":
        return Medicamento(**base, principio_ativo=d.get("principioAtivo") or "",
                           concentracao=d.get("concentracao") or "", controlado=bool(d.get("controlado")))
    if classe == "VACINA":
        return Vacina(**base, doenca_alvo=d.get("doencaAlvo") or "", doses_por_frasco=d.get("dosesPorFrasco", 1))
    if classe == "INSUMO":
        return Insumo(**base, unidade_medida=d.get("unidadeMedida") or "", esteril=bool(d.get("esteril")))
    raise ValueError(f"Classe de item desconhecida: {classe!r}")


def _hora(ms: int, formato: str) -> str:
    return datetime.fromtimestamp(ms / 1000).strftime(formato)


@dataclass
class Movimentacao:
    id: int
    tipo: str
    id_item: int
    nome_item: Optional[str]
    quantidade: int
    saldo_apos: int
    responsavel: Optional[str]
    motivo: Optional[str]
    instante: int

    @staticmethod
    def de_json(d: dict) -> "Movimentacao":
        return Movimentacao(d["id"], d["tipo"], d["idItem"], d.get("nomeItem"), d["quantidade"], d["saldoApos"],
                            d.get("responsavel"), d.get("motivo"), d["instante"])

    def __str__(self) -> str:
        motivo = f" | motivo: {self.motivo}" if self.motivo and self.motivo.strip() else ""
        return (f"Mov. #{self.id} [{_hora(self.instante, '%d/%m %H:%M:%S')}] {self.tipo} de {self.quantidade} un. de "
                f"'{self.nome_item}' (item #{self.id_item}) saldo={self.saldo_apos} por {self.responsavel}{motivo}")


@dataclass
class ItemPedido:
    id_item: int
    nome_item: Optional[str]
    quantidade: int


@dataclass
class PedidoReposicao:
    id: int
    fornecedor: Fornecedor  # agregação
    itens: list[ItemPedido] = field(default_factory=list)  # agregação
    status: str = "ABERTO"
    solicitante: Optional[str] = None
    criado_em: int = 0

    @staticmethod
    def de_json(d: dict) -> "PedidoReposicao":
        itens = [ItemPedido(i["idItem"], i.get("nomeItem"), i["quantidade"]) for i in d["itens"]]
        return PedidoReposicao(d["id"], Fornecedor.de_json(d["fornecedor"]), itens, d["status"],
                               d.get("solicitante"), d["criadoEm"])

    def __str__(self) -> str:
        linhas = ", ".join(f"{i.quantidade}x '{i.nome_item}' (item #{i.id_item})" for i in self.itens) or "sem itens"
        return f"Pedido #{self.id} [{self.status}] fornecedor={self.fornecedor.nome} solicitante={self.solicitante}: {linhas}"


@dataclass
class Notificacao:
    id: int
    tipo: str
    mensagem: str
    timestamp: int

    @staticmethod
    def de_json(d: dict) -> "Notificacao":
        return Notificacao(d["id"], d["tipo"], d["mensagem"], d["timestamp"])

    def __str__(self) -> str:
        return f"[{_hora(self.timestamp, '%H:%M:%S')}] [{self.tipo}] {self.mensagem}"


@dataclass
class Sessao:
    token: str
    usuario: str
    inicio: int

    @staticmethod
    def de_json(d: dict) -> "Sessao":
        return Sessao(d["token"], d["usuario"], d["inicio"])
