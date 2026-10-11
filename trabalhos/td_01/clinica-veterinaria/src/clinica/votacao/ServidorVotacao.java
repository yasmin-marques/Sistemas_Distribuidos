package clinica.votacao;

import clinica.multicast.Grupo;
import clinica.multicast.Notificacao;
import clinica.multicast.RedeMulticast;
import clinica.util.Json;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// QUESTÃO EXTRA - Servidor da votação
public class ServidorVotacao implements AutoCloseable {

    public static final int PORTA_TCP = 5003;
    public static final String GRUPO = "230.0.0.2";
    public static final int PORTA_MULTICAST = 4447;

    private static class Sessao {
        String usuario;
        Contas.Papel papel;
    }

    private final Urna urna = new Urna();
    private final Contas contas;
    private final Grupo grupo;
    private final int duracaoSegundos;

    private final ServerSocket servidorTcp;
    private final MulticastSocket socketUdp;
    private final InetAddress enderecoGrupo;

    private final ExecutorService atendimento = Executors.newCachedThreadPool(ServidorVotacao::daemon);
    private final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(ServidorVotacao::daemon);
    private final CountDownLatch encerrada = new CountDownLatch(1);
    private volatile long fimEm;

    public ServidorVotacao(int portaTcp, Grupo grupo, int duracaoSegundos, Contas contas) throws IOException {
        this.grupo = grupo;
        this.duracaoSegundos = duracaoSegundos;
        this.contas = contas;
        this.enderecoGrupo = InetAddress.getByName(grupo.getEndereco());
        this.servidorTcp = new ServerSocket(portaTcp);
        this.socketUdp = new MulticastSocket();
        this.socketUdp.setNetworkInterface(RedeMulticast.escolherInterface());
        this.socketUdp.setTimeToLive(1);
    }

    private static Thread daemon(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }

    public Urna getUrna() { return urna; }

    public int getPorta() { return servidorTcp.getLocalPort(); }

    // Bloqueia até o prazo da votação acabar; devolve false se o tempo informado passar antes
    public boolean aguardarEncerramento(long timeoutSegundos) throws InterruptedException {
        return encerrada.await(timeoutSegundos, TimeUnit.SECONDS);
    }

    // Começa a contar o prazo e a aceitar conexões em outra thread
    public void iniciar() {
        Thread aceitador = new Thread(this::executar, "thread-aceitador");
        aceitador.setDaemon(true);
        aceitador.start();
    }

    // Conta o prazo e aceita conexões na thread atual (bloqueia até fechar o servidor)
    public void executar() {
        fimEm = System.currentTimeMillis() + duracaoSegundos * 1000L;
        agendador.schedule(this::encerrarVotacao, duracaoSegundos, TimeUnit.SECONDS);
        System.out.println("[Votação] TCP na porta " + getPorta() + ", multicast " + grupo
                + ", votação aberta por " + duracaoSegundos + "s");
        while (!servidorTcp.isClosed()) {
            try {
                Socket cliente = servidorTcp.accept();
                atendimento.execute(() -> atender(cliente));
            } catch (SocketException e) {
                return;
            } catch (IOException e) {
                System.err.println("[Votação] Erro no accept: " + e);
            }
        }
    }

    private void encerrarVotacao() {
        Resultado resultado = urna.encerrar();
        System.out.println("[Votação] Votação encerrada.\n" + resultado);
        encerrada.countDown();
    }

    private void atender(Socket socket) {
        String origem = String.valueOf(socket.getRemoteSocketAddress());
        Sessao sessao = new Sessao();
        try (socket; Canal canal = new Canal(socket)) {
            socket.setSoTimeout(5 * 60 * 1000);
            while (true) {
                Map<String, Object> pedido;
                try {
                    pedido = canal.receber();
                } catch (IllegalArgumentException e) {
                    canal.enviar(erro("Mensagem inválida."));
                    continue;
                }
                if (pedido == null) break;
                if ("SAIR".equals(pedido.get("op"))) {
                    canal.enviar(ok("Até logo."));
                    break;
                }
                canal.enviar(processar(pedido, sessao));
            }
        } catch (IOException e) {
            System.out.println("[Votação] Conexão com " + origem + " encerrada: " + e.getMessage());
        }
    }

    private Map<String, Object> processar(Map<String, Object> pedido, Sessao sessao) {
        try {
            String op = Json.texto(pedido, "op");
            if (op == null) return erro("Pedido sem operação.");
            if (op.equals("LOGIN")) return login(pedido, sessao);
            if (sessao.papel == null) return erro("Faça login primeiro.");

            switch (op) {
                case "LISTAR":
                    return comCandidatos(ok("Candidatos em votação."));
                case "VOTAR": {
                    exigir(sessao, Contas.Papel.ELEITOR, "Só eleitores podem votar.");
                    Candidato c = urna.votar(sessao.usuario, (int) Json.inteiro(pedido, "candidato"));
                    System.out.println("[Votação] " + sessao.usuario + " votou.");
                    return ok("Voto registrado em " + c.getNome() + ".");
                }
                case "RESULTADO":
                    return resultado();
                case "ADD_CANDIDATO": {
                    exigir(sessao, Contas.Papel.ADMIN, "Só administradores podem adicionar candidatos.");
                    Candidato c = urna.adicionar(Json.texto(pedido, "nome"), Json.texto(pedido, "descricao"));
                    return ok("Candidato adicionado: " + c);
                }
                case "REMOVER_CANDIDATO": {
                    exigir(sessao, Contas.Papel.ADMIN, "Só administradores podem remover candidatos.");
                    int liberados = urna.remover((int) Json.inteiro(pedido, "candidato"));
                    return ok("Candidato removido." + (liberados > 0
                            ? " " + liberados + " eleitor(es) poderão votar de novo." : ""));
                }
                case "ENVIAR_NOTA": {
                    exigir(sessao, Contas.Papel.ADMIN, "Só administradores podem enviar notas.");
                    enviarNota(Json.texto(pedido, "texto"));
                    return ok("Nota enviada ao grupo multicast.");
                }
                default:
                    return erro("Operação desconhecida: " + op);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            return erro(e.getMessage());
        } catch (IOException e) {
            return erro("Falha ao enviar a nota: " + e.getMessage());
        }
    }

    private Map<String, Object> login(Map<String, Object> pedido, Sessao sessao) {
        String usuario = Json.texto(pedido, "usuario");
        Contas.Papel papel = contas.autenticar(usuario, Json.texto(pedido, "senha"));
        if (papel == null) return erro("Usuário ou senha inválidos.");

        sessao.usuario = usuario;
        sessao.papel = papel;
        System.out.println("[Votação] Login: " + usuario + " (" + papel + ")");

        Map<String, Object> resposta = comCandidatos(ok("Bem-vindo, " + usuario + "."));
        resposta.put("papel", papel.name());
        resposta.put("grupo", grupo.getEndereco());
        resposta.put("porta", grupo.getPorta());
        return resposta;
    }

    private Map<String, Object> resultado() {
        Resultado resultado = urna.getResultadoFinal();
        if (resultado == null) {
            Map<String, Object> resposta = ok("Votação em andamento. Restam " + segundosRestantes() + "s.");
            resposta.put("restante", segundosRestantes());
            return resposta;
        }
        Map<String, Object> resposta = ok("Votação encerrada.");
        resposta.put("restante", 0L);
        resposta.put("resultado", resultado.toMapa());
        return resposta;
    }

    private void enviarNota(String texto) throws IOException {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("A nota está vazia.");
        Notificacao nota = new Notificacao(Notificacao.NOTA_INFORMATIVA, texto.trim(), System.currentTimeMillis());
        byte[] dados = nota.toJson().getBytes(StandardCharsets.UTF_8);
        if (dados.length > Notificacao.TAMANHO_MAXIMO) throw new IllegalArgumentException("Nota grande demais.");
        socketUdp.send(new DatagramPacket(dados, dados.length, enderecoGrupo, grupo.getPorta()));
        System.out.println("[Votação] Nota enviada: " + nota);
    }

    private long segundosRestantes() {
        return Math.max(0, (fimEm - System.currentTimeMillis() + 999) / 1000);
    }

    private Map<String, Object> comCandidatos(Map<String, Object> resposta) {
        List<Object> lista = new ArrayList<>();
        for (Candidato c : urna.listar()) lista.add(c.toMapa());
        resposta.put("candidatos", lista);
        resposta.put("restante", urna.estaAberta() ? segundosRestantes() : 0L);
        return resposta;
    }

    private static void exigir(Sessao sessao, Contas.Papel papel, String mensagem) {
        if (sessao.papel != papel) throw new IllegalStateException(mensagem);
    }

    private static Map<String, Object> ok(String mensagem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sucesso", true);
        m.put("mensagem", mensagem);
        return m;
    }

    private static Map<String, Object> erro(String mensagem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sucesso", false);
        m.put("mensagem", mensagem);
        return m;
    }

    @Override
    public void close() throws IOException {
        servidorTcp.close();
        socketUdp.close();
        atendimento.shutdownNow();
        agendador.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        int duracao = args.length > 0 ? Integer.parseInt(args[0]) : 120;
        try (ServidorVotacao servidor = new ServidorVotacao(PORTA_TCP, new Grupo(GRUPO, PORTA_MULTICAST), duracao, Contas.padrao())) {
            servidor.urna.adicionar("Rex", "Cachorro, Labrador");
            servidor.urna.adicionar("Mimi", "Gato, Siamês");
            servidor.urna.adicionar("Thor", "Coelho, Mini Lop");
            servidor.executar();
        }
    }
}
