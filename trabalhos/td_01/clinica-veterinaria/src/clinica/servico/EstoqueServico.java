package clinica.servico;

import clinica.modelo.Estoque;
import clinica.modelo.Medicamento;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Operações sobre o {@link Estoque}. Métodos sincronizados (várias threads) e que devolvem cópias dos objetos. */
public class EstoqueServico {

    private final Estoque estoque = new Estoque();
    private int proximoId = 1;

    public synchronized Medicamento cadastrar(Medicamento medicamento) {
        if (medicamento == null || medicamento.getNome() == null || medicamento.getNome().isBlank()) {
            throw new IllegalArgumentException("Medicamento deve ter nome.");
        }
        if (medicamento.getQuantidade() < 0 || medicamento.getPreco() < 0) {
            throw new IllegalArgumentException("Quantidade e preço não podem ser negativos.");
        }
        Medicamento novo = new Medicamento(medicamento);
        novo.setId(proximoId++);
        estoque.adicionar(novo);
        return new Medicamento(novo);
    }

    public synchronized Medicamento buscar(int id) {
        return new Medicamento(obter(id));
    }

    public synchronized List<Medicamento> listar() {
        List<Medicamento> copia = new ArrayList<>();
        for (Medicamento m : estoque.getMedicamentos()) {
            copia.add(new Medicamento(m));
        }
        return copia;
    }

    public synchronized Medicamento repor(int id, int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
        Medicamento m = obter(id);
        m.setQuantidade(m.getQuantidade() + quantidade);
        return new Medicamento(m);
    }

    // Dá baixa no estoque e devolve o medicamento já atualizado
    public synchronized Medicamento baixar(int id, int quantidade) {
        if (quantidade <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");
        Medicamento m = obter(id);
        if (m.getValidade() != null && m.getValidade().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Medicamento '" + m.getNome() + "' está vencido.");
        }
        if (m.getQuantidade() < quantidade) {
            throw new IllegalStateException("Estoque insuficiente de '" + m.getNome()
                    + "': disponível " + m.getQuantidade() + ", solicitado " + quantidade + ".");
        }
        m.setQuantidade(m.getQuantidade() - quantidade);
        return new Medicamento(m);
    }

    private Medicamento obter(int id) {
        Medicamento m = estoque.buscarPorId(id);
        if (m == null) throw new IllegalArgumentException("Medicamento " + id + " não encontrado.");
        return m;
    }
}
