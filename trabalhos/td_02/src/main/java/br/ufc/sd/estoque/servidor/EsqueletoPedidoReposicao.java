package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;

/** Esqueleto de {@link PedidoReposicaoRemoto}. */
public class EsqueletoPedidoReposicao extends EsqueletoBase<PedidoReposicaoRemoto> {

    public EsqueletoPedidoReposicao(PedidoReposicaoRemoto servo, TabelaObjetosRemotos tabela) {
        super(PedidoReposicaoRemoto.class, servo, tabela);
    }

    @Override
    public Object invocar(String metodo, ObjetoJson a) {
        return switch (metodo) {
            case "consultar" -> CodecEntidades.pedidoParaJson(servo.consultar());
            case "adicionarItem" -> CodecEntidades.pedidoParaJson(
                    servo.adicionarItem(a.texto("token"), a.inteiro("idItem"), a.inteiro("quantidade")));
            case "confirmarRecebimento" -> CodecEntidades.listaParaJson(
                    servo.confirmarRecebimento(a.texto("token")), CodecEntidades::movimentacaoParaJson);
            case "cancelar" -> CodecEntidades.pedidoParaJson(servo.cancelar(a.texto("token")));
            default -> throw metodoInexistente(metodo);
        };
    }
}
