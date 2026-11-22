package br.ufc.sd.consultas;

/** O broker não confirmou a mensagem. */
public class FalhaPublicacaoException extends Exception {

    private static final long serialVersionUID = 1L;

    public FalhaPublicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
