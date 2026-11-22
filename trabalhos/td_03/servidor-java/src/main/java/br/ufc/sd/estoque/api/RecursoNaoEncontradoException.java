package br.ufc.sd.estoque.api;

/** O recurso pedido (item, objeto remoto...) não existe. Na API REST vira HTTP 404. */
public class RecursoNaoEncontradoException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
