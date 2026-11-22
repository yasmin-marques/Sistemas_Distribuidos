"""Segunda thread do cliente: exibe as notificações do grupo assim que chegam (long polling)."""

from __future__ import annotations

import threading
import time
from typing import Callable

from .http import ErroApi, FalhaComunicacao
from .modelo import Notificacao
from .proxies import AssinaturaProxy

ESPERA_MS = 10_000


class OuvinteNotificacoes:
    def __init__(self, assinatura: AssinaturaProxy, ao_receber: Callable[[Notificacao], None],
                 ao_avisar: Callable[[str], None]):
        self.assinatura = assinatura
        self._ao_receber = ao_receber
        self._ao_avisar = ao_avisar
        self._ativo = threading.Event()
        self._thread = threading.Thread(target=self._laco, name="ouvinte-notificacoes", daemon=True)

    def iniciar(self) -> None:
        self._ativo.set()
        self._thread.start()

    def parar(self) -> None:
        self._ativo.clear()

    @property
    def ativo(self) -> bool:
        return self._ativo.is_set()

    def _laco(self) -> None:
        falhas = 0
        while self._ativo.is_set():
            try:
                for n in self.assinatura.aguardar(ESPERA_MS):
                    if self._ativo.is_set():
                        self._ao_receber(n)
                falhas = 0
            except ErroApi as e:
                if self._ativo.is_set():
                    self._ao_avisar("Assinatura encerrada pelo servidor (" + e.mensagem + "). Digite 'entrar' para voltar.")
                self._ativo.clear()
            except FalhaComunicacao as e:
                falhas += 1
                if falhas == 1 and self._ativo.is_set():
                    self._ao_avisar(f"Sem contato com o servidor ({e}). Tentando de novo...")
                time.sleep(min(5, falhas))
