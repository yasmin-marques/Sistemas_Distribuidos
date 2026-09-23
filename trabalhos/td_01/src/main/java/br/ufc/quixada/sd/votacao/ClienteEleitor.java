package br.ufc.quixada.sd.votacao;

import br.ufc.quixada.sd.multicast.NotificacaoMulticast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClienteEleitor {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Matrícula: ");
        String matricula = teclado.readLine();

        // Thread dedicada a escutar notas informativas via multicast (UDP)
        Thread threadEscuta = new Thread(ClienteEleitor::escutarNotas, "thread-escuta-notas");
        threadEscuta.setDaemon(true);
        threadEscuta.start();

        try (Socket socket = new Socket(host, ServidorVotacao.PORTA_TCP);
             ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())) {

            enviarErecerber(saida, entrada, new RequisicaoVotacao(TipoOperacaoVotacao.LOGIN_ELEITOR, matricula));

            boolean continuar = true;
            while (continuar) {
                System.out.println("\n== Votação ==");
                System.out.println("1 - Listar candidatos");
                System.out.println("2 - Votar");
                System.out.println("3 - Consultar resultado parcial/final");
                System.out.println("0 - Sair");
                System.out.print("Opção: ");
                String opcao = teclado.readLine();
                if (opcao == null) {
                    break;
                }
                switch (opcao.trim()) {
                    case "1":
                        enviarErecerber(saida, entrada, new RequisicaoVotacao(TipoOperacaoVotacao.LISTAR_CANDIDATOS));
                        break;
                    case "2":
                        System.out.print("ID do candidato: ");
                        int id = Integer.parseInt(teclado.readLine().trim());
                        enviarErecerber(saida, entrada, new RequisicaoVotacao(TipoOperacaoVotacao.VOTAR, id));
                        break;
                    case "3":
                        enviarErecerber(saida, entrada, new RequisicaoVotacao(TipoOperacaoVotacao.CONSULTAR_RESULTADO));
                        break;
                    case "0":
                        saida.writeObject(new RequisicaoVotacao(TipoOperacaoVotacao.SAIR));
                        saida.flush();
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opção inválida.");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void enviarErecerber(ObjectOutputStream saida, ObjectInputStream entrada,
                                         RequisicaoVotacao requisicao) throws IOException, ClassNotFoundException {
        saida.writeObject(requisicao);
        saida.flush();
        RespostaVotacao resposta = (RespostaVotacao) entrada.readObject();
        System.out.println((resposta.isSucesso() ? "[OK] " : "[FALHA] ") + resposta.getMensagem());
        Object dado = resposta.getDado();
        if (dado instanceof List) {
            for (Object item : (List<Candidato>) dado) {
                System.out.println("  " + item);
            }
        } else if (dado != null) {
            System.out.println(dado);
        }
    }

    private static void escutarNotas() {
        try {
            InetAddress grupo = InetAddress.getByName(ServidorVotacao.GRUPO_MULTICAST);
            try (MulticastSocket socket = new MulticastSocket(ServidorVotacao.PORTA_MULTICAST)) {
                socket.joinGroup(grupo);
                byte[] buffer = new byte[4096];
                while (true) {
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    socket.receive(pacote);
                    String json = new String(pacote.getData(), 0, pacote.getLength(), StandardCharsets.UTF_8);
                    NotificacaoMulticast nota = NotificacaoMulticast.fromJson(json);
                    System.out.println("\n>>> Nota da administração [" + nota.getTipo() + "]: " + nota.getMensagem());
                }
            }
        } catch (IOException e) {
            System.out.println("Encerrando escuta de notas: " + e.getMessage());
        }
    }
}
