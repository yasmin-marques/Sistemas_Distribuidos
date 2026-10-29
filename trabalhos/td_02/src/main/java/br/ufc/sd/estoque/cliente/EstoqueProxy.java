package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.protocolo.ProtocoloCliente;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.util.List;

public class EstoqueProxy extends ProxyBase implements EstoqueRemoto {

    public EstoqueProxy(ProtocoloCliente protocolo, RemoteObjectRef referencia) {
        super(protocolo, referencia, EstoqueRemoto.class);
    }

    @Override
    public List<ItemEstoque> listarItens() {
        return CodecEntidades.listaDeJson(invocar("listarItens"), CodecEntidades::itemDeJson);
    }

    @Override
    public ItemEstoque buscarItem(int idItem) {
        return CodecEntidades.itemDeJson(invocar("buscarItem", "idItem", idItem));
    }

    @Override
    public List<Fornecedor> listarFornecedores() {
        return CodecEntidades.listaDeJson(invocar("listarFornecedores"), CodecEntidades::fornecedorDeJson);
    }

    /** O item é empacotado em JSON aqui: o servidor recebe uma cópia (passagem por valor). */
    @Override
    public ItemEstoque cadastrarItem(String token, ItemEstoque item) {
        if (item == null) throw new IllegalArgumentException("Item ausente.");
        return CodecEntidades.itemDeJson(invocar("cadastrarItem", "token", token,
                "item", CodecEntidades.itemParaJson(item)));
    }

    @Override
    public Movimentacao registrarEntrada(String token, int idItem, int quantidade) {
        return CodecEntidades.movimentacaoDeJson(invocar("registrarEntrada", "token", token,
                "idItem", idItem, "quantidade", quantidade));
    }

    @Override
    public Movimentacao registrarSaida(String token, int idItem, int quantidade, String motivo) {
        return CodecEntidades.movimentacaoDeJson(invocar("registrarSaida", "token", token,
                "idItem", idItem, "quantidade", quantidade, "motivo", motivo));
    }

    @Override
    public List<Movimentacao> listarMovimentacoes(int idItem) {
        return CodecEntidades.listaDeJson(invocar("listarMovimentacoes", "idItem", idItem),
                CodecEntidades::movimentacaoDeJson);
    }

    /** O resultado é uma referência: devolve um proxy que aponta para o pedido no servidor. */
    @Override
    public PedidoReposicaoRemoto abrirPedidoReposicao(String token, int idFornecedor) {
        Object ref = invocar("abrirPedidoReposicao", "token", token, "idFornecedor", idFornecedor);
        return new PedidoReposicaoProxy(protocolo, RemoteObjectRef.deJson(ref));
    }
}
