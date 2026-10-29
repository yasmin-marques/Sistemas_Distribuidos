package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Vigia o estoque e envia ALERTA ao grupo (regra herdada do {@code MonitorEstoque} do TD01).
 *
 * <p>Verifica cada item logo após uma alteração e também periodicamente (a validade muda com
 * o passar dos dias, sem que ninguém mexa no item). Cada problema — estoque baixo, item
 * vencido, item perto de vencer — é avisado uma única vez; se o problema for resolvido e
 * voltar, é avisado de novo.
 */
public class MonitorEstoque implements AutoCloseable {

    private final EstoqueServico estoque;
    private final Publicador publicador;
    private final long intervaloMs;
    private final Set<String> avisados = new HashSet<>();
    private final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "monitor-estoque");
        t.setDaemon(true);
        return t;
    });

    public MonitorEstoque(EstoqueServico estoque, Publicador publicador, long intervaloMs) {
        if (intervaloMs <= 0) throw new IllegalArgumentException("Intervalo deve ser positivo.");
        this.estoque = Objects.requireNonNull(estoque);
        this.publicador = Objects.requireNonNull(publicador);
        this.intervaloMs = intervaloMs;
    }

    public void iniciar() {
        agendador.scheduleWithFixedDelay(this::verificarTodos, intervaloMs, intervaloMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void verificarTodos() {
        try {
            for (ItemEstoque item : estoque.listarItens()) verificar(item);
        } catch (RuntimeException e) {
            System.err.println("[Monitor] Erro ao verificar o estoque: " + e);
        }
    }

    public synchronized void verificar(ItemEstoque item) {
        LocalDate hoje = estoque.hoje();
        String nome = item.getCategoria().toLowerCase() + " '" + item.getNome() + "' (#" + item.getId() + ")";
        checar("vencido-" + item.getId(), item.vencido(hoje),
                "Item vencido: " + nome + ", validade " + item.getValidade() + ". Não utilize.");
        checar("vencendo-" + item.getId(), item.venceEmBreve(hoje),
                "Validade próxima: " + nome + " vence em " + diasAte(hoje, item.getValidade())
                        + " dia(s) (" + item.getValidade() + ").");
        checar("baixo-" + item.getId(), item.abaixoDoMinimo(),
                "Estoque baixo de " + nome + ": " + item.getQuantidade() + " unidade(s), mínimo "
                        + item.getEstoqueMinimo() + ".");
    }

    private static long diasAte(LocalDate hoje, LocalDate data) {
        return data == null ? 0 : ChronoUnit.DAYS.between(hoje, data);
    }

    private void checar(String chave, boolean problema, String texto) {
        if (!problema) {
            avisados.remove(chave);
        } else if (avisados.add(chave)) {
            publicador.publicar(TipoNotificacao.ALERTA, texto);
        }
    }

    @Override
    public void close() {
        agendador.shutdownNow();
    }
}
