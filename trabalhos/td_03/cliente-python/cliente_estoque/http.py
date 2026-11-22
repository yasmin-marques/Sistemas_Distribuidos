"""Camada de requisição-resposta do cliente (o doOperation do livro, sobre HTTP).

Cada chamada remota vira uma requisição HTTP com corpo JSON e volta como uma resposta HTTP
com corpo JSON; erros do servidor viram ErroApi. Só usa a biblioteca padrão (urllib).
"""

from __future__ import annotations

import itertools
import json
import threading
import urllib.error
import urllib.request
from typing import Any, Callable, Optional


class ErroApi(Exception):
    """O servidor respondeu com erro: status HTTP + código ('OPERACAO_NEGADA'...) + mensagem."""

    def __init__(self, status: int, codigo: str, mensagem: str):
        super().__init__(f"{codigo}: {mensagem}")
        self.status = status
        self.codigo = codigo
        self.mensagem = mensagem


class FalhaComunicacao(Exception):
    """Não foi possível falar com o servidor (fora do ar, endereço errado, tempo esgotado...)."""


class ClienteHttp:
    def __init__(self, base_url: str, tempo_limite: float = 40.0):
        self.base_url = base_url.rstrip("/")
        self.tempo_limite = tempo_limite
        self._ids = itertools.count(1)
        self._trava = threading.Lock()
        #: função chamada com (texto) a cada mensagem trocada, quando o modo 'protocolo' está ligado
        self.rastro: Optional[Callable[[str], None]] = None

    def url(self, caminho: str) -> str:
        return caminho if caminho.startswith("http") else self.base_url + caminho

    def do_operation(self, metodo: str, caminho: str, corpo: Any = None, token: Optional[str] = None,
                     tempo_limite: Optional[float] = None) -> Any:
        """Envia a requisição e devolve o corpo JSON da resposta (None se não houver corpo)."""
        url = self.url(caminho)
        with self._trava:
            id_req = next(self._ids)
        dados = None
        cabecalhos = {"Accept": "application/json", "X-Request-Id": str(id_req)}
        if corpo is not None:
            dados = json.dumps(corpo, ensure_ascii=False).encode("utf-8")
            cabecalhos["Content-Type"] = "application/json"
        if token:
            cabecalhos["Authorization"] = f"Bearer {token}"
        requisicao = urllib.request.Request(url, data=dados, method=metodo, headers=cabecalhos)
        self._registrar(f">>> #{id_req} {metodo} {url}" + (f"\n    {dados.decode('utf-8')}" if dados else ""))
        try:
            with urllib.request.urlopen(requisicao, timeout=tempo_limite or self.tempo_limite) as r:
                status, texto = r.status, r.read().decode("utf-8")
        except urllib.error.HTTPError as e:  # status 4xx/5xx: o servidor respondeu com um erro estruturado
            status, texto = e.code, e.read().decode("utf-8", errors="replace")
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as e:
            raise FalhaComunicacao(f"{url}: {getattr(e, 'reason', e)}") from e
        self._registrar(f"<<< #{id_req} {status}" + (f"\n    {texto}" if texto else ""))
        resultado = None
        if texto:
            try:
                resultado = json.loads(texto)
            except ValueError as e:
                raise FalhaComunicacao(f"Resposta malformada de {url}: {e}") from e
        if status >= 400:
            erro = (resultado or {}).get("erro", {}) if isinstance(resultado, dict) else {}
            raise ErroApi(status, erro.get("codigo", "ERRO_INTERNO"), erro.get("mensagem", f"HTTP {status}"))
        return resultado

    def _registrar(self, texto: str) -> None:
        if self.rastro is not None:
            self.rastro(texto)
