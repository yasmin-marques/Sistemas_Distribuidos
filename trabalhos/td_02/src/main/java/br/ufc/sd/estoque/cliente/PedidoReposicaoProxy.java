package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.PedidoReposicao;
import br.ufc.sd.estoque.protocolo.ProtocoloCliente;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.util.List;

public class PedidoReposicaoProxy extends ProxyBase implements PedidoReposicaoRemoto {

    public PedidoReposicaoProxy(ProtocoloCliente protocolo, RemoteObjectRef referencia) {
        super(protocolo, referencia, PedidoReposicaoRemoto.class);
    }

    @Override
    public PedidoReposicao consultar() {
        return CodecEntidades.pedidoDeJson(invocar("consultar"));
    }

    @Override
    public PedidoReposicao adicionarItem(String token, int idItem, int quantidade) {
        return CodecEntidades.pedidoDeJson(invocar("adicionarItem", "token", token, "idItem", idItem,
                "quantidade", quantidade));
    }

    @Override
    public List<Movimentacao> confirmarRecebimento(String token) {
        return CodecEntidades.listaDeJson(invocar("confirmarRecebimento", "token", token),
                CodecEntidades::movimentacaoDeJson);
    }

    @Override
    public PedidoReposicao cancelar(String token) {
        return CodecEntidades.pedidoDeJson(invocar("cancelar", "token", token));
    }
}
