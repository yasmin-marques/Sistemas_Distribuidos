package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.ItemPedido;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.PedidoReposicao;
import br.ufc.sd.estoque.modelo.Sessao;
import br.ufc.sd.estoque.modelo.StatusPedido;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import java.util.List;

/**
 * Objeto remoto criado pelo servidor a cada {@code abrirPedidoReposicao}. O estado fica aqui,
 * no servidor; os clientes só recebem uma referência a ele. Métodos sincronizados: vários
 * clientes podem usar a mesma referência ao mesmo tempo.
 */
public class PedidoReposicaoServico implements PedidoReposicaoRemoto {

    private final PedidoReposicao pedido;
    private final EstoqueServico estoque;
    private final AutenticacaoServico autenticacao;
    private final Publicador publicador;

    PedidoReposicaoServico(int id, Fornecedor fornecedor, String solicitante, long criadoEm,
                           EstoqueServico estoque, AutenticacaoServico autenticacao, Publicador publicador) {
        this.pedido = new PedidoReposicao(id, fornecedor, List.of(), StatusPedido.ABERTO, solicitante, criadoEm);
        this.estoque = estoque;
        this.autenticacao = autenticacao;
        this.publicador = publicador;
    }

    public int getId() {
        return pedido.getId();
    }

    @Override
    public synchronized PedidoReposicao consultar() {
        return pedido.copiar();
    }

    @Override
    public synchronized PedidoReposicao adicionarItem(String token, int idItem, int quantidade) {
        autenticacao.validar(token);
        exigirAberto();
        if (quantidade <= 0) throw new IllegalArgumentException("A quantidade deve ser positiva.");
        if (quantidade > EstoqueServico.QUANTIDADE_MAXIMA) {
            throw new IllegalArgumentException("Quantidade acima do máximo (" + EstoqueServico.QUANTIDADE_MAXIMA + ").");
        }
        ItemEstoque item = estoque.buscarItem(idItem);
        if (item.getFornecedor().getId() != pedido.getFornecedor().getId()) {
            throw new IllegalStateException("'" + item.getNome() + "' é fornecido por " + item.getFornecedor().getNome()
                    + ", não por " + pedido.getFornecedor().getNome() + ".");
        }
        pedido.adicionar(new ItemPedido(item.getId(), item.getNome(), quantidade));
        return pedido.copiar();
    }

    @Override
    public synchronized List<Movimentacao> confirmarRecebimento(String token) {
        Sessao sessao = autenticacao.validar(token);
        exigirAberto();
        if (pedido.getItens().isEmpty()) throw new IllegalStateException("O pedido #" + pedido.getId() + " não tem itens.");
        List<Movimentacao> movs = estoque.entradaEmLote(sessao.getUsuario(), pedido.getItens(),
                "Pedido de reposição #" + pedido.getId());
        pedido.setStatus(StatusPedido.RECEBIDO);
        publicador.publicar(TipoNotificacao.ATUALIZACAO, "Pedido #" + pedido.getId() + " recebido por "
                + sessao.getUsuario() + ": " + pedido.totalUnidades() + " unidade(s) de " + pedido.getItens().size()
                + " item(ns) deram entrada no estoque.");
        return movs;
    }

    @Override
    public synchronized PedidoReposicao cancelar(String token) {
        Sessao sessao = autenticacao.validar(token);
        exigirAberto();
        pedido.setStatus(StatusPedido.CANCELADO);
        publicador.publicar(TipoNotificacao.NOTIFICACAO, "Pedido #" + pedido.getId() + " cancelado por "
                + sessao.getUsuario() + ".");
        return pedido.copiar();
    }

    private void exigirAberto() {
        if (pedido.getStatus() != StatusPedido.ABERTO) {
            throw new IllegalStateException("O pedido #" + pedido.getId() + " já está " + pedido.getStatus() + ".");
        }
    }
}
