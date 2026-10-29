package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.AutenticacaoRemota;
import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.protocolo.PortaServidora;
import br.ufc.sd.estoque.protocolo.ProtocoloServidor;
import br.ufc.sd.estoque.servico.AutenticacaoServico;
import br.ufc.sd.estoque.servico.CentralNotificacoes;
import br.ufc.sd.estoque.servico.DadosIniciais;
import br.ufc.sd.estoque.servico.EstoqueServico;
import br.ufc.sd.estoque.servico.MonitorEstoque;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Servidor do estoque da clínica veterinária (TD02).
 *
 * <p>Monta os serviços, registra-os na tabela de objetos remotos, cria o registro RMI,
 * exporta a porta servidora (na mesma porta TCP do registro, uma só porta para liberar no
 * firewall) e inicia o despachante e o monitor de estoque.
 */
public final class ServidorEstoque implements AutoCloseable {

    public static final int PORTA_PADRAO = 1099;

    /** Parâmetros do servidor. */
    public record Configuracao(String host, int porta, String servico, long intervaloMonitorMs, long leaseMs,
                               boolean detalhado, Clock relogio, Consumer<String> registro) {
        public Configuracao {
            Objects.requireNonNull(host);
            Objects.requireNonNull(servico);
            Objects.requireNonNull(relogio);
            Objects.requireNonNull(registro);
        }

        public static Configuracao padrao() {
            return new Configuracao("localhost", PORTA_PADRAO, PortaServidora.NOME_REGISTRO, 10_000, 30_000,
                    false, Clock.systemDefaultZone(), System.out::println);
        }

        public Configuracao comPorta(int novaPorta) {
            return new Configuracao(host, novaPorta, servico, intervaloMonitorMs, leaseMs, detalhado, relogio, registro);
        }
    }

    private final Configuracao config;
    private final AutenticacaoServico autenticacao;
    private final CentralNotificacoes central;
    private final EstoqueServico estoque;
    private final MonitorEstoque monitor;
    private final TabelaObjetosRemotos tabela;
    private final ProtocoloServidor protocolo;
    private final Despachante despachante;
    private Registry registro;

    private ServidorEstoque(Configuracao config) {
        this.config = config;
        this.autenticacao = new AutenticacaoServico(AutenticacaoServico.USUARIOS_PADRAO);
        this.central = new CentralNotificacoes(autenticacao, config.leaseMs());
        this.estoque = new EstoqueServico(autenticacao, central, config.relogio());
        DadosIniciais.semear(estoque);
        this.monitor = new MonitorEstoque(estoque, central, config.intervaloMonitorMs());
        estoque.setObservador(monitor::verificar);

        this.tabela = new TabelaObjetosRemotos(config.host(), config.porta(), config.servico());
        tabela.registrarTipo(AutenticacaoRemota.class, s -> new EsqueletoAutenticacao(s, tabela));
        tabela.registrarTipo(EstoqueRemoto.class, s -> new EsqueletoEstoque(s, tabela));
        tabela.registrarTipo(PedidoReposicaoRemoto.class, s -> new EsqueletoPedidoReposicao(s, tabela));
        tabela.registrarTipo(NotificacoesRemotas.class, s -> new EsqueletoNotificacoes(s, tabela));
        tabela.registrarTipo(AssinaturaRemota.class, s -> new EsqueletoAssinatura(s, tabela));
        tabela.registrar(AutenticacaoRemota.NOME, autenticacao);
        tabela.registrar(EstoqueRemoto.NOME, estoque);
        tabela.registrar(NotificacoesRemotas.NOME, central);
        // fim do ciclo de vida da assinatura (saiu do grupo ou expirou): sai da tabela
        central.setAoEncerrar(tabela::remover);

        this.protocolo = new ProtocoloServidor();
        this.despachante = new Despachante(protocolo, tabela, config.registro(), config.detalhado());
    }

    /** Cria e inicia um servidor. */
    public static ServidorEstoque iniciar(Configuracao config) throws RemoteException {
        // endereço que o RMI grava nos stubs; precisa ser o IP da máquina para acesso pela rede
        System.setProperty("java.rmi.server.hostname", config.host());
        ServidorEstoque servidor = new ServidorEstoque(config);
        try {
            servidor.registro = LocateRegistry.createRegistry(config.porta());
            PortaServidora stub = servidor.protocolo.exportar(config.porta());
            servidor.registro.rebind(config.servico(), stub);
        } catch (RemoteException | RuntimeException e) {
            servidor.close();
            throw e;
        }
        servidor.despachante.iniciar();
        servidor.central.iniciar();
        servidor.monitor.iniciar();
        return servidor;
    }

    public Configuracao configuracao() { return config; }
    public EstoqueServico estoque() { return estoque; }
    public CentralNotificacoes central() { return central; }
    public AutenticacaoServico autenticacao() { return autenticacao; }
    public MonitorEstoque monitor() { return monitor; }
    public TabelaObjetosRemotos tabela() { return tabela; }
    public ProtocoloServidor protocolo() { return protocolo; }
    public Registry registro() { return registro; }

    @Override
    public synchronized void close() {
        monitor.close();
        central.close();
        if (registro != null) {
            try {
                registro.unbind(config.servico());
            } catch (RemoteException | NotBoundException ignorado) {
                // já fora do registro
            }
        }
        protocolo.encerrar();
        despachante.close();
        if (registro != null) {
            try {
                UnicastRemoteObject.unexportObject(registro, true);
            } catch (NoSuchObjectException ignorado) {
                // já encerrado
            }
            registro = null;
        }
    }

    // ------------------------------------------------------------------ linha de comando

    public static void main(String[] args) throws Exception {
        Configuracao padrao = Configuracao.padrao();
        String host = padrao.host();
        int porta = padrao.porta();
        long monitor = padrao.intervaloMonitorMs();
        boolean console = true;
        boolean detalhado = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = valor(args, ++i);
                case "--porta" -> porta = Integer.parseInt(valor(args, ++i));
                case "--monitor" -> monitor = Long.parseLong(valor(args, ++i)) * 1000;
                case "--sem-console" -> console = false;
                case "--detalhado" -> detalhado = true;
                case "--ajuda", "-h" -> {
                    uso();
                    return;
                }
                default -> {
                    System.err.println("Opção desconhecida: " + args[i]);
                    uso();
                    System.exit(2);
                }
            }
        }
        Configuracao config = new Configuracao(host, porta, padrao.servico(), monitor, padrao.leaseMs(),
                detalhado, padrao.relogio(), padrao.registro());

        ServidorEstoque servidor;
        try {
            servidor = iniciar(config);
        } catch (RemoteException e) {
            System.err.println("Não foi possível iniciar o servidor na porta " + porta + ": " + e.getMessage());
            System.err.println("Verifique se a porta está livre ou use --porta <outra>.");
            System.exit(1);
            return;
        }
        servidor.central.setAoPublicar(n -> System.out.println("[Grupo] " + n));
        System.out.println("Servidor do estoque veterinário (TD02 - Java RMI)");
        System.out.println("Registro RMI: " + host + ":" + porta + "  |  serviço: " + config.servico());
        System.out.println("Objetos remotos: " + servidor.tabela.nomes());
        System.out.println("Monitor de estoque a cada " + (monitor / 1000) + " s.");
        System.out.println("Servidor pronto.");

        Runtime.getRuntime().addShutdownHook(new Thread(servidor::close, "encerramento"));
        if (console) {
            Charset cs = Charset.forName(System.getProperty("stdout.encoding", Charset.defaultCharset().name()));
            new ConsoleServidor(servidor, new BufferedReader(new InputStreamReader(System.in, cs))).executar();
            System.out.println("Encerrando o servidor...");
            servidor.close();
            System.exit(0);
        } else {
            new CountDownLatch(1).await(); // roda até o processo ser encerrado (Ctrl+C)
        }
    }

    private static String valor(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("Falta o valor da opção " + args[i - 1]);
        return args[i];
    }

    private static void uso() {
        System.out.println("Uso: java -cp out/classes br.ufc.sd.estoque.servidor.ServidorEstoque [opções]");
        System.out.println("  --host <nome|IP>   endereço anunciado aos clientes (padrão localhost)");
        System.out.println("  --porta <n>        porta do registro RMI e dos objetos (padrão 1099)");
        System.out.println("  --monitor <s>      intervalo do monitor de estoque em segundos (padrão 10)");
        System.out.println("  --sem-console      não lê comandos do teclado");
        System.out.println("  --detalhado        registra também as chamadas de aguardar notificações");
    }
}
