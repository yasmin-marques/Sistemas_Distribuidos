package clinica.multicast;

import clinica.modelo.Medicamento;
import clinica.servico.EstoqueServico;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Verifica o estoque de tempos em tempos e envia um ALERTA ao grupo (uma vez por problema)
public class MonitorEstoque {

    private final EstoqueServico estoque;
    private final ServidorMulticast servidor;
    private final int limite;
    private final Set<String> avisados = new HashSet<>();
    private final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "thread-monitor-estoque");
        t.setDaemon(true);
        return t;
    });

    private final int intervalo;

    public MonitorEstoque(EstoqueServico estoque, ServidorMulticast servidor, int limite, int intervaloSegundos) {
        this.estoque = estoque;
        this.servidor = servidor;
        this.limite = limite;
        this.intervalo = intervaloSegundos;
    }

    public void iniciar() {
        agendador.scheduleWithFixedDelay(this::verificar, intervalo, intervalo, TimeUnit.SECONDS);
    }

    void verificar() {
        try {
            LocalDate hoje = LocalDate.now();
            for (Medicamento m : estoque.listar()) {
                checar("vencido-" + m.getId(), m.getValidade() != null && m.getValidade().isBefore(hoje),
                        "Medicamento vencido: " + m.getNome() + ".");
                checar("baixo-" + m.getId(), m.getQuantidade() <= limite,
                        "Estoque baixo de " + m.getNome() + ": " + m.getQuantidade() + " unidade(s).");
            }
        } catch (RuntimeException e) {
            System.err.println("[Monitor] Erro: " + e);
        }
    }

    private void checar(String chave, boolean problema, String texto) {
        if (!problema) {
            avisados.remove(chave);
        } else if (avisados.add(chave)) {
            servidor.enviar(Notificacao.ALERTA, texto);
        }
    }

    public void parar() {
        agendador.shutdownNow();
    }
}
