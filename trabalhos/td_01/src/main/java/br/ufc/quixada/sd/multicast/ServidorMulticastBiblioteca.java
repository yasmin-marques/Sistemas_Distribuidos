package br.ufc.quixada.sd.multicast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Exercício 4 (b,d) - Multicast.

public class ServidorMulticastBiblioteca {

    static final String GRUPO_MULTICAST = "230.0.0.1";
    static final int PORTA_MULTICAST = 4446;
    static final int PORTA_AUTENTICACAO = 5002;

    private final MulticastSocket socketMulticast;
    private final InetAddress grupo;

    public ServidorMulticastBiblioteca() throws IOException {
        this.socketMulticast = new MulticastSocket();
        this.grupo = InetAddress.getByName(GRUPO_MULTICAST);
    }

    public static void main(String[] args) throws IOException {
        ServidorMulticastBiblioteca servidor = new ServidorMulticastBiblioteca();
        servidor.iniciarThreadAutenticacao();
        servidor.loopDeConsole();
    }

    // Thread dedicada a aceitar e autenticar clientes via TCP (unicast)
    private void iniciarThreadAutenticacao() {
        Thread thread = new Thread(() -> {
            try (ServerSocket servidorTcp = new ServerSocket(PORTA_AUTENTICACAO)) {
                System.out.println("Autenticação (TCP) ouvindo na porta " + PORTA_AUTENTICACAO);
                while (true) {
                    Socket cliente = servidorTcp.accept();
                    new Thread(new AutenticacaoWorker(cliente)).start();
                }
            } catch (IOException e) {
                System.out.println("Encerrando thread de autenticação: " + e.getMessage());
            }
        }, "thread-autenticacao");

        thread.setDaemon(true);
        thread.start();
    }

    // Lê comandos do administrador e dispara notificações multicast
    private void loopDeConsole() throws IOException {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Grupo multicast " + GRUPO_MULTICAST + ":" + PORTA_MULTICAST);
        System.out.println("Digite: <NOTIFICACAO|ALERTA|ATUALIZACAO> <mensagem>  (ou 'sair')");
        String linha;
        while ((linha = teclado.readLine()) != null) {
            if (linha.trim().equalsIgnoreCase("sair")) {
                break;
            }
            String[] partes = linha.split(" ", 2);
            if (partes.length < 2) {
                System.out.println("Formato inválido. Ex: NOTIFICACAO Novo dado disponível");
                continue;
            }
            enviarNotificacao(partes[0].toUpperCase(), partes[1]);
        }
        socketMulticast.close();
    }

    // Empacota a notificação em JSON e envia ao grupo multicast (thread-safe)
    synchronized void enviarNotificacao(String tipo, String mensagem) {
        try {
            NotificacaoMulticast notificacao = new NotificacaoMulticast(tipo, mensagem, System.currentTimeMillis());
            byte[] dados = notificacao.toJson().getBytes(StandardCharsets.UTF_8);
            DatagramPacket pacote = new DatagramPacket(dados, dados.length, grupo, PORTA_MULTICAST);
            socketMulticast.send(pacote);
            System.out.println("Enviado: " + notificacao);
        } catch (IOException e) {
            System.out.println("Falha ao enviar notificação multicast: " + e.getMessage());
        }
    }

    // Uma thread por cliente que se autentica via TCP antes de entrar no grupo multicast
    private static class AutenticacaoWorker implements Runnable {

        private final Socket socket;

        AutenticacaoWorker(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (Socket s = socket;
                 BufferedReader entrada = new BufferedReader(new InputStreamReader(s.getInputStream()));
                 PrintWriter saida = new PrintWriter(s.getOutputStream(), true)) {

                String usuario = entrada.readLine();
                if (usuario == null || usuario.trim().isEmpty()) {
                    saida.println("ERRO usuario_vazio");
                    return;
                }
                System.out.println("Cliente autenticado: " + usuario + " (" + s.getRemoteSocketAddress() + ")");
                saida.println("OK " + GRUPO_MULTICAST + " " + PORTA_MULTICAST);
            } catch (IOException e) {
                System.out.println("Erro na autenticação: " + e.getMessage());
            }
        }
    }
}
