package br.ufc.sd.estoque.modelo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Registro de uma entrada ou saída de estoque (histórico imutável). */
public class Movimentacao {

    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault());

    private final int id;
    private final TipoMovimentacao tipo;
    private final int idItem;
    private final String nomeItem;
    private final int quantidade;
    private final int saldoApos;
    private final String responsavel;
    private final String motivo;
    private final long instante;

    public Movimentacao(int id, TipoMovimentacao tipo, int idItem, String nomeItem, int quantidade,
                        int saldoApos, String responsavel, String motivo, long instante) {
        this.id = id;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.idItem = idItem;
        this.nomeItem = nomeItem;
        this.quantidade = quantidade;
        this.saldoApos = saldoApos;
        this.responsavel = responsavel;
        this.motivo = motivo;
        this.instante = instante;
    }

    public int getId() { return id; }
    public TipoMovimentacao getTipo() { return tipo; }
    public int getIdItem() { return idItem; }
    public String getNomeItem() { return nomeItem; }
    public int getQuantidade() { return quantidade; }
    public int getSaldoApos() { return saldoApos; }
    public String getResponsavel() { return responsavel; }
    public String getMotivo() { return motivo; }
    public long getInstante() { return instante; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Movimentacao m)) return false;
        return id == m.id && idItem == m.idItem && quantidade == m.quantidade && saldoApos == m.saldoApos
                && instante == m.instante && tipo == m.tipo && Objects.equals(nomeItem, m.nomeItem)
                && Objects.equals(responsavel, m.responsavel) && Objects.equals(motivo, m.motivo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tipo, idItem, nomeItem, quantidade, saldoApos, responsavel, motivo, instante);
    }

    @Override
    public String toString() {
        return "Mov. #" + id + " [" + FORMATO.format(Instant.ofEpochMilli(instante)) + "] " + tipo + " de "
                + quantidade + " un. de '" + nomeItem + "' (item #" + idItem + ") saldo=" + saldoApos
                + " por " + responsavel + (motivo == null || motivo.isBlank() ? "" : " | motivo: " + motivo);
    }
}
