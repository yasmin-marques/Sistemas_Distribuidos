package br.ufc.quixada.sd.votacao;

import java.io.Serializable;

public class RequisicaoVotacao implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TipoOperacaoVotacao operacao;
    private final Object[] parametros;

    public RequisicaoVotacao(TipoOperacaoVotacao operacao, Object... parametros) {
        this.operacao = operacao;
        this.parametros = parametros;
    }

    public TipoOperacaoVotacao getOperacao() {
        return operacao;
    }

    public Object[] getParametros() {
        return parametros;
    }
}
