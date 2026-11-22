"""Estoque: consome as solicitações de baixa da fila e atualiza o SQLite.

    python estoque/consumidor.py
    python estoque/consumidor.py --simular-queda-antes-do-ack

O ACK só vai ao RabbitMQ depois do COMMIT. Se o processo cair antes, a mensagem volta para a
fila e é reconhecida pelo id na segunda entrega.
"""
import argparse
import json
import logging
import os
import sqlite3
import sys
import time

import pika
import pika.exceptions

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import banco  # noqa: E402
import config  # noqa: E402

log = logging.getLogger("estoque")


def ler_mensagem(propriedades, corpo: bytes) -> dict:
    """Valida o corpo JSON; ValueError se a mensagem estiver malformada."""
    try:
        dados = json.loads(corpo.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as e:
        raise ValueError(f"corpo não é JSON válido: {e}") from e
    if not isinstance(dados, dict):
        raise ValueError("corpo JSON deve ser um objeto")
    id_mensagem = dados.get("id_mensagem") or propriedades.message_id
    if not id_mensagem:
        raise ValueError("mensagem sem id_mensagem")
    try:
        return {
            "id_mensagem": str(id_mensagem),
            "id_consulta": int(dados["id_consulta"]),
            "id_produto": int(dados["id_produto"]),
            "quantidade": int(dados["quantidade"]),
        }
    except (KeyError, TypeError, ValueError) as e:
        raise ValueError(f"campo ausente ou inválido: {e}") from e


class Consumidor:
    def __init__(self, cfg: config.Configuracao, simular_queda: bool):
        self.cfg = cfg
        self.simular_queda = simular_queda
        self.db = banco.conectar(cfg.banco)

    def processar(self, canal, entrega, propriedades, corpo):
        reentrega = " (REENTREGA)" if entrega.redelivered else ""
        try:
            msg = ler_mensagem(propriedades, corpo)
        except ValueError as e:
            log.error("Mensagem descartada (malformada): %s | corpo=%r", e, corpo[:200])
            canal.basic_nack(delivery_tag=entrega.delivery_tag, requeue=False)
            return

        log.info("Recebida%s: mensagem %s | consulta #%d | produto #%d | quantidade %d",
                 reentrega, msg["id_mensagem"], msg["id_consulta"], msg["id_produto"], msg["quantidade"])
        try:
            r = banco.aplicar_baixa(self.db, msg["id_mensagem"], msg["id_consulta"],
                                    msg["id_produto"], msg["quantidade"])
        except sqlite3.Error as e:
            # nada foi gravado; devolve a mensagem para a fila
            log.error("Falha ao gravar a baixa (%s). Mensagem devolvida à fila; nova tentativa em 3 s.", e)
            time.sleep(3)
            canal.basic_nack(delivery_tag=entrega.delivery_tag, requeue=True)
            return

        if r.situacao == "APLICADA":
            log.info("Baixa APLICADA: %s, %s. Saldo atual: %d", r.produto, r.detalhe, r.saldo)
        elif r.situacao == "DUPLICADA":
            log.warning("Mensagem %s DUPLICADA: %s. Saldo atual de %s: %s",
                        msg["id_mensagem"], r.detalhe, r.produto, r.saldo)
        else:
            log.warning("Baixa REJEITADA (%s). Registrada para não ser reprocessada.", r.detalhe)

        if self.simular_queda and r.situacao == "APLICADA":
            log.critical("SIMULAÇÃO: baixa gravada, mas o processo vai cair ANTES de enviar o ACK.")
            os._exit(1)  # sai sem fechar a conexão, como numa queda

        canal.basic_ack(delivery_tag=entrega.delivery_tag)
        log.info("ACK enviado para a mensagem %s.", msg["id_mensagem"])

    def executar(self):
        parametros = pika.ConnectionParameters(
            host=self.cfg.host, port=self.cfg.porta, virtual_host=self.cfg.vhost,
            credentials=pika.PlainCredentials(self.cfg.usuario, self.cfg.senha),
            heartbeat=30, connection_attempts=1, socket_timeout=5,
            client_properties={"connection_name": "estoque"})
        while True:
            try:
                conexao = pika.BlockingConnection(parametros)
            except pika.exceptions.AMQPConnectionError as e:
                log.error("RabbitMQ indisponível em %s (%s). Nova tentativa em 5 s...",
                          self.cfg.descricao(), e.__class__.__name__)
                time.sleep(5)
                continue
            try:
                canal = conexao.channel()
                fila = canal.queue_declare(queue=self.cfg.fila, durable=True)
                canal.basic_qos(prefetch_count=1)  # uma mensagem por vez
                log.info("Conectado a %s. Mensagens aguardando na fila: %d. Esperando solicitações...",
                         self.cfg.descricao(), fila.method.message_count)
                canal.basic_consume(queue=self.cfg.fila, on_message_callback=self.processar,
                                    auto_ack=False)
                while True:  # espera curta para o Ctrl+C funcionar no Windows
                    conexao.process_data_events(time_limit=1)
            except KeyboardInterrupt:
                log.info("Encerrando o Estoque (Ctrl+C). Mensagens sem ACK voltam para a fila.")
                try:
                    conexao.close()
                except pika.exceptions.AMQPError:
                    pass
                return
            except pika.exceptions.AMQPConnectionError as e:
                log.error("Conexão com o RabbitMQ perdida (%s). Reconectando em 5 s...",
                          e.__class__.__name__)
                time.sleep(5)


def main():
    parser = argparse.ArgumentParser(description="Consumidor de baixas do Estoque")
    parser.add_argument("--simular-queda-antes-do-ack", action="store_true",
                        help="grava a primeira baixa e encerra o processo sem enviar o ACK")
    args = parser.parse_args()

    for fluxo in (sys.stdout, sys.stderr):  # acentos corretos também com saída redirecionada
        if hasattr(fluxo, "reconfigure"):
            fluxo.reconfigure(encoding="utf-8")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s.%(msecs)03d [ESTOQUE] %(message)s",
                        datefmt="%H:%M:%S")
    logging.getLogger("pika").setLevel(logging.CRITICAL)

    cfg = config.carregar()
    log.info("Banco do Estoque: %s", cfg.banco)
    try:
        Consumidor(cfg, args.simular_queda_antes_do_ack).executar()
    except KeyboardInterrupt:
        log.info("Encerrando o Estoque (Ctrl+C).")


if __name__ == "__main__":
    main()
