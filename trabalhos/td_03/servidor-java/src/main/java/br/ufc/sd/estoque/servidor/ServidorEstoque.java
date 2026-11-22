package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.AutenticacaoRemota;
import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.servico.AutenticacaoServico;
import br.ufc.sd.estoque.servico.CentralNotificacoes;
import br.ufc.sd.estoque.servico.DadosIniciais;
import br.ufc.sd.estoque.servico.EstoqueServico;
import br.ufc.sd.estoque.servico.MonitorEstoque;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import br.ufc.sd.estoque.web.ServidorHttp;
import br.ufc.sd.estoque.web.TabelaObjetosDistribuidos;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Servidor do estoque da clínica veterinária (TD03): API REST sobre HTTP.
 *
 * <p>Monta os objetos distribuídos (Autenticacao, Estoque e Notificacoes, mais os pedidos e
 * assinaturas criados em tempo de execução), registra-os na tabela de objetos, liga cada um ao
 * seu controlador e inicia a porta HTTP e o monitor de estoque. É um projeto independente: não
 * compartilha nenhum código com os clientes (Python e JavaScript), só o contrato HTTP/JSON.
 */
public final class ServidorEstoque implements AutoCloseable {

    public static final int PORTA_PADRAO = 8080;

    /** Parâmetros do servidor. Porta 0 escolhe uma porta livre (usado nos testes). */
    public record Configuracao(int porta, boolean somenteLocal, long intervaloMonitorMs, long leaseMs,
                               boolean detalhado, Clock relogio, Consumer<String> registro) {
        public Configuracao {
            Objects.requireNonNull(relogio);
            Objects.requireNonNull(registro);
        }

        public static Configuracao padrao() {
            return new Configuracao(PORTA_PADRAO, false, 10_000, 30_000, false, Clock.systemDefaultZone(),
                    System.out::println);
        }
    }

    private final Configuracao config;
    private final AutenticacaoServico autenticacao;
    private final CentralNotificacoes central;
    private final EstoqueServico estoque;
    private final MonitorEstoque monitor;
    private final TabelaObjetosDistribuidos tabela;
    private final ServidorHttp http;

    private ServidorEstoque(Configuracao config) throws IOException {
        this.config = config;
        this.autenticacao = new AutenticacaoServico(AutenticacaoServico.USUARIOS_PADRAO);
        this.central = new CentralNotificacoes(autenticacao, config.leaseMs());
        this.estoque = new EstoqueServico(autenticacao, central, config.relogio());
        DadosIniciais.semear(estoque);
        this.monitor = new MonitorEstoque(estoque, central, config.intervaloMonitorMs());
        estoque.setObservador(monitor::verificar);

        // objetos distribuídos: nome e endereço de cada um
        this.tabela = new TabelaObjetosDistribuidos();
        tabela.registrarTipoDinamico(PedidoReposicaoRemoto.class, ControladorPedidos.CAMINHO);
        tabela.registrarTipoDinamico(AssinaturaRemota.class, ControladorAssinaturas.CAMINHO);
        tabela.registrarFixo(AutenticacaoRemota.NOME, autenticacao, AutenticacaoRemota.class,
                ControladorAutenticacao.CAMINHO);
        tabela.registrarFixo(EstoqueRemoto.NOME, estoque, EstoqueRemoto.class, ControladorEstoque.CAMINHO);
        tabela.registrarFixo(NotificacoesRemotas.NOME, central, NotificacoesRemotas.class,
                ControladorNotificacoes.CAMINHO);
        // fim do ciclo de vida da assinatura (saiu do grupo ou expirou): sai da tabela
        central.setAoEncerrar(tabela::remover);

        Roteador roteador = new Roteador(config.registro());
        List<ControladorBase> controladores = List.of(
                new ControladorAutenticacao(autenticacao, tabela),
                new ControladorEstoque(estoque, tabela),
                new ControladorPedidos(tabela),
                new ControladorNotificacoes(central, tabela),
                new ControladorAssinaturas(central, tabela));
        controladores.forEach(c -> c.registrar(roteador));
        roteador.get("/", this::indice);
        roteador.get("/api", this::indice);

        this.http = new ServidorHttp(config.porta(), config.somenteLocal(), roteador, config.registro(),
                config.detalhado());
    }

    /** Cria e inicia um servidor. */
    public static ServidorEstoque iniciar(Configuracao config) throws IOException {
        ServidorEstoque servidor = new ServidorEstoque(config);
        servidor.http.iniciar();
        servidor.central.iniciar();
        servidor.monitor.iniciar();
        return servidor;
    }

    /** Descrição do serviço: lista os objetos distribuídos fixos e onde cada um responde. */
    private Resposta indice(br.ufc.sd.estoque.web.Requisicao q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("servico", "Estoque da clínica veterinária");
        m.put("versao", "TD03");
        m.put("objetos", tabela.descreverFixos(q.baseUrl()));
        return Resposta.ok(m);
    }

    public Configuracao configuracao() { return config; }
    public int porta() { return http.porta(); }
    public String url() { return "http://localhost:" + http.porta(); }
    public EstoqueServico estoque() { return estoque; }
    public CentralNotificacoes central() { return central; }
    public AutenticacaoServico autenticacao() { return autenticacao; }
    public MonitorEstoque monitor() { return monitor; }
    public TabelaObjetosDistribuidos tabela() { return tabela; }

    @Override
    public synchronized void close() {
        monitor.close();
        central.close();
        http.close();
    }

    // ------------------------------------------------------------------ linha de comando

    public static void main(String[] args) throws Exception {
        Configuracao padrao = Configuracao.padrao();
        int porta = padrao.porta();
        long monitor = padrao.intervaloMonitorMs();
        boolean somenteLocal = false;
        boolean console = true;
        boolean detalhado = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--porta" -> porta = Integer.parseInt(valor(args, ++i));
                case "--monitor" -> monitor = Long.parseLong(valor(args, ++i)) * 1000;
                case "--local" -> somenteLocal = true;
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
        Configuracao config = new Configuracao(porta, somenteLocal, monitor, padrao.leaseMs(), detalhado,
                padrao.relogio(), padrao.registro());

        ServidorEstoque servidor;
        try {
            servidor = iniciar(config);
        } catch (IOException e) {
            System.err.println("Não foi possível iniciar o servidor na porta " + porta + ": " + e.getMessage());
            System.err.println("Verifique se a porta está livre ou use --porta <outra>.");
            System.exit(1);
            return;
        }
        servidor.central.setAoPublicar(n -> System.out.println("[Grupo] " + n));
        System.out.println("Servidor do estoque veterinário (TD03 - API REST)");
        System.out.println("Escutando em " + (somenteLocal ? "localhost" : "todas as interfaces") + ", porta "
                + servidor.porta() + "  ->  http://localhost:" + servidor.porta() + "/api");
        System.out.println("Objetos distribuídos: " + servidor.tabela.nomes());
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
        System.out.println("  --porta <n>      porta HTTP (padrão 8080; 0 = qualquer porta livre)");
        System.out.println("  --local          aceita só conexões da própria máquina (padrão: todas as interfaces)");
        System.out.println("  --monitor <s>    intervalo do monitor de estoque em segundos (padrão 10)");
        System.out.println("  --sem-console    não lê comandos do teclado");
        System.out.println("  --detalhado      registra também as esperas de notificações (long polling)");
    }
}
