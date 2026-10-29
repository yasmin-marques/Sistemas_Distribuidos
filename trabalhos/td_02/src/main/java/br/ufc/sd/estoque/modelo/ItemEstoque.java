package br.ufc.sd.estoque.modelo;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * Superclasse de tudo que é guardado no estoque da clínica.
 *
 * <p>Relações:
 * <ul>
 *   <li>"é-um": {@link Medicamento}, {@link Vacina} e {@link Insumo} estendem esta classe;</li>
 *   <li>"tem-um" (agregação): todo item tem um {@link Fornecedor}, que existe
 *       independentemente do item.</li>
 * </ul>
 * Cada subclasse define suas próprias regras (validação, antecedência do aviso de
 * validade e exigência de justificativa na saída).
 */
public abstract class ItemEstoque {

    private int id;
    private String nome;
    private Fornecedor fornecedor;
    private int quantidade;
    private int estoqueMinimo;
    private double precoUnitario;
    private LocalDate validade;

    protected ItemEstoque() {
    }

    protected ItemEstoque(int id, String nome, Fornecedor fornecedor, int quantidade,
                          int estoqueMinimo, double precoUnitario, LocalDate validade) {
        this.id = id;
        this.nome = nome;
        this.fornecedor = fornecedor;
        this.quantidade = quantidade;
        this.estoqueMinimo = estoqueMinimo;
        this.precoUnitario = precoUnitario;
        this.validade = validade;
    }

    protected ItemEstoque(ItemEstoque outro) {
        this(outro.id, outro.nome, outro.fornecedor == null ? null : new Fornecedor(outro.fornecedor),
                outro.quantidade, outro.estoqueMinimo, outro.precoUnitario, outro.validade);
    }

    /** Nome da categoria exibido ao usuário. */
    public abstract String getCategoria();

    /** Quantos dias antes do vencimento o monitor deve emitir o aviso. */
    public abstract int getDiasAvisoValidade();

    /** Cópia profunda (o fornecedor também é copiado). */
    public abstract ItemEstoque copiar();

    /** Se a saída deste item exige uma justificativa. */
    public boolean exigeJustificativaSaida() {
        return false;
    }

    /** Valida os campos comuns; subclasses acrescentam as próprias regras. */
    public void validar() {
        exigir(nome != null && !nome.isBlank(), "O item deve ter nome.");
        exigir(fornecedor != null, "O item deve ter um fornecedor.");
        exigir(quantidade >= 0, "A quantidade não pode ser negativa.");
        exigir(estoqueMinimo >= 0, "O estoque mínimo não pode ser negativo.");
        exigir(precoUnitario >= 0 && !Double.isNaN(precoUnitario), "O preço não pode ser negativo.");
    }

    protected static void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }

    public boolean vencido(LocalDate hoje) {
        return validade != null && validade.isBefore(hoje);
    }

    public boolean venceEmBreve(LocalDate hoje) {
        return validade != null && !vencido(hoje)
                && !validade.isAfter(hoje.plusDays(getDiasAvisoValidade()));
    }

    public boolean abaixoDoMinimo() {
        return quantidade <= estoqueMinimo;
    }

    public double valorEmEstoque() {
        return quantidade * precoUnitario;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public Fornecedor getFornecedor() { return fornecedor; }
    public void setFornecedor(Fornecedor fornecedor) { this.fornecedor = fornecedor; }

    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }

    public int getEstoqueMinimo() { return estoqueMinimo; }
    public void setEstoqueMinimo(int estoqueMinimo) { this.estoqueMinimo = estoqueMinimo; }

    public double getPrecoUnitario() { return precoUnitario; }
    public void setPrecoUnitario(double precoUnitario) { this.precoUnitario = precoUnitario; }

    public LocalDate getValidade() { return validade; }
    public void setValidade(LocalDate validade) { this.validade = validade; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || o.getClass() != getClass()) return false;
        ItemEstoque i = (ItemEstoque) o;
        return id == i.id && quantidade == i.quantidade && estoqueMinimo == i.estoqueMinimo
                && Double.compare(precoUnitario, i.precoUnitario) == 0
                && Objects.equals(nome, i.nome) && Objects.equals(fornecedor, i.fornecedor)
                && Objects.equals(validade, i.validade);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id, nome, fornecedor, quantidade, estoqueMinimo, precoUnitario, validade);
    }

    /** Detalhes próprios da subclasse, usados no {@link #toString()}. */
    protected abstract String detalhes();

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s #%d '%s' qtd=%d (mín. %d) R$ %.2f val=%s fornecedor=%s [%s]",
                getCategoria(), id, nome, quantidade, estoqueMinimo, precoUnitario,
                validade == null ? "-" : validade, nomeDoFornecedor(), detalhes());
    }

    private String nomeDoFornecedor() {
        if (fornecedor == null) return "-";
        return fornecedor.getNome() != null ? fornecedor.getNome() : "#" + fornecedor.getId();
    }
}
