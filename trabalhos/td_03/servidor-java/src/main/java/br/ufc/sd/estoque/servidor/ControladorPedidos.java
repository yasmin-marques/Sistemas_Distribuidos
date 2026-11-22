package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.web.Requisicao;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import br.ufc.sd.estoque.web.TabelaObjetosDistribuidos;

/**
 * Objetos distribuídos {@code PedidoReposicao-xxxxxxxx}, criados a cada pedido aberto e
 * acessados <b>pela referência</b> (o {@code href} recebido de {@code POST /api/estoque/pedidos}).
 * <pre>
 *   GET  /api/pedidos/{objeto}               consultar()
 *   POST /api/pedidos/{objeto}/itens         adicionarItem(token, idItem, quantidade)
 *   POST /api/pedidos/{objeto}/recebimento   confirmarRecebimento(token)
 *   POST /api/pedidos/{objeto}/cancelamento  cancelar(token)
 * </pre>
 */
final class ControladorPedidos extends ControladorBase {

    static final String CAMINHO = "/api/pedidos";

    ControladorPedidos(TabelaObjetosDistribuidos tabela) {
        super(tabela);
    }

    @Override
    void registrar(Roteador r) {
        r.get(CAMINHO + "/{objeto}", q -> Resposta.ok(CodecEntidades.pedidoParaJson(pedido(q).consultar())));
        r.post(CAMINHO + "/{objeto}/itens", q -> {
            var a = q.corpo();
            return Resposta.ok(CodecEntidades.pedidoParaJson(
                    pedido(q).adicionarItem(q.token(), a.inteiro("idItem"), a.inteiro("quantidade"))));
        });
        r.post(CAMINHO + "/{objeto}/recebimento", q -> Resposta.ok(CodecEntidades.listaParaJson(
                pedido(q).confirmarRecebimento(q.token()), CodecEntidades::movimentacaoParaJson)));
        r.post(CAMINHO + "/{objeto}/cancelamento",
                q -> Resposta.ok(CodecEntidades.pedidoParaJson(pedido(q).cancelar(q.token()))));
    }

    private PedidoReposicaoRemoto pedido(Requisicao q) {
        return tabela.localizar(q.parametro("objeto"), PedidoReposicaoRemoto.class);
    }
}
