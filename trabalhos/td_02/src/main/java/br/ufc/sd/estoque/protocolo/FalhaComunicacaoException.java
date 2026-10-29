package br.ufc.sd.estoque.protocolo;

/** A requisição não pôde ser entregue ou a resposta não chegou (servidor fora do ar, RMI falhou etc.). */
public class FalhaComunicacaoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FalhaComunicacaoException(String mensagem) {
        super(mensagem);
    }

    public FalhaComunicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
