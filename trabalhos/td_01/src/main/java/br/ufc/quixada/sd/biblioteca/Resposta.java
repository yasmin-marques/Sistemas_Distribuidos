package br.ufc.quixada.sd.biblioteca;

import java.io.Serializable;

// Exercício 3 - Representação externa de dados da mensagem de REPLY.

public class Resposta implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean sucesso;
    private final String mensagem;
    private final Object dado;

    public Resposta(boolean sucesso, String mensagem, Object dado) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
        this.dado = dado;
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
}
