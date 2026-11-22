"""Consulta e manutenção do banco do Estoque (não usa o RabbitMQ).

    python estoque/estoque_cli.py saldo          todos os produtos
    python estoque/estoque_cli.py saldo 1        só o produto 1
    python estoque/estoque_cli.py historico      mensagens já processadas (uma linha por id de mensagem)
    python estoque/estoque_cli.py restaurar      volta aos saldos iniciais e apaga o histórico
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import banco  # noqa: E402
import config  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description="Estoque da clínica (SQLite)")
    sub = parser.add_subparsers(dest="comando", required=True)
    p_saldo = sub.add_parser("saldo", help="mostra o saldo dos produtos")
    p_saldo.add_argument("produto", nargs="?", type=int, help="id do produto")
    sub.add_parser("historico", help="lista as mensagens processadas")
    sub.add_parser("restaurar", help="restaura os dados de exemplo")
    args = parser.parse_args()

    db = banco.conectar(config.carregar().banco)

    if args.comando == "saldo":
        if args.produto is not None:
            linha = banco.buscar_produto(db, args.produto)
            if linha is None:
                print(f"Produto #{args.produto} não existe.")
                sys.exit(1)
            linhas = [linha]
        else:
            linhas = banco.listar_produtos(db)
        print(f"{'ID':>3}  {'PRODUTO':<24} {'TIPO':<12} {'SALDO':>5}")
        for id_, nome, tipo, qtd in linhas:
            print(f"{id_:>3}  {nome:<24} {tipo:<12} {qtd:>5}")

    elif args.comando == "historico":
        linhas = banco.historico(db)
        if not linhas:
            print("Nenhuma mensagem processada.")
        for quando, id_msg, consulta, produto, qtd, situacao, saldo, detalhe in linhas:
            print(f"{quando} | consulta #{consulta} | produto #{produto} | qtd {qtd} | {situacao:<9} "
                  f"| saldo após: {saldo} | {detalhe} | mensagem {id_msg}")
        print(f"Total: {len(linhas)} mensagem(ns).")

    elif args.comando == "restaurar":
        banco.restaurar(db)
        print("Estoque restaurado para os saldos iniciais; histórico de mensagens apagado.")
        for id_, nome, _tipo, qtd in banco.listar_produtos(db):
            print(f"  #{id_} {nome}: {qtd}")


if __name__ == "__main__":
    main()
