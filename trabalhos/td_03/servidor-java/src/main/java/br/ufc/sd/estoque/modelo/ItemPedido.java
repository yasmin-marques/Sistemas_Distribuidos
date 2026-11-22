package br.ufc.sd.estoque.modelo;

import java.util.Objects;

/** Linha de um {@link PedidoReposicao}: qual item e quantas unidades. */
public class ItemPedido {

    private final int idItem;
    private final String nomeItem;
    private final int quantidade;

    public ItemPedido(int idItem, String nomeItem, int quantidade) {
        this.idItem = idItem;
        this.nomeItem = nomeItem;
        this.quantidade = quantidade;
    }

    public int getIdItem() { return idItem; }
    public String getNomeItem() { return nomeItem; }
    public int getQuantidade() { return quantidade; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemPedido i)) return false;
        return idItem == i.idItem && quantidade == i.quantidade && Objects.equals(nomeItem, i.nomeItem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idItem, nomeItem, quantidade);
    }

    @Override
    public String toString() {
        return quantidade + "x '" + nomeItem + "' (item #" + idItem + ")";
    }
}
