package clinica.votacao;

import clinica.util.Json;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Conexão TCP que troca uma mensagem JSON por linha
class Canal implements Closeable {

    private static final int LIMITE_LINHA = 64 * 1024;

    private final BufferedReader in;
    private final PrintWriter out;

    Canal(Socket socket) throws IOException {
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    synchronized void enviar(Map<String, Object> mensagem) {
        out.print(Json.escrever(mensagem) + "\n");
        out.flush();
    }

    /** @return a próxima mensagem, ou {@code null} se a outra ponta encerrou a conexão. */
    Map<String, Object> receber() throws IOException {
        StringBuilder linha = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') return Json.lerObjeto(linha.toString());
            if (c != '\r') linha.append((char) c);
            if (linha.length() > LIMITE_LINHA) throw new IOException("Mensagem maior que o limite permitido.");
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        out.close();
        in.close();
    }
}
