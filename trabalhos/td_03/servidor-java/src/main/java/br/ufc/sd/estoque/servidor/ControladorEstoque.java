package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.web.Requisicao;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import br.ufc.sd.estoque.web.TabelaObjetosDistribuidos;

/**
 * Objeto distribuído {@code Estoque}.
 * <pre>
 *   GET    /api/estoque   descrição do objeto (nome, interface e operações)
 *   GET  /api/estoque/itens                      listarItens()
 *   GET  /api/estoque/itens/{id}                 buscarItem(id)
 *   POST /api/estoque/itens                      cadastrarItem(token, item)   item enviado POR VALOR
 *   GET  /api/estoque/fornecedores               listarFornecedores()
 *   POST /api/estoque/itens/{id}/entradas        registrarEntrada(token, id, quantidade)
 *   POST /api/estoque/itens/{id}/saidas          registrarSaida(token, id, quantidade, motivo)
 *   GET  /api/estoque/movimentacoes[?item=id]    listarMovimentacoes(id)
 *   POST /api/estoque/pedidos                    abrirPedidoReposicao(token, idFornecedor) -> REFERÊNCIA
 * </pre>
 */
final class ControladorEstoque extends ControladorBase {

    static final String CAMINHO = "/api/estoque";

    private final EstoqueRemoto servo;

    ControladorEstoque(EstoqueRemoto servo, TabelaObjetosDistribuidos tabela) {
        super(tabela);
        this.servo = servo;
    }

    @Override
    void registrar(Roteador r) {
        descrever(r, EstoqueRemoto.NOME, EstoqueRemoto.class, CAMINHO);
        r.get(CAMINHO + "/itens", this::listarItens);
        r.get(CAMINHO + "/itens/{id}", this::buscarItem);
        r.post(CAMINHO + "/itens", this::cadastrarItem);
        r.get(CAMINHO + "/fornecedores", this::listarFornecedores);
        r.post(CAMINHO + "/itens/{id}/entradas", this::registrarEntrada);
        r.post(CAMINHO + "/itens/{id}/saidas", this::registrarSaida);
        r.get(CAMINHO + "/movimentacoes", this::listarMovimentacoes);
        r.post(CAMINHO + "/pedidos", this::abrirPedido);
    }

    private Resposta listarItens(Requisicao q) {
        return Resposta.ok(CodecEntidades.listaParaJson(servo.listarItens(), CodecEntidades::itemParaJson));
    }

    private Resposta buscarItem(Requisicao q) {
        return Resposta.ok(CodecEntidades.itemParaJson(servo.buscarItem(q.parametroInteiro("id"))));
    }

    /** Passagem por valor: o item chega como JSON e é reconstruído aqui; devolve outra cópia, com o id gerado. */
    private Resposta cadastrarItem(Requisicao q) {
        ItemEstoque recebido = CodecEntidades.itemDeJson(q.corpo().mapa());
        ItemEstoque criado = servo.cadastrarItem(q.token(), recebido);
        return Resposta.criado(q.baseUrl() + CAMINHO + "/itens/" + criado.getId(), CodecEntidades.itemParaJson(criado));
    }

    private Resposta listarFornecedores(Requisicao q) {
        return Resposta.ok(CodecEntidades.listaParaJson(servo.listarFornecedores(), CodecEntidades::fornecedorParaJson));
    }

    private Resposta registrarEntrada(Requisicao q) {
        var mov = servo.registrarEntrada(q.token(), q.parametroInteiro("id"), q.corpo().inteiro("quantidade"));
        return Resposta.criado(null, CodecEntidades.movimentacaoParaJson(mov));
    }

    private Resposta registrarSaida(Requisicao q) {
        ObjetoJson a = q.corpo();
        var mov = servo.registrarSaida(q.token(), q.parametroInteiro("id"), a.inteiro("quantidade"),
                a.textoOpcional("motivo"));
        return Resposta.criado(null, CodecEntidades.movimentacaoParaJson(mov));
    }

    private Resposta listarMovimentacoes(Requisicao q) {
        int idItem = (int) q.consultaLonga("item", 0);
        return Resposta.ok(CodecEntidades.listaParaJson(servo.listarMovimentacoes(idItem),
                CodecEntidades::movimentacaoParaJson));
    }

    /** Passagem por referência: o pedido fica no servidor; o cliente recebe o endereço dele. */
    private Resposta abrirPedido(Requisicao q) {
        return porReferencia(q, servo.abrirPedidoReposicao(q.token(), q.corpo().inteiro("idFornecedor")));
    }
}
