package br.ufc.sd.estoque.protocolo;

/** Valores do campo {@code messageType} (seção 5.2 do livro-texto: 0 = Request, 1 = Reply). */
public final class TipoMensagem {

    public static final int REQUEST = 0;
    public static final int REPLY = 1;

    private TipoMensagem() { }

    public static String nome(int tipo) {
        return switch (tipo) {
            case REQUEST -> "Request";
            case REPLY -> "Reply";
            default -> "Desconhecido(" + tipo + ")";
        };
    }
}
