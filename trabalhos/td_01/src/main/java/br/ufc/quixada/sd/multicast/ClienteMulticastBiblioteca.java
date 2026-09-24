package br.ufc.quixada.sd.multicast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;

// Exercício 4 - cliente multicast.
public class ClienteMulticastBiblioteca {

    private static final DateTimeFormatter FORMATO_HORA
            = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";

        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Usuário: ");
        String usuario = teclado.readLine();

        // 1. Autenticação via TCP (unicast)
        String grupo;
        int portaMulticast;
        try (Socket socket = new Socket(host, ServidorMulticastBiblioteca.PORTA_AUTENTICACAO); PrintWriter saida = new PrintWriter(socket.getOutputStream(), true); BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            saida.println(usuario);
            String resposta = entrada.readLine();
            if (resposta == null || !resposta.startsWith("OK")) {
                System.out.println("Falha na autenticação: " + resposta);
                return;
            }
            String[] partes = resposta.split(" ");
            grupo = partes[1];
            portaMulticast = Integer.parseInt(partes[2]);
            System.out.println("Autenticado com sucesso. Entrando no grupo " + grupo + ":" + portaMulticast);
        }

        // 2. Entra no grupo multicast (UDP)
        InetAddress enderecoGrupo = InetAddress.getByName(grupo);
        MulticastSocket socketMulticast = new MulticastSocket(portaMulticast);
        InetSocketAddress endpointGrupo = new InetSocketAddress(enderecoGrupo, portaMulticast);
        NetworkInterface interfaceRede = escolherInterfaceMulticast();
        socketMulticast.joinGroup(endpointGrupo, interfaceRede);

        Thread threadEscuta = new Thread(() -> escutarMulticast(socketMulticast), "thread-escuta-multicast");
        threadEscuta.setDaemon(true);
        threadEscuta.start();

        // 3. Thread principal cuida da interação com o usuário
        System.out.println("Digite 'sair' para deixar o grupo e encerrar.");
        String linha;
        while ((linha = teclado.readLine()) != null) {
            if (linha.trim().equalsIgnoreCase("sair")) {
                break;
            }
            System.out.println("(este cliente apenas recebe notificações; comandos disponíveis: sair)");
        }

        socketMulticast.leaveGroup(endpointGrupo, interfaceRede);
        socketMulticast.close();
        System.out.println("Grupo multicast abandonado. Até logo!");
    }

    private static void escutarMulticast(MulticastSocket socketMulticast) {
        byte[] buffer = new byte[4096];
        while (!socketMulticast.isClosed()) {
            try {
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                socketMulticast.receive(pacote);
                String json = new String(pacote.getData(), 0, pacote.getLength(), StandardCharsets.UTF_8);
                NotificacaoMulticast notificacao = NotificacaoMulticast.fromJson(json);
                String hora = FORMATO_HORA.format(Instant.ofEpochMilli(notificacao.getTimestamp()));
                System.out.println("\n[" + hora + "] " + notificacao);
            } catch (IOException e) {
                if (!socketMulticast.isClosed()) {
                    System.out.println("Erro ao receber notificação: " + e.getMessage());
                }
            }
        }
    }

    private static NetworkInterface escolherInterfaceMulticast() throws IOException {

        Enumeration<NetworkInterface> interfaces
                = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {

            NetworkInterface interfaceAtual = interfaces.nextElement();

            if (!interfaceAtual.isUp()
                    || interfaceAtual.isLoopback()
                    || !interfaceAtual.supportsMulticast()) {
                continue;
            }

            Enumeration<InetAddress> enderecos
                    = interfaceAtual.getInetAddresses();

            while (enderecos.hasMoreElements()) {

                InetAddress endereco = enderecos.nextElement();

                // Prioriza interfaces IPv4 reais
                if (endereco instanceof java.net.Inet4Address) {

                    System.out.println(
                            "Interface escolhida: "
                            + interfaceAtual.getName()
                            + " - "
                            + endereco.getHostAddress()
                    );

                    return interfaceAtual;
                }
            }
        }

        throw new IOException(
                "Nenhuma interface IPv4 compatível com multicast encontrada."
        );
    }
}
