package br.ufc.quixada.sd.biblioteca;

import java.io.Serializable;

// Exercício 3 - Representação externa de dados da mensagem de REQUEST

public class Requisicao implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TipoOperacao operacao;
    private final Object[] parametros;

    public Requisicao(TipoOperacao operacao, Object... parametros) {
        this.operacao = operacao;
        this.parametros = parametros;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public Object[] getParametros() {
        return parametros;
    }
}
