package br.ufc.sd.estoque.api;

import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.PedidoReposicao;
import java.util.List;

/**
 * Pedido de reposição em andamento. Vive no servidor e é acessado <b>por referência</b>:
 * todos que possuem a referência atuam sobre o mesmo pedido.
 */
public interface PedidoReposicaoRemoto extends ObjetoRemoto {

    /** Retrato atual do pedido (cópia por valor). */
    PedidoReposicao consultar();

    /** Acrescenta um item do mesmo fornecedor do pedido. */
    PedidoReposicao adicionarItem(String token, int idItem, int quantidade);

    /** Registra a chegada da mercadoria: dá entrada de todos os itens no estoque de uma só vez. */
    List<Movimentacao> confirmarRecebimento(String token);

    PedidoReposicao cancelar(String token);
}
