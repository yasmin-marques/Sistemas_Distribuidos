package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.modelo.Sessao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Grupo de notificações (substitui o grupo multicast do TD01).
 *
 * <p>Publicar coloca a mensagem na fila de cada assinatura ativa; cada cliente a busca pela
 * sua própria chamada {@code aguardar}, atendida em paralelo pelas threads do servidor. A
 * distribuição é feita sob uma trava, o que dá <em>ordem total</em>: todos os membros recebem
 * as mensagens na mesma ordem, mesmo com várias threads publicando ao mesmo tempo (console,
 * monitor e requisições dos clientes).
 */
public class CentralNotificacoes implements NotificacoesRemotas, Publicador, AutoCloseable {

    private final AutenticacaoServico autenticacao;
    private final long leaseMs;
    private final Set<Assinatura> assinaturas = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequencia = new AtomicLong();
    private final Object ordem = new Object();
    private final ScheduledExecutorService coletor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "coletor-assinaturas");
        t.setDaemon(true);
        return t;
    });
    private volatile Consumer<Assinatura> aoEncerrar = a -> { };
    private volatile Consumer<Notificacao> aoPublicar = n -> { };

    public CentralNotificacoes(AutenticacaoServico autenticacao, long leaseMs) {
        if (leaseMs <= 0) throw new IllegalArgumentException("Lease deve ser positivo.");
        this.autenticacao = Objects.requireNonNull(autenticacao);
        this.leaseMs = leaseMs;
    }

    /** Começa a remover periodicamente as assinaturas abandonadas. */
    public void iniciar() {
        long periodo = Math.max(100, Math.min(5_000, leaseMs / 2));
        coletor.scheduleWithFixedDelay(this::coletarExpiradas, periodo, periodo, TimeUnit.MILLISECONDS);
    }

    /** Chamado quando uma assinatura deixa o grupo (o servidor a retira da tabela de objetos). */
    public void setAoEncerrar(Consumer<Assinatura> aoEncerrar) {
        this.aoEncerrar = Objects.requireNonNull(aoEncerrar);
    }

    /** Chamado a cada notificação publicada (o servidor exibe no console). */
    public void setAoPublicar(Consumer<Notificacao> aoPublicar) {
        this.aoPublicar = Objects.requireNonNull(aoPublicar);
    }

    // ------------------------------------------------------------------ operações remotas

    @Override
    public AssinaturaRemota entrarGrupo(String token) {
        Sessao sessao = autenticacao.validar(token);
        Assinatura a = new Assinatura(sessao.getUsuario(), leaseMs);
        assinaturas.add(a);
        return a;
    }

    @Override
    public void sairGrupo(AssinaturaRemota assinatura) {
        if (!(assinatura instanceof Assinatura a) || !assinaturas.contains(a)) {
            throw new IllegalArgumentException("Assinatura desconhecida ou já encerrada.");
        }
        encerrar(a);
    }

    @Override
    public Notificacao publicar(String token, TipoNotificacao tipo, String mensagem) {
        Sessao sessao = autenticacao.validar(token);
        if (mensagem == null || mensagem.isBlank()) throw new IllegalArgumentException("Mensagem vazia.");
        return publicar(tipo, sessao.getUsuario() + ": " + mensagem.strip());
    }

    @Override
    public List<String> membros() {
        List<String> nomes = new ArrayList<>();
        for (Assinatura a : assinaturas) nomes.add(a.usuario());
        Collections.sort(nomes);
        return nomes;
    }

    // ------------------------------------------------------------------ uso interno do servidor

    @Override
    public Notificacao publicar(TipoNotificacao tipo, String mensagem) {
        if (tipo == null) throw new IllegalArgumentException("Tipo de notificação ausente.");
        if (mensagem == null || mensagem.isBlank()) throw new IllegalArgumentException("Mensagem vazia.");
        if (mensagem.length() > Notificacao.TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Mensagem maior que " + Notificacao.TAMANHO_MAXIMO + " caracteres.");
        }
        Notificacao n;
        synchronized (ordem) {
            n = new Notificacao(sequencia.incrementAndGet(), tipo, mensagem.strip(), System.currentTimeMillis());
            for (Assinatura a : assinaturas) a.entregar(n);
        }
        aoPublicar.accept(n);
        return n;
    }

    public int quantidadeMembros() {
        return assinaturas.size();
    }

    void coletarExpiradas() {
        long agora = System.currentTimeMillis();
        for (Assinatura a : assinaturas) {
            if (a.expirou(agora)) encerrar(a);
        }
    }

    private void encerrar(Assinatura a) {
        if (assinaturas.remove(a)) {
            a.encerrar();
            aoEncerrar.accept(a);
        }
    }

    @Override
    public void close() {
        coletor.shutdownNow();
        for (Assinatura a : assinaturas) encerrar(a);
    }
}
