package br.ufc.sd.estoque.modelo;

import java.time.LocalDate;
import java.util.Objects;

/** Medicamento ("é-um" {@link ItemEstoque}). Medicamentos controlados exigem justificativa na saída. */
public class Medicamento extends ItemEstoque {

    private String principioAtivo;
    private String concentracao;
    private boolean controlado;

    public Medicamento() {
    }

    public Medicamento(int id, String nome, Fornecedor fornecedor, int quantidade, int estoqueMinimo,
                       double precoUnitario, LocalDate validade,
                       String principioAtivo, String concentracao, boolean controlado) {
        super(id, nome, fornecedor, quantidade, estoqueMinimo, precoUnitario, validade);
        this.principioAtivo = principioAtivo;
        this.concentracao = concentracao;
        this.controlado = controlado;
    }

    public Medicamento(Medicamento outro) {
        super(outro);
        this.principioAtivo = outro.principioAtivo;
        this.concentracao = outro.concentracao;
        this.controlado = outro.controlado;
    }

    @Override public String getCategoria() { return "Medicamento"; }
    @Override public int getDiasAvisoValidade() { return 30; }
    @Override public Medicamento copiar() { return new Medicamento(this); }
    @Override public boolean exigeJustificativaSaida() { return controlado; }

    @Override
    public void validar() {
        super.validar();
        exigir(principioAtivo != null && !principioAtivo.isBlank(), "O medicamento deve ter princípio ativo.");
        exigir(getValidade() != null, "O medicamento deve ter data de validade.");
    }

    public String getPrincipioAtivo() { return principioAtivo; }
    public void setPrincipioAtivo(String principioAtivo) { this.principioAtivo = principioAtivo; }

    public String getConcentracao() { return concentracao; }
    public void setConcentracao(String concentracao) { this.concentracao = concentracao; }

    public boolean isControlado() { return controlado; }
    public void setControlado(boolean controlado) { this.controlado = controlado; }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        Medicamento m = (Medicamento) o;
        return controlado == m.controlado && Objects.equals(principioAtivo, m.principioAtivo)
                && Objects.equals(concentracao, m.concentracao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), principioAtivo, concentracao, controlado);
    }

    @Override
    protected String detalhes() {
        return "princípio ativo: " + principioAtivo + ", " + concentracao + (controlado ? ", CONTROLADO" : "");
    }
}
