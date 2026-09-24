package br.ufc.quixada.sd.multicast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Exercício 4 (b,d) - Multicast.

public class ServidorMulticastBiblioteca {

    static final String GRUPO_MULTICAST = "230.0.0.1";
    static final int PORTA_MULTICAST = 4446;
    static final int PORTA_AUTENTICACAO = 5002;

    // IP da interface física do servidor
    static final String IP_SERVIDOR = "localhost";

    private final MulticastSocket socketMulticast;
    private final InetAddress grupo;

    public ServidorMulticastBiblioteca() throws IOException {

        this.socketMulticast = new MulticastSocket();

        NetworkInterface interfaceRede = escolherInterfaceMulticast();

        this.socketMulticast.setNetworkInterface(interfaceRede);

        this.socketMulticast.setTimeToLive(1);

        this.grupo = InetAddress.getByName(GRUPO_MULTICAST);

        System.out.println(
                "Interface multicast configurada: "
                + interfaceRede.getDisplayName()
        );
    }

    public static void main(String[] args) throws IOException {

        ServidorMulticastBiblioteca servidor =
                new ServidorMulticastBiblioteca();

        servidor.iniciarThreadAutenticacao();
        servidor.loopDeConsole();
    }

     // Seleciona a interface de rede correspondente ao IP físico
     // do servidor.

    private static NetworkInterface escolherInterfaceMulticast()
            throws IOException {

        InetAddress enderecoServidor =
                InetAddress.getByName(IP_SERVIDOR);

        NetworkInterface iface =
                NetworkInterface.getByInetAddress(enderecoServidor);

        if (iface == null) {
            throw new IOException(
                    "Não foi encontrada uma interface com o IP "
                    + IP_SERVIDOR
            );
        }

        if (!iface.isUp()) {
            throw new IOException(
                    "A interface "
                    + iface.getDisplayName()
                    + " não está ativa."
            );
        }

        if (!iface.supportsMulticast()) {
            throw new IOException(
                    "A interface "
                    + iface.getDisplayName()
                    + " não suporta multicast."
            );
        }

        System.out.println(
                "IPv4 multicast do servidor: "
                + enderecoServidor.getHostAddress()
        );

        System.out.println(
                "Interface multicast do servidor: "
                + iface.getDisplayName()
        );

        return iface;
    }

    private void iniciarThreadAutenticacao() {

        Thread thread = new Thread(() -> {

            try (
                    ServerSocket servidorTcp =
                            new ServerSocket(PORTA_AUTENTICACAO)
            ) {

                System.out.println(
                        "Autenticação (TCP) ouvindo na porta "
                        + PORTA_AUTENTICACAO
                );

                while (true) {

                    Socket cliente = servidorTcp.accept();

                    new Thread(
                            new AutenticacaoWorker(cliente)
                    ).start();
                }

            } catch (IOException e) {

                System.out.println(
                        "Encerrando thread de autenticação: "
                        + e.getMessage()
                );
            }

        }, "thread-autenticacao");

        thread.setDaemon(true);
        thread.start();
    }

    private void loopDeConsole() throws IOException {

        BufferedReader teclado =
                new BufferedReader(
                        new InputStreamReader(System.in)
                );

        System.out.println(
                "Grupo multicast "
                + GRUPO_MULTICAST
                + ":"
                + PORTA_MULTICAST
        );

        System.out.println(
                "Digite: <NOTIFICACAO|ALERTA|ATUALIZACAO> "
                + "<mensagem> (ou 'sair')"
        );

        String linha;

        while ((linha = teclado.readLine()) != null) {

            if (linha.trim().equalsIgnoreCase("sair")) {
                break;
            }

            String[] partes = linha.split(" ", 2);

            if (partes.length < 2) {

                System.out.println(
                        "Formato inválido. "
                        + "Ex: NOTIFICACAO Novo dado disponível"
                );

                continue;
            }

            String tipo = partes[0].toUpperCase();
            String mensagem = partes[1];

            enviarNotificacao(tipo, mensagem);
        }

        socketMulticast.close();
    }

    synchronized void enviarNotificacao(
            String tipo,
            String mensagem
    ) {

        try {

            NotificacaoMulticast notificacao =
                    new NotificacaoMulticast(
                            tipo,
                            mensagem,
                            System.currentTimeMillis()
                    );

            byte[] dados =
                    notificacao
                            .toJson()
                            .getBytes(StandardCharsets.UTF_8);

            DatagramPacket pacote =
                    new DatagramPacket(
                            dados,
                            dados.length,
                            grupo,
                            PORTA_MULTICAST
                    );

            socketMulticast.send(pacote);

            System.out.println(
                    "Enviado: " + notificacao
            );

        } catch (IOException e) {

            System.out.println(
                    "Falha ao enviar notificação multicast: "
                    + e.getMessage()
            );
        }
    }
    private static class AutenticacaoWorker
            implements Runnable {

        private final Socket socket;

        AutenticacaoWorker(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try (
                    Socket s = socket;

                    BufferedReader entrada =
                            new BufferedReader(
                                    new InputStreamReader(
                                            s.getInputStream()
                                    )
                            );

                    PrintWriter saida =
                            new PrintWriter(
                                    s.getOutputStream(),
                                    true
                            )
            ) {

                String usuario = entrada.readLine();

                if (usuario == null
                        || usuario.trim().isEmpty()) {

                    saida.println(
                            "ERRO usuario_vazio"
                    );

                    return;
                }

                System.out.println(
                        "Cliente autenticado: "
                        + usuario
                        + " ("
                        + s.getRemoteSocketAddress()
                        + ")"
                );

                // Informa ao cliente o grupo e a porta multicast
                saida.println(
                        "OK "
                        + GRUPO_MULTICAST
                        + " "
                        + PORTA_MULTICAST
                );

            } catch (IOException e) {

                System.out.println(
                        "Erro na autenticação: "
                        + e.getMessage()
                );
            }
        }
    }
}