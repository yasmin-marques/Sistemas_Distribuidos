package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.modelo.Notificacao;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Participação de um membro no grupo: guarda as notificações ainda não buscadas.
 *
 * <p>Ciclo de vida: criada em {@code entrarGrupo}; encerrada em {@code sairGrupo} ou quando
 * o cliente fica mais que o prazo de concessão ("lease") sem buscar mensagens — assim um
 * cliente que caiu não acumula mensagens para sempre (mesma ideia do coletor distribuído do RMI).
 */
public class Assinatura implements AssinaturaRemota {

    /** Máximo de notificações guardadas; acima disso as mais antigas são descartadas. */
    public static final int CAPACIDADE = 500;

    private final String usuario;
    private final long leaseMs;
    private final Deque<Notificacao> fila = new ArrayDeque<>();
    private boolean ativa = true;
    private int esperando = 0;
    private long ultimoAcesso = System.currentTimeMillis();
    private long descartadas = 0;

    Assinatura(String usuario, long leaseMs) {
        this.usuario = usuario;
        this.leaseMs = leaseMs;
    }

    @Override
    public String usuario() {
        return usuario;
    }

    @Override
    public synchronized List<Notificacao> aguardar(long esperaMaximaMs) {
        if (esperaMaximaMs < 0 || esperaMaximaMs > ESPERA_MAXIMA_MS) {
            throw new IllegalArgumentException("A espera deve estar entre 0 e " + ESPERA_MAXIMA_MS + " ms.");
        }
        if (!ativa) throw new IllegalStateException("Assinatura encerrada.");
        esperando++;
        ultimoAcesso = System.currentTimeMillis();
        try {
            long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(esperaMaximaMs);
            while (fila.isEmpty() && ativa) {
                long resta = limite - System.nanoTime();
                if (resta <= 0) break;
                TimeUnit.NANOSECONDS.timedWait(this, resta);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            esperando--;
            ultimoAcesso = System.currentTimeMillis();
        }
        List<Notificacao> entregues = new ArrayList<>(fila);
        fila.clear();
        return entregues;
    }

    /** Coloca a notificação na fila deste membro e acorda quem estiver aguardando. */
    synchronized void entregar(Notificacao n) {
        if (!ativa) return;
        if (fila.size() >= CAPACIDADE) {
            fila.removeFirst();
            descartadas++;
        }
        fila.addLast(n);
        notifyAll();
    }

    synchronized void encerrar() {
        ativa = false;
        fila.clear();
        notifyAll();
    }

    synchronized boolean expirou(long agora) {
        return ativa && esperando == 0 && agora - ultimoAcesso > leaseMs;
    }

    public synchronized boolean ativa() {
        return ativa;
    }

    public synchronized int pendentes() {
        return fila.size();
    }

    public synchronized long descartadas() {
        return descartadas;
    }

    @Override
    public String toString() {
        return "Assinatura de " + usuario;
    }
}
