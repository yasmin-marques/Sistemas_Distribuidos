package br.ufc.sd.estoque.web;

import java.util.Objects;

/** Erro que o servidor devolve ao cliente com o status HTTP do {@link CodigoErro}. */
public class ErroApi extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CodigoErro codigo;

    public ErroApi(CodigoErro codigo, String mensagem) {
        super(mensagem);
        this.codigo = Objects.requireNonNull(codigo);
    }

    public CodigoErro getCodigo() {
        return codigo;
    }

    public int status() {
        return codigo.status();
    }

    @Override
    public String toString() {
        return codigo + ": " + getMessage();
    }
}
