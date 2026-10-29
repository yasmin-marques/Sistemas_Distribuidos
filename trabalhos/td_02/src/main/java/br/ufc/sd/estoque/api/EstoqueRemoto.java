package br.ufc.sd.estoque.api;

import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.Movimentacao;
import java.util.List;

/**
 * Serviço remoto de estoque da clínica veterinária. Objeto remoto de nome fixo {@value #NOME}.
 *
 * <p>Entidades são passadas por valor (cópias em JSON). {@link #abrirPedidoReposicao}
 * devolve um objeto remoto, ou seja, uma referência.
 */
public interface EstoqueRemoto extends ObjetoRemoto {

    String NOME = "Estoque";

    List<ItemEstoque> listarItens();

    ItemEstoque buscarItem(int idItem);

    List<Fornecedor> listarFornecedores();

    /**
     * Cadastra um item criado pelo cliente. O objeto chega ao servidor <b>por valor</b>:
     * o servidor trabalha sobre a cópia desempacotada e devolve outra cópia com o id gerado.
     */
    ItemEstoque cadastrarItem(String token, ItemEstoque item);

    Movimentacao registrarEntrada(String token, int idItem, int quantidade);

    /** Dá baixa no estoque; recusa item vencido, saldo insuficiente e controlado sem justificativa. */
    Movimentacao registrarSaida(String token, int idItem, int quantidade, String motivo);

    /** Histórico de movimentações de um item ({@code idItem = 0} para todos). */
    List<Movimentacao> listarMovimentacoes(int idItem);

    /** Abre um pedido de reposição e devolve uma <b>referência</b> ao objeto remoto criado no servidor. */
    PedidoReposicaoRemoto abrirPedidoReposicao(String token, int idFornecedor);
}
