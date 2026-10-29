package br.ufc.sd.estoque.modelo;

import java.time.LocalDate;
import java.util.Objects;

/** Vacina ("é-um" {@link ItemEstoque}). Exige validade e é avisada com maior antecedência. */
public class Vacina extends ItemEstoque {

    private String doencaAlvo;
    private int dosesPorFrasco;

    public Vacina() {
    }

    public Vacina(int id, String nome, Fornecedor fornecedor, int quantidade, int estoqueMinimo,
                  double precoUnitario, LocalDate validade, String doencaAlvo, int dosesPorFrasco) {
        super(id, nome, fornecedor, quantidade, estoqueMinimo, precoUnitario, validade);
        this.doencaAlvo = doencaAlvo;
        this.dosesPorFrasco = dosesPorFrasco;
    }

    public Vacina(Vacina outra) {
        super(outra);
        this.doencaAlvo = outra.doencaAlvo;
        this.dosesPorFrasco = outra.dosesPorFrasco;
    }

    @Override public String getCategoria() { return "Vacina"; }
    @Override public int getDiasAvisoValidade() { return 60; }
    @Override public Vacina copiar() { return new Vacina(this); }

    @Override
    public void validar() {
        super.validar();
        exigir(doencaAlvo != null && !doencaAlvo.isBlank(), "A vacina deve informar a doença-alvo.");
        exigir(dosesPorFrasco > 0, "A vacina deve ter ao menos uma dose por frasco.");
        exigir(getValidade() != null, "A vacina deve ter data de validade.");
    }

    public String getDoencaAlvo() { return doencaAlvo; }
    public void setDoencaAlvo(String doencaAlvo) { this.doencaAlvo = doencaAlvo; }

    public int getDosesPorFrasco() { return dosesPorFrasco; }
    public void setDosesPorFrasco(int dosesPorFrasco) { this.dosesPorFrasco = dosesPorFrasco; }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        Vacina v = (Vacina) o;
        return dosesPorFrasco == v.dosesPorFrasco && Objects.equals(doencaAlvo, v.doencaAlvo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), doencaAlvo, dosesPorFrasco);
    }

    @Override
    protected String detalhes() {
        return "doença-alvo: " + doencaAlvo + ", " + dosesPorFrasco + " dose(s)/frasco";
    }
}
