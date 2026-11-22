package br.ufc.sd.estoque.modelo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pedido de reposição feito a um fornecedor.
 *
 * <p>Agregação ("tem-um"): o pedido tem um {@link Fornecedor} e reúne vários {@link ItemPedido}s.
 */
public class PedidoReposicao {

    private final int id;
    private final Fornecedor fornecedor;
    private final List<ItemPedido> itens;
    private StatusPedido status;
    private final String solicitante;
    private final long criadoEm;

    public PedidoReposicao(int id, Fornecedor fornecedor, List<ItemPedido> itens, StatusPedido status,
                           String solicitante, long criadoEm) {
        this.id = id;
        this.fornecedor = Objects.requireNonNull(fornecedor, "fornecedor");
        this.itens = new ArrayList<>(itens);
        this.status = Objects.requireNonNull(status, "status");
        this.solicitante = solicitante;
        this.criadoEm = criadoEm;
    }

    /** Cópia profunda: é este retrato do pedido que viaja por valor. */
    public PedidoReposicao copiar() {
        return new PedidoReposicao(id, new Fornecedor(fornecedor), itens, status, solicitante, criadoEm);
    }

    /** Acrescenta a linha; se o item já estiver no pedido, soma as quantidades. */
    public void adicionar(ItemPedido item) {
        Objects.requireNonNull(item);
        for (int i = 0; i < itens.size(); i++) {
            ItemPedido atual = itens.get(i);
            if (atual.getIdItem() == item.getIdItem()) {
                itens.set(i, new ItemPedido(atual.getIdItem(), atual.getNomeItem(),
                        Math.addExact(atual.getQuantidade(), item.getQuantidade())));
                return;
            }
        }
        itens.add(item);
    }

    public int getId() { return id; }
    public Fornecedor getFornecedor() { return fornecedor; }
    public List<ItemPedido> getItens() { return Collections.unmodifiableList(itens); }
    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = Objects.requireNonNull(status); }
    public String getSolicitante() { return solicitante; }
    public long getCriadoEm() { return criadoEm; }

    public int totalUnidades() {
        int total = 0;
        for (ItemPedido i : itens) total += i.getQuantidade();
        return total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PedidoReposicao p)) return false;
        return id == p.id && criadoEm == p.criadoEm && fornecedor.equals(p.fornecedor) && itens.equals(p.itens)
                && status == p.status && Objects.equals(solicitante, p.solicitante);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fornecedor, itens, status, solicitante, criadoEm);
    }

    @Override
    public String toString() {
        return "Pedido #" + id + " [" + status + "] fornecedor=" + fornecedor.getNome() + " solicitante="
                + solicitante + " itens=" + itens;
    }
}
