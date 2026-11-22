package br.ufc.sd.estoque.api;

/** Credenciais incorretas ou token de sessão ausente/inválido. */
public class NaoAutenticadoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
