"""Banco SQLite do Estoque.

A baixa e o id da mensagem são gravados na mesma transação. Se a mensagem chegar de novo
(queda depois do COMMIT e antes do ACK), o id já está em mensagens_processadas e a baixa
não se repete.
"""
import sqlite3
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

# Ids e saldos do TD02 (só medicamentos e vacinas).
PRODUTOS_INICIAIS = [
    (1, "Amoxicilina 250mg", "MEDICAMENTO", 50),
    (2, "Vermífugo Oral", "MEDICAMENTO", 30),
    (3, "Anti-inflamatório 10mg", "MEDICAMENTO", 5),
    (4, "Pomada cicatrizante", "MEDICAMENTO", 8),
    (5, "Antirrábica", "VACINA", 40),
    (6, "V10 Polivalente", "VACINA", 20),
    (9, "Tramadol 50mg", "MEDICAMENTO", 25),
]

ESQUEMA = """
CREATE TABLE IF NOT EXISTS produtos (
    id          INTEGER PRIMARY KEY,
    nome        TEXT    NOT NULL,
    tipo        TEXT    NOT NULL CHECK (tipo IN ('MEDICAMENTO', 'VACINA')),
    quantidade  INTEGER NOT NULL CHECK (quantidade >= 0)
);
CREATE TABLE IF NOT EXISTS mensagens_processadas (
    id_mensagem   TEXT    PRIMARY KEY,
    id_consulta   INTEGER NOT NULL,
    id_produto    INTEGER NOT NULL,
    quantidade    INTEGER NOT NULL,
    situacao      TEXT    NOT NULL CHECK (situacao IN ('APLICADA', 'REJEITADA')),
    detalhe       TEXT,
    saldo_apos    INTEGER,
    processada_em TEXT    NOT NULL
);
"""


@dataclass(frozen=True)
class Resultado:
    situacao: str          # APLICADA, REJEITADA ou DUPLICADA
    detalhe: str
    produto: str | None
    saldo: int | None


def conectar(caminho: Path) -> sqlite3.Connection:
    caminho.parent.mkdir(parents=True, exist_ok=True)
    conexao = sqlite3.connect(caminho, timeout=10, isolation_level=None)  # transações explícitas
    conexao.execute("PRAGMA journal_mode=WAL")
    conexao.execute("PRAGMA synchronous=FULL")
    conexao.executescript(ESQUEMA)
    if conexao.execute("SELECT COUNT(*) FROM produtos").fetchone()[0] == 0:
        _semear(conexao)
    return conexao


def _semear(conexao: sqlite3.Connection) -> None:
    conexao.execute("BEGIN IMMEDIATE")
    conexao.executemany("INSERT INTO produtos (id, nome, tipo, quantidade) VALUES (?, ?, ?, ?)",
                        PRODUTOS_INICIAIS)
    conexao.execute("COMMIT")


def restaurar(conexao: sqlite3.Connection) -> None:
    """Volta aos saldos iniciais e esquece as mensagens já processadas."""
    conexao.execute("BEGIN IMMEDIATE")
    conexao.execute("DELETE FROM mensagens_processadas")
    conexao.execute("DELETE FROM produtos")
    conexao.executemany("INSERT INTO produtos (id, nome, tipo, quantidade) VALUES (?, ?, ?, ?)",
                        PRODUTOS_INICIAIS)
    conexao.execute("COMMIT")


def listar_produtos(conexao: sqlite3.Connection):
    return conexao.execute("SELECT id, nome, tipo, quantidade FROM produtos ORDER BY id").fetchall()


def buscar_produto(conexao: sqlite3.Connection, id_produto: int):
    return conexao.execute("SELECT id, nome, tipo, quantidade FROM produtos WHERE id = ?",
                           (id_produto,)).fetchone()


def historico(conexao: sqlite3.Connection):
    return conexao.execute(
        "SELECT processada_em, id_mensagem, id_consulta, id_produto, quantidade, situacao, "
        "saldo_apos, detalhe FROM mensagens_processadas ORDER BY processada_em").fetchall()


def aplicar_baixa(conexao: sqlite3.Connection, id_mensagem: str, id_consulta: int,
                  id_produto: int, quantidade: int) -> Resultado:
    """Aplica a baixa uma única vez por id_mensagem. sqlite3.Error em falha técnica."""
    conexao.execute("BEGIN IMMEDIATE")
    try:
        anterior = conexao.execute(
            "SELECT situacao, saldo_apos, processada_em FROM mensagens_processadas WHERE id_mensagem = ?",
            (id_mensagem,)).fetchone()
        if anterior is not None:
            conexao.execute("ROLLBACK")
            produto = buscar_produto(conexao, id_produto)
            return Resultado("DUPLICADA",
                             f"já processada em {anterior[2]} (situação {anterior[0]}); nada foi alterado",
                             produto[1] if produto else None, produto[3] if produto else None)

        produto = buscar_produto(conexao, id_produto)
        nome = produto[1] if produto else None
        if produto is None:
            situacao, detalhe, saldo = "REJEITADA", f"produto #{id_produto} não existe", None
        elif quantidade <= 0:
            situacao, detalhe, saldo = "REJEITADA", f"quantidade inválida ({quantidade})", produto[3]
        elif produto[3] < quantidade:
            situacao, detalhe, saldo = ("REJEITADA",
                                        f"saldo insuficiente: disponível {produto[3]}, solicitado {quantidade}",
                                        produto[3])
        else:
            saldo = produto[3] - quantidade
            conexao.execute("UPDATE produtos SET quantidade = ? WHERE id = ?", (saldo, id_produto))
            situacao, detalhe = "APLICADA", f"saldo {produto[3]} -> {saldo}"

        conexao.execute(
            "INSERT INTO mensagens_processadas (id_mensagem, id_consulta, id_produto, quantidade, "
            "situacao, detalhe, saldo_apos, processada_em) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (id_mensagem, id_consulta, id_produto, quantidade, situacao, detalhe, saldo,
             datetime.now().isoformat(sep=" ", timespec="milliseconds")))
        conexao.execute("COMMIT")
        return Resultado(situacao, detalhe, nome, saldo)
    except BaseException:
        if conexao.in_transaction:
            conexao.execute("ROLLBACK")
        raise
