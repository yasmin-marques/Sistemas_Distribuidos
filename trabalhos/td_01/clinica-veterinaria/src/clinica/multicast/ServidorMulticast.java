package clinica.multicast;

import clinica.modelo.Medicamento;
import clinica.servico.DadosIniciais;
import clinica.servico.EstoqueServico;
import clinica.util.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Lado do servidor multicast da clínica
// A autenticação é feita por TCP (unicast, as mensagens saem por UDP para o grupo

public class ServidorMulticast implements AutoCloseable {

    public static final String GRUPO = "230.0.0.1";
    public static final int PORTA_MULTICAST = 4446;
    public static final int PORTA_AUTENTICACAO = 5002;

    private static final Set<String> TIPOS =
            Set.of(Notificacao.NOTIFICACAO, Notificacao.ALERTA, Notificacao.ATUALIZACAO);

    private final Map<String, String> usuarios = Map.of(
            "veterinario", "vet123",
            "recepcao", "rec123",
            "estoquista", "est123");
    private final Set<String> autenticados = ConcurrentHashMap.newKeySet();

    private final ServerSocket servidorTcp;
    private final MulticastSocket socketUdp;
    private final InetAddress enderecoGrupo;
    private final Grupo grupo;

    private final ExecutorService atendimento = Executors.newCachedThreadPool(ServidorMulticast::daemon);
    private final ExecutorService envio = Executors.newFixedThreadPool(4, ServidorMulticast::daemon);

    public ServidorMulticast(int portaAutenticacao, String endereco, int portaMulticast) throws IOException {
        this.grupo = new Grupo(endereco, portaMulticast);
        this.enderecoGrupo = InetAddress.getByName(endereco);
        this.servidorTcp = new ServerSocket(portaAutenticacao);
        this.socketUdp = new MulticastSocket();
        this.socketUdp.setNetworkInterface(RedeMulticast.escolherInterface());
        this.socketUdp.setTimeToLive(1);
    }

    private static Thread daemon(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }

    public int getPortaAutenticacao() {
        return servidorTcp.getLocalPort();
    }

    public Set<String> getAutenticados() {
        return new TreeSet<>(autenticados);
    }

    // Começa a aceitar autenticações (uma thread por cliente) e retorna
    public void iniciar() {
        Thread aceitador = new Thread(() -> {
            while (!servidorTcp.isClosed()) {
                try {
                    Socket cliente = servidorTcp.accept();
                    atendimento.execute(() -> autenticar(cliente));
                } catch (SocketException e) {
                    return;     // servidor fechado
                } catch (IOException e) {
                    System.err.println("[Servidor] Erro no accept: " + e);
                }
            }
        }, "thread-autenticacao");
        aceitador.setDaemon(true);
        aceitador.start();
    }

    private void autenticar(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(15000);
            String linha = in.readLine();
            if (linha == null) return;

            Map<String, Object> resposta = new LinkedHashMap<>();
            try {
                Map<String, Object> pedido = Json.lerObjeto(linha);
                String usuario = Json.texto(pedido, "usuario");
                String senha = Json.texto(pedido, "senha");
                if (usuario != null && senha != null && senha.equals(usuarios.get(usuario))) {
                    autenticados.add(usuario);
                    resposta.put("sucesso", true);
                    resposta.put("mensagem", "Autenticado.");
                    resposta.put("grupo", grupo.getEndereco());
                    resposta.put("porta", grupo.getPorta());
                    System.out.println("[Servidor] Autenticado: " + usuario + " (" + socket.getRemoteSocketAddress() + ")");
                } else {
                    resposta.put("sucesso", false);
                    resposta.put("mensagem", "Usuário ou senha inválidos.");
                }
            } catch (IllegalArgumentException e) {
                resposta.put("sucesso", false);
                resposta.put("mensagem", "Requisição inválida.");
            }
            out.print(Json.escrever(resposta) + "\n");
            out.flush();
        } catch (IOException e) {
            System.err.println("[Servidor] Falha na autenticação: " + e.getMessage());
        }
    }

    // Agenda o envio para o grupo; várias threads podem chamar ao mesmo tempo
    public Future<?> enviar(String tipo, String mensagem) {
        if (!TIPOS.contains(tipo)) {
            throw new IllegalArgumentException("Tipo inválido: " + tipo + " (use " + TIPOS + ")");
        }
        Notificacao notificacao = new Notificacao(tipo, mensagem, System.currentTimeMillis());
        byte[] dados = notificacao.toJson().getBytes(StandardCharsets.UTF_8);
        if (dados.length > Notificacao.TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Mensagem grande demais para um datagrama.");
        }
        return envio.submit(() -> transmitir(notificacao, dados));
    }

    private void transmitir(Notificacao notificacao, byte[] dados) {
        try {
            socketUdp.send(new DatagramPacket(dados, dados.length, enderecoGrupo, grupo.getPorta()));
            System.out.println("[Servidor] Enviado: " + notificacao);
        } catch (IOException e) {
            System.err.println("[Servidor] Falha ao enviar: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        servidorTcp.close();
        socketUdp.close();
        atendimento.shutdownNow();
        envio.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        EstoqueServico estoque = new EstoqueServico();
        DadosIniciais.semearEstoque(estoque);

        try (ServidorMulticast servidor = new ServidorMulticast(PORTA_AUTENTICACAO, GRUPO, PORTA_MULTICAST)) {
            MonitorEstoque monitor = new MonitorEstoque(estoque, servidor, 10, 10);
            servidor.iniciar();
            monitor.iniciar();
            System.out.println("[Servidor] Autenticação (TCP) na porta " + PORTA_AUTENTICACAO
                    + ", grupo multicast " + servidor.grupo);
            ajuda();
            console(servidor, estoque);
            monitor.parar();
        }
    }

    private static void console(ServidorMulticast servidor, EstoqueServico estoque) throws IOException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        String linha;
        while ((linha = teclado.readLine()) != null) {
            String[] partes = linha.trim().split("\\s+", 2);
            String comando = partes[0].toUpperCase();
            String resto = partes.length > 1 ? partes[1] : "";
            try {
                switch (comando) {
                    case "":
                        break;
                    case "SAIR":
                        return;
                    case "AJUDA":
                        ajuda();
                        break;
                    case "CLIENTES":
                        System.out.println("Autenticados: " + servidor.getAutenticados());
                        break;
                    case "ESTOQUE":
                        estoque.listar().forEach(System.out::println);
                        break;
                    case "BAIXAR": {
                        String[] a = resto.split("\\s+");
                        Medicamento m = estoque.baixar(Integer.parseInt(a[0]), Integer.parseInt(a[1]));
                        System.out.println("Baixa feita: " + m);
                        break;
                    }
                    case "REPOR": {
                        String[] a = resto.split("\\s+");
                        Medicamento m = estoque.repor(Integer.parseInt(a[0]), Integer.parseInt(a[1]));
                        servidor.enviar(Notificacao.ATUALIZACAO,
                                "Estoque reposto: " + m.getNome() + " agora com " + m.getQuantidade() + " unidade(s).");
                        break;
                    }
                    default:
                        if (resto.isEmpty()) {
                            System.out.println("Comando inválido. Digite AJUDA.");
                        } else {
                            servidor.enviar(comando, resto);
                        }
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.out.println("Uso: " + comando.toLowerCase() + " <id> <quantidade>");
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("Erro: " + e.getMessage());
            }
        }
    }

    private static void ajuda() {
        System.out.println("Comandos:");
        System.out.println("  NOTIFICACAO|ALERTA|ATUALIZACAO <mensagem>   envia ao grupo");
        System.out.println("  baixar <id> <qtd>   dá baixa no estoque (o monitor avisa se ficar baixo)");
        System.out.println("  repor <id> <qtd>    repõe o estoque e envia uma ATUALIZACAO");
        System.out.println("  estoque | clientes | ajuda | sair");
    }
}
