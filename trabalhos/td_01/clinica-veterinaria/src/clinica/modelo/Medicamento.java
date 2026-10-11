package clinica.modelo;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/** POJO que representa um medicamento mantido no {@link Estoque}. */
public class Medicamento implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String nome;
    private String fabricante;
    private int quantidade;
    private double preco;
    private LocalDate validade;

    public Medicamento() {
    }

    public Medicamento(int id, String nome, String fabricante, int quantidade,
                       double preco, LocalDate validade) {
        this.id = id;
        this.nome = nome;
        this.fabricante = fabricante;
        this.quantidade = quantidade;
        this.preco = preco;
        this.validade = validade;
    }

    // Construtor de cópia
    public Medicamento(Medicamento o) {
        this(o.id, o.nome, o.fabricante, o.quantidade, o.preco, o.validade);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getFabricante() { return fabricante; }
    public void setFabricante(String fabricante) { this.fabricante = fabricante; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }

    public LocalDate getValidade() { return validade; }
    public void setValidade(LocalDate validade) { this.validade = validade; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Medicamento)) return false;
        Medicamento m = (Medicamento) obj;
        return id == m.id && quantidade == m.quantidade
                && Double.compare(preco, m.preco) == 0
                && Objects.equals(nome, m.nome)
                && Objects.equals(fabricante, m.fabricante)
                && Objects.equals(validade, m.validade);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome, fabricante, quantidade, preco, validade);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Medicamento #%d '%s' (%s) qtd=%d R$ %.2f val=%s",
                id, nome, fabricante, quantidade, preco, validade);
    }
}
