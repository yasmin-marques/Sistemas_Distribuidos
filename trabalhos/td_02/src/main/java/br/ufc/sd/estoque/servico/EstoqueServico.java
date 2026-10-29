package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.modelo.Estoque;
import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.ItemPedido;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.Sessao;
import br.ufc.sd.estoque.modelo.TipoMovimentacao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Regras de negócio do estoque.
 *
 * <p>Concorrência: todo o estado (estoque, fornecedores, histórico e contadores) é protegido
 * por uma única trava, então cada operação é atômica — duas saídas simultâneas nunca deixam
 * o saldo negativo. Os objetos guardados nunca saem daqui: os métodos devolvem cópias. As
 * notificações e o aviso ao monitor acontecem depois de liberar a trava.
 */
public class EstoqueServico implements EstoqueRemoto {

    /** Maior quantidade aceita numa única movimentação. */
    public static final int QUANTIDADE_MAXIMA = 1_000_000;

    private final Object trava = new Object();
    private final Estoque estoque = new Estoque();
    private final Map<Integer, Fornecedor> fornecedores = new LinkedHashMap<>();
    private final List<Movimentacao> movimentacoes = new ArrayList<>();
    private int proximoItem = 1;
    private int proximoFornecedor = 1;
    private int proximaMovimentacao = 1;
    private int proximoPedido = 1;

    private final AutenticacaoServico autenticacao;
    private final Publicador publicador;
    private final Clock relogio;
    private volatile Consumer<ItemEstoque> observador = item -> { };

    public EstoqueServico(AutenticacaoServico autenticacao, Publicador publicador, Clock relogio) {
        this.autenticacao = Objects.requireNonNull(autenticacao);
        this.publicador = Objects.requireNonNull(publicador);
        this.relogio = Objects.requireNonNull(relogio);
    }

    /** Chamado (fora da trava) com uma cópia de cada item criado ou alterado. Usado pelo monitor. */
    public void setObservador(Consumer<ItemEstoque> observador) {
        this.observador = Objects.requireNonNull(observador);
    }

    public LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    // ------------------------------------------------------------------ consultas (por valor)

    @Override
    public List<ItemEstoque> listarItens() {
        synchronized (trava) {
            List<ItemEstoque> copia = new ArrayList<>();
            for (ItemEstoque i : estoque.listar()) copia.add(i.copiar());
            return copia;
        }
    }

    @Override
    public ItemEstoque buscarItem(int idItem) {
        synchronized (trava) {
            return obter(idItem).copiar();
        }
    }

    @Override
    public List<Fornecedor> listarFornecedores() {
        synchronized (trava) {
            List<Fornecedor> copia = new ArrayList<>();
            for (Fornecedor f : fornecedores.values()) copia.add(new Fornecedor(f));
            return copia;
        }
    }

    @Override
    public List<Movimentacao> listarMovimentacoes(int idItem) {
        if (idItem < 0) throw new IllegalArgumentException("Id de item inválido: " + idItem + ".");
        synchronized (trava) {
            if (idItem == 0) return new ArrayList<>(movimentacoes);
            obter(idItem);
            List<Movimentacao> doItem = new ArrayList<>();
            for (Movimentacao m : movimentacoes) {
                if (m.getIdItem() == idItem) doItem.add(m);
            }
            return doItem;
        }
    }

    public double valorTotal() {
        synchronized (trava) {
            return estoque.valorTotal();
        }
    }

    // ------------------------------------------------------------------ operações remotas

    @Override
    public ItemEstoque cadastrarItem(String token, ItemEstoque item) {
        return cadastrarItemComo(autenticacao.validar(token).getUsuario(), item);
    }

    @Override
    public Movimentacao registrarEntrada(String token, int idItem, int quantidade) {
        return entradaComo(autenticacao.validar(token).getUsuario(), idItem, quantidade, null);
    }

    @Override
    public Movimentacao registrarSaida(String token, int idItem, int quantidade, String motivo) {
        return saidaComo(autenticacao.validar(token).getUsuario(), idItem, quantidade, motivo);
    }

    @Override
    public PedidoReposicaoRemoto abrirPedidoReposicao(String token, int idFornecedor) {
        Sessao sessao = autenticacao.validar(token);
        PedidoReposicaoServico pedido;
        synchronized (trava) {
            Fornecedor f = fornecedores.get(idFornecedor);
            if (f == null) throw new IllegalArgumentException("Fornecedor " + idFornecedor + " não encontrado.");
            pedido = new PedidoReposicaoServico(proximoPedido++, new Fornecedor(f), sessao.getUsuario(),
                    relogio.millis(), this, autenticacao, publicador);
        }
        publicador.publicar(TipoNotificacao.NOTIFICACAO, sessao.getUsuario() + " abriu o pedido de reposição #"
                + pedido.getId() + " para o fornecedor " + pedido.consultar().getFornecedor().getNome() + ".");
        return pedido;
    }

    // ------------------------------------------------------------------ operações com responsável explícito
    // (usadas pelos métodos remotos acima, pelo console do servidor e pela carga inicial)

    public Fornecedor cadastrarFornecedor(Fornecedor fornecedor) {
        Objects.requireNonNull(fornecedor, "fornecedor");
        if (fornecedor.getNome() == null || fornecedor.getNome().isBlank()) {
            throw new IllegalArgumentException("O fornecedor deve ter nome.");
        }
        synchronized (trava) {
            Fornecedor novo = new Fornecedor(fornecedor);
            novo.setId(proximoFornecedor++);
            fornecedores.put(novo.getId(), novo);
            return new Fornecedor(novo);
        }
    }

    public ItemEstoque cadastrarItemComo(String responsavel, ItemEstoque item) {
        if (item == null) throw new IllegalArgumentException("Item ausente.");
        // trabalha sobre uma cópia: o objeto recebido pertence a quem chamou
        ItemEstoque novo = item.copiar();
        if (novo.getFornecedor() == null) throw new IllegalArgumentException("O item deve ter um fornecedor.");
        ItemEstoque copia;
        synchronized (trava) {
            Fornecedor f = fornecedores.get(novo.getFornecedor().getId());
            if (f == null) {
                throw new IllegalArgumentException("Fornecedor " + novo.getFornecedor().getId() + " não encontrado.");
            }
            novo.setFornecedor(f); // agrega o fornecedor cadastrado, não o enviado pelo cliente
            novo.validar();
            if (novo.vencido(hoje())) throw new IllegalArgumentException("Não é possível cadastrar item vencido.");
            if (novo.getQuantidade() > QUANTIDADE_MAXIMA) {
                throw new IllegalArgumentException("Quantidade acima do máximo (" + QUANTIDADE_MAXIMA + ").");
            }
            for (ItemEstoque existente : estoque.listar()) {
                if (existente.getClass() == novo.getClass() && existente.getNome().equalsIgnoreCase(novo.getNome().strip())) {
                    throw new IllegalStateException("Já existe " + existente.getCategoria().toLowerCase()
                            + " chamado '" + existente.getNome() + "' (#" + existente.getId() + ").");
                }
            }
            novo.setNome(novo.getNome().strip());
            novo.setId(proximoItem++);
            estoque.adicionar(novo);
            copia = novo.copiar();
        }
        publicador.publicar(TipoNotificacao.NOTIFICACAO, responsavel + " cadastrou " + copia.getCategoria().toLowerCase()
                + " '" + copia.getNome() + "' (#" + copia.getId() + ") com " + copia.getQuantidade() + " unidade(s).");
        observador.accept(copia);
        return copia;
    }

    public Movimentacao entradaComo(String responsavel, int idItem, int quantidade, String motivo) {
        validarQuantidade(quantidade);
        Movimentacao mov;
        ItemEstoque copia;
        synchronized (trava) {
            ItemEstoque item = obter(idItem);
            mov = aplicarEntrada(item, quantidade, responsavel, motivo);
            copia = item.copiar();
        }
        publicador.publicar(TipoNotificacao.ATUALIZACAO, "Entrada de " + quantidade + " un. de '" + copia.getNome()
                + "' por " + responsavel + ". Saldo: " + copia.getQuantidade() + ".");
        observador.accept(copia);
        return mov;
    }

    public Movimentacao saidaComo(String responsavel, int idItem, int quantidade, String motivo) {
        validarQuantidade(quantidade);
        Movimentacao mov;
        ItemEstoque copia;
        synchronized (trava) {
            ItemEstoque item = obter(idItem);
            if (item.vencido(hoje())) {
                throw new IllegalStateException("'" + item.getNome() + "' está vencido desde " + item.getValidade() + ".");
            }
            if (item.exigeJustificativaSaida() && (motivo == null || motivo.isBlank())) {
                throw new IllegalStateException("'" + item.getNome() + "' é controlado: informe a justificativa da saída.");
            }
            if (item.getQuantidade() < quantidade) {
                throw new IllegalStateException("Estoque insuficiente de '" + item.getNome() + "': disponível "
                        + item.getQuantidade() + ", solicitado " + quantidade + ".");
            }
            item.setQuantidade(item.getQuantidade() - quantidade);
            mov = registrar(TipoMovimentacao.SAIDA, item, quantidade, responsavel, motivo);
            copia = item.copiar();
        }
        publicador.publicar(TipoNotificacao.ATUALIZACAO, "Saída de " + quantidade + " un. de '" + copia.getNome()
                + "' por " + responsavel + ". Saldo: " + copia.getQuantidade() + ".");
        observador.accept(copia);
        return mov;
    }

    /**
     * Dá entrada de vários itens de uma vez (tudo ou nada). Usado na confirmação de um pedido.
     * Não publica notificação: quem chama decide a mensagem.
     */
    List<Movimentacao> entradaEmLote(String responsavel, List<ItemPedido> itens, String motivo) {
        List<Movimentacao> movs = new ArrayList<>();
        List<ItemEstoque> alterados = new ArrayList<>();
        synchronized (trava) {
            // valida tudo antes de alterar qualquer saldo
            for (ItemPedido linha : itens) {
                validarQuantidade(linha.getQuantidade());
                ItemEstoque item = obter(linha.getIdItem());
                if ((long) item.getQuantidade() + linha.getQuantidade() > Integer.MAX_VALUE) {
                    throw new IllegalStateException("Saldo de '" + item.getNome() + "' excederia o limite.");
                }
            }
            for (ItemPedido linha : itens) {
                ItemEstoque item = obter(linha.getIdItem());
                movs.add(aplicarEntrada(item, linha.getQuantidade(), responsavel, motivo));
                alterados.add(item.copiar());
            }
        }
        alterados.forEach(observador);
        return movs;
    }

    // ------------------------------------------------------------------ internos (chamar com a trava)

    private Movimentacao aplicarEntrada(ItemEstoque item, int quantidade, String responsavel, String motivo) {
        try {
            item.setQuantidade(Math.addExact(item.getQuantidade(), quantidade));
        } catch (ArithmeticException e) {
            throw new IllegalStateException("Saldo de '" + item.getNome() + "' excederia o limite.");
        }
        return registrar(TipoMovimentacao.ENTRADA, item, quantidade, responsavel, motivo);
    }

    private Movimentacao registrar(TipoMovimentacao tipo, ItemEstoque item, int quantidade, String responsavel,
                                   String motivo) {
        Movimentacao m = new Movimentacao(proximaMovimentacao++, tipo, item.getId(), item.getNome(), quantidade,
                item.getQuantidade(), responsavel, motivo == null || motivo.isBlank() ? null : motivo.strip(),
                relogio.millis());
        movimentacoes.add(m);
        return m;
    }

    private ItemEstoque obter(int idItem) {
        ItemEstoque item = estoque.buscar(idItem);
        if (item == null) throw new IllegalArgumentException("Item " + idItem + " não encontrado.");
        return item;
    }

    private static void validarQuantidade(int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("A quantidade deve ser positiva.");
        if (quantidade > QUANTIDADE_MAXIMA) {
            throw new IllegalArgumentException("Quantidade acima do máximo (" + QUANTIDADE_MAXIMA + ").");
        }
    }
}
