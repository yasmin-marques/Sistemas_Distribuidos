"""Configuração do componente Estoque: broker (mesmo .env de Consultas) e caminho do banco SQLite."""
import os
from dataclasses import dataclass
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent  # pasta td_04
BANCO_PADRAO = Path(__file__).resolve().parent / "dados" / "estoque.db"


def _ler_env(arquivo: Path) -> dict:
    valores = {}
    if arquivo.is_file():
        for linha in arquivo.read_text(encoding="utf-8").splitlines():
            linha = linha.strip()
            if not linha or linha.startswith("#") or "=" not in linha:
                continue
            chave, valor = linha.split("=", 1)
            valores[chave.strip()] = valor.strip()
    return valores


@dataclass(frozen=True)
class Configuracao:
    host: str
    porta: int
    usuario: str
    senha: str
    vhost: str
    fila: str
    banco: Path

    def descricao(self) -> str:
        return f"{self.host}:{self.porta} (vhost '{self.vhost}', fila '{self.fila}')"


def carregar() -> Configuracao:
    arquivo = _ler_env(RAIZ / ".env")

    def valor(chave, padrao):
        return (os.environ.get(chave) or "").strip() or arquivo.get(chave) or padrao

    return Configuracao(
        host=valor("RABBITMQ_HOST", "localhost"),
        porta=int(valor("RABBITMQ_PORT", "5672")),
        usuario=valor("RABBITMQ_USER", "clinica"),
        senha=valor("RABBITMQ_PASSWORD", "clinica123"),
        vhost=valor("RABBITMQ_VHOST", "/"),
        fila=valor("RABBITMQ_QUEUE", "estoque.baixas"),
        banco=Path(valor("ESTOQUE_DB", str(BANCO_PADRAO))),
    )
