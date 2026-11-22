"""Testes do cliente Python contra um servidor em execução.

    API_URL=http://localhost:8080 python -m unittest discover -s testes -v

(o script testes.sh/testes.ps1 da raiz do projeto sobe o servidor Java e roda isto sozinho.)
Cada teste usa itens e pedidos próprios, então a ordem de execução não importa.
"""

import os
import threading
import time
import unittest
import uuid

from cliente_estoque.http import ClienteHttp, ErroApi, FalhaComunicacao
from cliente_estoque.modelo import (Fornecedor, Insumo, ItemEstoque, Medicamento, Vacina, item_de_json)
from cliente_estoque.proxies import ConexaoClinica, PedidoReposicaoProxy

URL = os.environ.get("API_URL", "http://localhost:8080")


def sufixo() -> str:
    return uuid.uuid4().hex[:6]


class TesteCliente(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.conexao = ConexaoClinica(URL)
        cls.estoque = cls.conexao.estoque
        cls.notificacoes = cls.conexao.notificacoes

    def entrar(self, usuario="estoquista", senha="est123"):
        return self.conexao.autenticacao.autenticar(usuario, senha).token

    # descoberta e autenticação

    def test_descobre_os_tres_objetos_distribuidos(self):
        for proxy in (self.conexao.autenticacao, self.estoque, self.notificacoes):
            self.assertTrue(proxy.href.startswith(URL + "/api/"), proxy.href)

    def test_autenticacao(self):
        sessao = self.conexao.autenticacao.autenticar("veterinario", "vet123")
        self.assertEqual("veterinario", sessao.usuario)
        for usuario, senha in (("veterinario", "errada"), ("intruso", "x")):
            with self.assertRaises(ErroApi) as c:
                self.conexao.autenticacao.autenticar(usuario, senha)
            self.assertEqual((401, "NAO_AUTENTICADO"), (c.exception.status, c.exception.codigo))
        with self.assertRaises(ErroApi) as c:
            self.estoque.registrar_entrada("token-falso", 1, 1)
        self.assertEqual("NAO_AUTENTICADO", c.exception.codigo)
        self.conexao.autenticacao.encerrar_sessao(sessao.token)
        with self.assertRaises(ErroApi):
            self.estoque.registrar_entrada(sessao.token, 1, 1)

    def test_servidor_inexistente_vira_falha_de_comunicacao(self):
        with self.assertRaises(FalhaComunicacao):
            ClienteHttp("http://localhost:1", tempo_limite=3).do_operation("GET", "/api")

    # consultas e herança

    def test_consultas_devolvem_subclasses_e_agregacao(self):
        itens = self.estoque.listar_itens()
        self.assertGreaterEqual(len(itens), 9)
        self.assertTrue(all(isinstance(i, ItemEstoque) for i in itens))
        self.assertTrue(any(isinstance(i, Medicamento) for i in itens))
        self.assertTrue(any(isinstance(i, Vacina) for i in itens))
        self.assertTrue(any(isinstance(i, Insumo) for i in itens))
        item = self.estoque.buscar_item(1)
        self.assertIsInstance(item, Medicamento)
        self.assertEqual("VetPharma", item.fornecedor.nome)
        self.assertGreaterEqual(len(self.estoque.listar_fornecedores()), 4)
        with self.assertRaises(ErroApi) as c:
            self.estoque.buscar_item(99999)
        self.assertEqual((404, "OBJETO_INEXISTENTE"), (c.exception.status, c.exception.codigo))

    def test_item_de_json_recusa_classe_desconhecida(self):
        with self.assertRaises(ValueError):
            item_de_json({"classe": "FERRAMENTA", "id": 1, "quantidade": 1, "estoqueMinimo": 1, "precoUnitario": 1})

    # passagem por valor

    def test_cadastrar_envia_copia_e_objeto_local_nao_muda(self):
        token = self.entrar()
        f = Fornecedor(2)
        locais = [
            Medicamento(nome=f"Dipirona {sufixo()}", fornecedor=f, quantidade=30, estoque_minimo=5, preco_unitario=9.9,
                        validade="2031-01-01", principio_ativo="Dipirona", concentracao="500 mg"),
            Vacina(nome=f"Giárdia {sufixo()}", fornecedor=f, quantidade=10, estoque_minimo=2, preco_unitario=60,
                   validade="2031-01-01", doenca_alvo="Giardíase", doses_por_frasco=1),
            Insumo(nome=f"Gaze {sufixo()}", fornecedor=f, quantidade=100, estoque_minimo=20, preco_unitario=0.5,
                   unidade_medida="pacote", esteril=True),
        ]
        for local in locais:
            criado = self.estoque.cadastrar_item(token, local)
            self.assertGreater(criado.id, 0)
            self.assertEqual(0, local.id, "o objeto local continua com id 0")
            self.assertIs(type(local), type(criado), "a subclasse volta preservada")
            self.assertEqual("PetSaude", criado.fornecedor.nome, "o servidor agrega o fornecedor")
            local.quantidade = 777  # alterar a cópia local não afeta o servidor
            self.assertNotEqual(777, self.estoque.buscar_item(criado.id).quantidade)

    def test_cadastrar_valida_regras(self):
        token = self.entrar()
        f = Fornecedor(2)
        invalidos = [
            (Insumo(nome="X", fornecedor=Fornecedor(77), quantidade=1, unidade_medida="un"), 400),
            (Medicamento(nome="Sem validade", fornecedor=f, quantidade=1, principio_ativo="A", concentracao="1"), 400),
            (Vacina(nome="Vencida", fornecedor=f, quantidade=1, validade="2020-01-01", doenca_alvo="Raiva"), 400),
            (Insumo(nome="Negativo", fornecedor=f, quantidade=-5, unidade_medida="un"), 400),
        ]
        for item, status in invalidos:
            with self.assertRaises(ErroApi, msg=item.nome) as c:
                self.estoque.cadastrar_item(token, item)
            self.assertEqual(status, c.exception.status, item.nome)
        nome = f"Unico {sufixo()}"
        self.estoque.cadastrar_item(token, Insumo(nome=nome, fornecedor=f, quantidade=1, unidade_medida="un"))
        with self.assertRaises(ErroApi) as c:  # duplicado (mesmo tipo e nome)
            self.estoque.cadastrar_item(token, Insumo(nome=nome.upper(), fornecedor=f, quantidade=1, unidade_medida="un"))
        self.assertEqual((409, "OPERACAO_NEGADA"), (c.exception.status, c.exception.codigo))

    # movimentações

    def test_entrada_saida_e_regras_de_negocio(self):
        token = self.entrar("veterinario", "vet123")
        item = self.estoque.cadastrar_item(token, Insumo(nome=f"Luva {sufixo()}", fornecedor=Fornecedor(4), quantidade=10,
                                                          estoque_minimo=1, unidade_medida="caixa"))
        saida = self.estoque.registrar_saida(token, item.id, 4, "procedimento")
        self.assertEqual(("SAIDA", 6, "veterinario"), (saida.tipo, saida.saldo_apos, saida.responsavel))
        self.assertEqual(9, self.estoque.registrar_entrada(token, item.id, 3).saldo_apos)
        self.assertEqual(2, len(self.estoque.listar_movimentacoes(item.id)))
        for chamada, status, codigo in (
            (lambda: self.estoque.registrar_saida(token, item.id, 50, "x"), 409, "OPERACAO_NEGADA"),
            (lambda: self.estoque.registrar_saida(token, item.id, -2), 400, "ARGUMENTO_INVALIDO"),
            (lambda: self.estoque.registrar_entrada(token, item.id, 0), 400, "ARGUMENTO_INVALIDO"),
            (lambda: self.estoque.registrar_entrada(token, 99999, 1), 404, "OBJETO_INEXISTENTE"),
        ):
            with self.assertRaises(ErroApi) as c:
                chamada()
            self.assertEqual((status, codigo), (c.exception.status, c.exception.codigo))

    def test_controlado_exige_motivo(self):
        token = self.entrar()
        controlado = next(i for i in self.estoque.listar_itens() if isinstance(i, Medicamento) and i.controlado)
        with self.assertRaises(ErroApi) as c:
            self.estoque.registrar_saida(token, controlado.id, 1)
        self.assertEqual("OPERACAO_NEGADA", c.exception.codigo)
        self.assertEqual("SAIDA", self.estoque.registrar_saida(token, controlado.id, 1, "analgesia").tipo)

    # passagem por referência

    def test_duas_referencias_ao_mesmo_pedido(self):
        token = self.entrar()
        p1 = self.estoque.abrir_pedido_reposicao(token, 4)  # MedCirurgica
        p2 = PedidoReposicaoProxy(self.conexao.http, p1.referencia)  # outro "cliente" com a mesma referência
        self.assertEqual(p1, p2)
        p1.adicionar_item(token, 7, 5)
        self.assertEqual(1, len(p2.consultar().itens), "p2 enxerga a alteração feita por p1")
        with self.assertRaises(ErroApi) as c:  # item de outro fornecedor
            p1.adicionar_item(token, 1, 5)
        self.assertEqual("OPERACAO_NEGADA", c.exception.codigo)
        self.assertEqual("CANCELADO", p2.cancelar(token).status)
        self.assertEqual("CANCELADO", p1.consultar().status, "p1 enxerga o cancelamento feito por p2")

    def test_pedido_confirmado_da_entrada_em_lote(self):
        token = self.entrar()
        antes = self.estoque.buscar_item(7).quantidade
        pedido = self.estoque.abrir_pedido_reposicao(token, 4)
        pedido.adicionar_item(token, 7, 100)
        pedido.adicionar_item(token, 7, 50)  # soma na mesma linha
        self.assertEqual(150, pedido.consultar().itens[0].quantidade)
        movs = pedido.confirmar_recebimento(token)
        self.assertEqual(1, len(movs))
        self.assertEqual(antes + 150, self.estoque.buscar_item(7).quantidade)
        with self.assertRaises(ErroApi) as c:
            pedido.confirmar_recebimento(token)
        self.assertEqual("OPERACAO_NEGADA", c.exception.codigo)

    # notificações

    def test_grupo_recebe_mensagens_e_sair_funciona(self):
        t1 = self.entrar("veterinario", "vet123")
        t2 = self.entrar("recepcao", "rec123")
        a1 = self.notificacoes.entrar_grupo(t1)
        a2 = self.notificacoes.entrar_grupo(t2)
        try:
            self.assertEqual("veterinario", a1.usuario())
            marca = sufixo()
            for tipo in ("NOTIFICACAO", "ALERTA", "ATUALIZACAO"):
                self.notificacoes.publicar(t1, tipo, f"{tipo} {marca}")
            for assinatura in (a1, a2):
                recebidas = {}
                limite = time.time() + 5
                while len(recebidas) < 3 and time.time() < limite:
                    for n in assinatura.aguardar(1000):
                        if marca in n.mensagem:
                            recebidas[n.tipo] = n.mensagem
                self.assertEqual({"NOTIFICACAO", "ALERTA", "ATUALIZACAO"}, set(recebidas))
            self.assertIn("veterinario", self.notificacoes.membros())
            self.notificacoes.sair_grupo(a2)  # a referência vai como argumento
            with self.assertRaises(ErroApi) as c:
                a2.aguardar(0)
            self.assertEqual("OBJETO_INEXISTENTE", c.exception.codigo)
            with self.assertRaises(ErroApi) as c:  # sair duas vezes: a referência não vale mais
                self.notificacoes.sair_grupo(a2)
            self.assertEqual(400, c.exception.status)
        finally:
            for a in (a1, a2):
                try:
                    self.notificacoes.sair_grupo(a)
                except ErroApi:
                    pass

    def test_long_polling_entrega_em_tempo_real(self):
        token = self.entrar()
        assinatura = self.notificacoes.entrar_grupo(token)
        try:
            marca = sufixo()
            threading.Timer(0.4, lambda: self.notificacoes.publicar(token, "ALERTA", f"urgente {marca}")).start()
            inicio = time.time()
            recebidas = []
            while not recebidas and time.time() - inicio < 10:
                recebidas = [n for n in assinatura.aguardar(8000) if marca in n.mensagem]
            self.assertTrue(recebidas, "a mensagem deve chegar")
            self.assertLess(time.time() - inicio, 4, "chegou bem antes do fim da espera máxima")
            with self.assertRaises(ErroApi) as c:
                assinatura.aguardar(30_001)
            self.assertEqual(400, c.exception.status)
        finally:
            self.notificacoes.sair_grupo(assinatura)

    def test_publicar_valida_a_mensagem(self):
        token = self.entrar()
        for tipo, texto in (("ALERTA", "  "), ("SPAM", "x")):
            with self.assertRaises(ErroApi) as c:
                self.notificacoes.publicar(token, tipo, texto)
            self.assertEqual(400, c.exception.status)


if __name__ == "__main__":
    unittest.main()
