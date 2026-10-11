package clinica.votacao;

import java.io.IOException;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

// Stub do cliente da votação: cada método envia um pedido JSON e devolve a resposta do servidor
public class ClienteVotacao implements AutoCloseable {

    private final Socket socket;
    private final Canal canal;

    public ClienteVotacao(String host, int porta) throws IOException {
        this.socket = new Socket(host, porta);
        this.canal = new Canal(socket);
    }

    public RespostaVotacao login(String usuario, String senha) throws IOException {
        Map<String, Object> pedido = pedido("LOGIN");
        pedido.put("usuario", usuario);
        pedido.put("senha", senha);
        return chamar(pedido);
    }

    public RespostaVotacao listar() throws IOException {
        return chamar(pedido("LISTAR"));
    }

    public RespostaVotacao votar(int idCandidato) throws IOException {
        Map<String, Object> pedido = pedido("VOTAR");
        pedido.put("candidato", idCandidato);
        return chamar(pedido);
    }

    public RespostaVotacao resultado() throws IOException {
        return chamar(pedido("RESULTADO"));
    }

    public RespostaVotacao adicionarCandidato(String nome, String descricao) throws IOException {
        Map<String, Object> pedido = pedido("ADD_CANDIDATO");
        pedido.put("nome", nome);
        pedido.put("descricao", descricao);
        return chamar(pedido);
    }

    public RespostaVotacao removerCandidato(int idCandidato) throws IOException {
        Map<String, Object> pedido = pedido("REMOVER_CANDIDATO");
        pedido.put("candidato", idCandidato);
        return chamar(pedido);
    }

    public RespostaVotacao enviarNota(String texto) throws IOException {
        Map<String, Object> pedido = pedido("ENVIAR_NOTA");
        pedido.put("texto", texto);
        return chamar(pedido);
    }

    private static Map<String, Object> pedido(String operacao) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("op", operacao);
        return pedido;
    }

    private synchronized RespostaVotacao chamar(Map<String, Object> pedido) throws IOException {
        canal.enviar(pedido);
        Map<String, Object> resposta = canal.receber();
        if (resposta == null) throw new IOException("O servidor encerrou a conexão.");
        return new RespostaVotacao(resposta);
    }

    @Override
    public void close() throws IOException {
        try {
            canal.enviar(pedido("SAIR"));
        } finally {
            socket.close();
        }
    }
}
