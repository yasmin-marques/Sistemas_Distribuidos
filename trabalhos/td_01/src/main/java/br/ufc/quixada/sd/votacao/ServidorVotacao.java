package br.ufc.quixada.sd.votacao;

import br.ufc.quixada.sd.multicast.NotificacaoMulticast;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Questão extra - Servidor da aplicação distribuída de votação.
 
//Login, lista de candidatos e voto: TCP unicast
//Notas informativas dos administradores: UDP multicast

public class ServidorVotacao {

    static final int PORTA_TCP = 5003;
    static final String GRUPO_MULTICAST = "230.0.0.2";
    static final int PORTA_MULTICAST = 4447;
    static final String SENHA_ADMIN = "admin123";

    private final List<Candidato> candidatos = new CopyOnWriteArrayList<>();
    private final AtomicInteger proximoId = new AtomicInteger(1);
    private final Set<String> jaVotaram = ConcurrentHashMap.newKeySet();
    private volatile boolean votacaoAberta = true;

    private final MulticastSocket socketMulticast;
    private final InetAddress grupo;

    public ServidorVotacao() throws IOException {
        this.socketMulticast = new MulticastSocket();
        this.grupo = InetAddress.getByName(GRUPO_MULTICAST);
    }

    public static void main(String[] args) throws IOException {
        int duracaoSegundos = args.length > 0 ? Integer.parseInt(args[0]) : 120;

        ServidorVotacao servidor = new ServidorVotacao();
        servidor.candidatos.add(new Candidato(servidor.proximoId.getAndIncrement(), "Chapa Conexão"));
        servidor.candidatos.add(new Candidato(servidor.proximoId.getAndIncrement(), "Chapa Renovação"));

        ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor();
        agendador.schedule(servidor::encerrarVotacao, duracaoSegundos, TimeUnit.SECONDS);

        System.out.println("ServidorVotacao ouvindo na porta " + PORTA_TCP
                + " (votação aberta por " + duracaoSegundos + "s)");
        try (ServerSocket servidorTcp = new ServerSocket(PORTA_TCP)) {
            while (true) {
                Socket cliente = servidorTcp.accept();
                new Thread(() -> servidor.atenderCliente(cliente)).start();
            }
        } finally {
            agendador.shutdownNow();
        }
    }

    private void atenderCliente(Socket cliente) {
        boolean[] ehAdmin = {false};
        String[] matriculaLogada = {null};
        try (Socket socket = cliente;
             ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())) {

            while (true) {
                RequisicaoVotacao requisicao = (RequisicaoVotacao) entrada.readObject();
                if (requisicao.getOperacao() == TipoOperacaoVotacao.SAIR) {
                    break;
                }
                RespostaVotacao resposta = processar(requisicao, ehAdmin, matriculaLogada);
                saida.writeObject(resposta);
                saida.flush();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Cliente desconectado: " + e.getMessage());
        }
    }

    private RespostaVotacao processar(RequisicaoVotacao requisicao, boolean[] ehAdmin, String[] matriculaLogada) {
        Object[] p = requisicao.getParametros();
        switch (requisicao.getOperacao()) {
            case LOGIN_ELEITOR: {
                String matricula = (String) p[0];
                if (matricula == null || matricula.trim().isEmpty()) {
                    return new RespostaVotacao(false, "Matrícula inválida.", null);
                }
                matriculaLogada[0] = matricula;
                return new RespostaVotacao(true, "Login de eleitor efetuado.", null);
            }
            case LOGIN_ADMIN: {
                String senha = (String) p[0];
                if (!SENHA_ADMIN.equals(senha)) {
                    return new RespostaVotacao(false, "Senha de administrador incorreta.", null);
                }
                ehAdmin[0] = true;
                return new RespostaVotacao(true, "Login de administrador efetuado.", null, true);
            }
            case LISTAR_CANDIDATOS:
                return new RespostaVotacao(true, "OK", List.copyOf(candidatos));
            case VOTAR: {
                if (matriculaLogada[0] == null) {
                    return new RespostaVotacao(false, "Faça login antes de votar.", null);
                }
                if (!votacaoAberta) {
                    return new RespostaVotacao(false, "Votação encerrada.", null);
                }
                if (!jaVotaram.add(matriculaLogada[0])) {
                    return new RespostaVotacao(false, "Você já votou.", null);
                }
                int idCandidato = (Integer) p[0];
                Candidato candidato = buscarCandidato(idCandidato);
                if (candidato == null) {
                    jaVotaram.remove(matriculaLogada[0]);
                    return new RespostaVotacao(false, "Candidato inválido.", null);
                }
                candidato.votar();
                return new RespostaVotacao(true, "Voto registrado em " + candidato.getNome() + ".", null);
            }
            case ADICIONAR_CANDIDATO: {
                if (!ehAdmin[0]) {
                    return new RespostaVotacao(false, "Apenas administradores podem adicionar candidatos.", null);
                }
                Candidato novo = new Candidato(proximoId.getAndIncrement(), (String) p[0]);
                candidatos.add(novo);
                return new RespostaVotacao(true, "Candidato adicionado.", novo);
            }
            case REMOVER_CANDIDATO: {
                if (!ehAdmin[0]) {
                    return new RespostaVotacao(false, "Apenas administradores podem remover candidatos.", null);
                }
                int id = (Integer) p[0];
                boolean removido = candidatos.removeIf(c -> c.getId() == id);
                return new RespostaVotacao(removido, removido ? "Candidato removido." : "Candidato não encontrado.", null);
            }
            case ENVIAR_NOTA: {
                if (!ehAdmin[0]) {
                    return new RespostaVotacao(false, "Apenas administradores podem enviar notas.", null);
                }
                enviarMulticast("NOTA_INFORMATIVA", (String) p[0]);
                return new RespostaVotacao(true, "Nota informativa enviada a todos os eleitores.", null);
            }
            case CONSULTAR_RESULTADO:
                return new RespostaVotacao(true, votacaoAberta ? "Votação em andamento." : "Votação encerrada.",
                        calcularResultado());
            default:
                return new RespostaVotacao(false, "Operação desconhecida.", null);
        }
    }

    private Candidato buscarCandidato(int id) {
        for (Candidato c : candidatos) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private void encerrarVotacao() {
        votacaoAberta = false;
        String resultado = calcularResultado();
        System.out.println("--- Votação encerrada ---\n" + resultado);
        enviarMulticast("RESULTADO", resultado);
    }

    private String calcularResultado() {
        int total = 0;
        for (Candidato c : candidatos) {
            total += c.getVotos();
        }
        if (total == 0) {
            return "Nenhum voto registrado até o momento.";
        }
        StringBuilder sb = new StringBuilder();
        Candidato vencedor = candidatos.get(0);
        for (Candidato c : candidatos) {
            double percentual = (c.getVotos() * 100.0) / total;
            sb.append(String.format("%s: %d voto(s) (%.1f%%)%n", c.getNome(), c.getVotos(), percentual));
            if (c.getVotos() > vencedor.getVotos()) {
                vencedor = c;
            }
        }
        sb.append("Total de votos: ").append(total).append(". Vencedor(a) parcial: ").append(vencedor.getNome());
        return sb.toString();
    }

    /** Empacota em JSON (reaproveitando NotificacaoMulticast) e envia ao grupo multicast. */
    private synchronized void enviarMulticast(String tipo, String mensagem) {
        try {
            NotificacaoMulticast notificacao = new NotificacaoMulticast(tipo, mensagem, System.currentTimeMillis());
            byte[] dados = notificacao.toJson().getBytes(StandardCharsets.UTF_8);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, grupo, PORTA_MULTICAST);
            socketMulticast.send(pacote);
        } catch (IOException e) {
            System.out.println("Falha ao enviar mensagem multicast: " + e.getMessage());
        }
    }
}
