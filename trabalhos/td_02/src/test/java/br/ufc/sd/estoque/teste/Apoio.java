package br.ufc.sd.estoque.teste;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.protocolo.CodigoErro;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.PortaServidora;
import br.ufc.sd.estoque.servidor.ServidorEstoque;
import java.rmi.RemoteException;
import java.rmi.server.ExportException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Utilitários compartilhados pelos testes. */
final class Apoio {

    /** Portas usadas pelos servidores de teste dentro da JVM de testes. */
    private static final AtomicInteger PROXIMA_PORTA = new AtomicInteger(21_100);

    private Apoio() { }

    /** Relógio que o teste pode adiantar (para simular o passar dos dias e o vencimento). */
    static final class RelogioAjustavel extends Clock {
        private volatile Instant agora;
        private final ZoneId zona;

        RelogioAjustavel() {
            this(Instant.now(), ZoneId.systemDefault());
        }

        private RelogioAjustavel(Instant agora, ZoneId zona) {
            this.agora = agora;
            this.zona = zona;
        }

        void avancarDias(long dias) {
            agora = agora.plus(Duration.ofDays(dias));
        }

        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId z) { return new RelogioAjustavel(agora, z); }
        @Override public Instant instant() { return agora; }
    }

    /** Inicia um servidor real (registro RMI + porta servidora) numa porta livre. */
    static ServidorEstoque servidor(Clock relogio, long leaseMs) throws RemoteException {
        RemoteException ultimo = null;
        for (int tentativa = 0; tentativa < 50; tentativa++) {
            int porta = PROXIMA_PORTA.getAndIncrement();
            ServidorEstoque.Configuracao config = new ServidorEstoque.Configuracao("localhost", porta,
                    PortaServidora.NOME_REGISTRO, 3_600_000, leaseMs, false, relogio, msg -> { });
            try {
                return ServidorEstoque.iniciar(config);
            } catch (ExportException e) { // porta ocupada por outro programa
                ultimo = e;
            }
        }
        throw ultimo;
    }

    static ServidorEstoque servidor() throws RemoteException {
        return servidor(Clock.systemDefaultZone(), 30_000);
    }

    /**
     * Caixa de entrada de um membro do grupo para os testes: busca no servidor e guarda o que
     * ainda não foi conferido (uma busca pode trazer várias notificações de uma vez).
     */
    static final class Caixa {
        private final AssinaturaRemota assinatura;
        private final List<Notificacao> pendentes = new ArrayList<>();
        private final List<Notificacao> todas = new ArrayList<>();

        Caixa(AssinaturaRemota assinatura) {
            this.assinatura = assinatura;
        }

        /** Espera (até 5 s) uma notificação que satisfaça o critério e a retira da caixa. */
        Notificacao esperar(Predicate<Notificacao> criterio) {
            long limite = System.currentTimeMillis() + 5_000;
            while (true) {
                for (Iterator<Notificacao> it = pendentes.iterator(); it.hasNext(); ) {
                    Notificacao n = it.next();
                    if (criterio.test(n)) {
                        it.remove();
                        return n;
                    }
                }
                long resta = limite - System.currentTimeMillis();
                if (resta <= 0) {
                    throw new AssertionError("Notificação esperada não chegou em 5 s. Recebidas: " + todas);
                }
                List<Notificacao> novas = assinatura.aguardar(Math.min(2_000, resta));
                pendentes.addAll(novas);
                todas.addAll(novas);
            }
        }

        /** Tudo o que chegar durante {@code ms} (além do que já está na caixa). */
        List<Notificacao> coletar(long ms) {
            long limite = System.currentTimeMillis() + ms;
            long resta;
            while ((resta = limite - System.currentTimeMillis()) > 0) {
                List<Notificacao> novas = assinatura.aguardar(resta);
                pendentes.addAll(novas);
                todas.addAll(novas);
            }
            List<Notificacao> r = new ArrayList<>(pendentes);
            pendentes.clear();
            return r;
        }
    }

    /** Executa e confere o código do erro remoto. */
    static ExcecaoRemota erro(CodigoErro esperado, Teste.Corpo corpo) {
        ExcecaoRemota e = Teste.lanca(ExcecaoRemota.class, corpo);
        Teste.igual(esperado, e.getCodigo(), "código do erro (" + e.getMessage() + ")");
        return e;
    }
}
