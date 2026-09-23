package br.ufc.quixada.sd.votacao;

import java.io.Serializable;

public class RespostaVotacao implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean sucesso;
    private final String mensagem;
    private final Object dado;
    private final boolean admin;

    public RespostaVotacao(boolean sucesso, String mensagem, Object dado) {
        this(sucesso, mensagem, dado, false);
    }

    public RespostaVotacao(boolean sucesso, String mensagem, Object dado, boolean admin) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
        this.dado = dado;
        this.admin = admin;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Object getDado() {
        return dado;
    }

    public boolean isAdmin() {
        return admin;
    }
}
