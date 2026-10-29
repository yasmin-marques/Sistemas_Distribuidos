package br.ufc.sd.estoque.modelo;

import java.util.Objects;

/**
 * Mensagem enviada a todos os membros do grupo. Mantém o formato do TD01,
 * {@code {"tipo": ..., "mensagem": ..., "timestamp": ...}}, e acrescenta um número de sequência.
 */
public class Notificacao {

    /** Tamanho máximo do texto da mensagem. */
    public static final int TAMANHO_MAXIMO = 1000;

    private final long id;
    private final TipoNotificacao tipo;
    private final String mensagem;
    private final long timestamp;

    public Notificacao(long id, TipoNotificacao tipo, String mensagem, long timestamp) {
        this.id = id;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.mensagem = Objects.requireNonNull(mensagem, "mensagem");
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public TipoNotificacao getTipo() { return tipo; }
    public String getMensagem() { return mensagem; }
    public long getTimestamp() { return timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notificacao n)) return false;
        return id == n.id && timestamp == n.timestamp && tipo == n.tipo && mensagem.equals(n.mensagem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tipo, mensagem, timestamp);
    }

    @Override
    public String toString() {
        return "[" + tipo + "] " + mensagem;
    }
}
