package br.ufc.sd.estoque.modelo;

import java.time.LocalDate;
import java.util.Objects;

/** Material de consumo, como seringas e luvas ("é-um" {@link ItemEstoque}). A validade é opcional. */
public class Insumo extends ItemEstoque {

    private String unidadeMedida;
    private boolean esteril;

    public Insumo() {
    }

    public Insumo(int id, String nome, Fornecedor fornecedor, int quantidade, int estoqueMinimo,
                  double precoUnitario, LocalDate validade, String unidadeMedida, boolean esteril) {
        super(id, nome, fornecedor, quantidade, estoqueMinimo, precoUnitario, validade);
        this.unidadeMedida = unidadeMedida;
        this.esteril = esteril;
    }

    public Insumo(Insumo outro) {
        super(outro);
        this.unidadeMedida = outro.unidadeMedida;
        this.esteril = outro.esteril;
    }

    @Override public String getCategoria() { return "Insumo"; }
    @Override public int getDiasAvisoValidade() { return 15; }
    @Override public Insumo copiar() { return new Insumo(this); }

    @Override
    public void validar() {
        super.validar();
        exigir(unidadeMedida != null && !unidadeMedida.isBlank(), "O insumo deve ter unidade de medida.");
    }

    public String getUnidadeMedida() { return unidadeMedida; }
    public void setUnidadeMedida(String unidadeMedida) { this.unidadeMedida = unidadeMedida; }

    public boolean isEsteril() { return esteril; }
    public void setEsteril(boolean esteril) { this.esteril = esteril; }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        Insumo i = (Insumo) o;
        return esteril == i.esteril && Objects.equals(unidadeMedida, i.unidadeMedida);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), unidadeMedida, esteril);
    }

    @Override
    protected String detalhes() {
        return "unidade: " + unidadeMedida + (esteril ? ", estéril" : "");
    }
}
