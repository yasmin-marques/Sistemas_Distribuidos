package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;

/** Esqueleto de {@link EstoqueRemoto}. */
public class EsqueletoEstoque extends EsqueletoBase<EstoqueRemoto> {

    public EsqueletoEstoque(EstoqueRemoto servo, TabelaObjetosRemotos tabela) {
        super(EstoqueRemoto.class, servo, tabela);
    }

    @Override
    public Object invocar(String metodo, ObjetoJson a) {
        return switch (metodo) {
            case "listarItens" -> CodecEntidades.listaParaJson(servo.listarItens(), CodecEntidades::itemParaJson);
            case "buscarItem" -> CodecEntidades.itemParaJson(servo.buscarItem(a.inteiro("idItem")));
            case "listarFornecedores" ->
                    CodecEntidades.listaParaJson(servo.listarFornecedores(), CodecEntidades::fornecedorParaJson);
            // passagem por valor: o item chega como JSON e é reconstruído aqui, no servidor
            case "cadastrarItem" -> CodecEntidades.itemParaJson(
                    servo.cadastrarItem(a.texto("token"), CodecEntidades.itemDeJson(a.objeto("item").mapa())));
            case "registrarEntrada" -> CodecEntidades.movimentacaoParaJson(
                    servo.registrarEntrada(a.texto("token"), a.inteiro("idItem"), a.inteiro("quantidade")));
            case "registrarSaida" -> CodecEntidades.movimentacaoParaJson(servo.registrarSaida(a.texto("token"),
                    a.inteiro("idItem"), a.inteiro("quantidade"), a.textoOpcional("motivo")));
            case "listarMovimentacoes" -> CodecEntidades.listaParaJson(
                    servo.listarMovimentacoes(a.inteiro("idItem")), CodecEntidades::movimentacaoParaJson);
            // passagem por referência: o pedido fica no servidor, o cliente recebe a referência
            case "abrirPedidoReposicao" ->
                    porReferencia(servo.abrirPedidoReposicao(a.texto("token"), a.inteiro("idFornecedor")));
            default -> throw metodoInexistente(metodo);
        };
    }
}
