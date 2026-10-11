package clinica.multicast;

import clinica.util.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

// QUESTÃO 4 - Cliente multicast
public class ClienteMulticast {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // Autentica no servidor e devolve o grupo multicast a ser usado.
    public static Grupo autenticar(String host, int porta, String usuario, String senha) throws IOException {
        try (Socket socket = new Socket(host, porta);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, Object> pedido = new LinkedHashMap<>();
            pedido.put("usuario", usuario);
            pedido.put("senha", senha);
            out.print(Json.escrever(pedido) + "\n");
            out.flush();

            String linha = in.readLine();
            if (linha == null) throw new IOException("O servidor encerrou a conexão sem responder.");
            Map<String, Object> resposta = Json.lerObjeto(linha);
            if (!Json.booleano(resposta, "sucesso")) {
                throw new IOException("Autenticação recusada: " + Json.texto(resposta, "mensagem"));
            }
            return new Grupo(Json.texto(resposta, "grupo"), (int) Json.inteiro(resposta, "porta"));
        }
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : ServidorMulticast.PORTA_AUTENTICACAO;

        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Usuário: ");
        String usuario = teclado.readLine();
        System.out.print("Senha: ");
        String senha = teclado.readLine();

        Grupo grupo;
        try {
            grupo = autenticar(host, porta, usuario, senha);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return;
        }
        System.out.println("Autenticado. Grupo multicast: " + grupo);

        try (OuvinteMulticast ouvinte = new OuvinteMulticast(grupo, ClienteMulticast::exibir)) {
            ouvinte.entrar();
            System.out.println("Você entrou no grupo. Comandos: entrar, deixar, status, sair");

            String linha;
            while ((linha = teclado.readLine()) != null) {
                switch (linha.trim().toLowerCase()) {
                    case "entrar":
                        ouvinte.entrar();
                        System.out.println("Entrou no grupo.");
                        break;
                    case "deixar":
                        ouvinte.sair();
                        System.out.println("Saiu do grupo (não recebe mais mensagens).");
                        break;
                    case "status":
                        System.out.println(ouvinte.estaNoGrupo() ? "No grupo " + grupo : "Fora do grupo.");
                        break;
                    case "sair":
                        System.out.println("Até logo!");
                        return;
                    case "":
                        break;
                    default:
                        System.out.println("Comandos: entrar, deixar, status, sair");
                }
            }
        }
    }

    private static void exibir(Notificacao n) {
        System.out.println("\n[" + HORA.format(Instant.ofEpochMilli(n.getTimestamp())) + "] " + n);
    }
}
