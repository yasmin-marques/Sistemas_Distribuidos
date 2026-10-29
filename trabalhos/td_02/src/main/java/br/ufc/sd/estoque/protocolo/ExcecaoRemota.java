package br.ufc.sd.estoque.protocolo;

import java.util.Objects;

/** Erro devolvido pelo servidor numa mensagem de resposta e relançado no cliente. */
public class ExcecaoRemota extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CodigoErro codigo;

    public ExcecaoRemota(CodigoErro codigo, String mensagem) {
        super(mensagem);
        this.codigo = Objects.requireNonNull(codigo);
    }

    public CodigoErro getCodigo() {
        return codigo;
    }

    @Override
    public String toString() {
        return codigo + ": " + getMessage();
    }
}
