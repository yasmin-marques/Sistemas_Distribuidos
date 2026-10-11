package clinica.serializacao;

import java.io.Serializable;

// Mensagem de reply: sucesso (com resultado) ou erro (com mensagem)
public class Resposta implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean sucesso;
    private final String mensagem;
    private final Serializable resultado;

    private Resposta(boolean sucesso, String mensagem, Serializable resultado) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
        this.resultado = resultado;
    }

    public static Resposta ok(Serializable resultado) {
        return new Resposta(true, "OK", resultado);
    }

    public static Resposta erro(String mensagem) {
        return new Resposta(false, mensagem, null);
    }

    public boolean isSucesso() { return sucesso; }
    public String getMensagem() { return mensagem; }
    public Serializable getResultado() { return resultado; }
}
