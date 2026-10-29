package br.ufc.sd.estoque.modelo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agregação ("tem-vários"): o estoque reúne {@link ItemEstoque}s, que também são
 * referenciados por pedidos e movimentações.
 *
 * <p>Não é thread-safe: o acesso concorrente é controlado pelo serviço que o possui.
 */
public class Estoque {

    private final Map<Integer, ItemEstoque> itens = new LinkedHashMap<>();

    public void adicionar(ItemEstoque item) {
        if (itens.containsKey(item.getId())) {
            throw new IllegalStateException("Já existe item com id " + item.getId() + ".");
        }
        itens.put(item.getId(), item);
    }

    /** @return o item com o id informado ou {@code null}. */
    public ItemEstoque buscar(int id) {
        return itens.get(id);
    }

    public List<ItemEstoque> listar() {
        return new ArrayList<>(itens.values());
    }

    public int tamanho() {
        return itens.size();
    }

    public double valorTotal() {
        double total = 0;
        for (ItemEstoque i : itens.values()) total += i.valorEmEstoque();
        return total;
    }
}
