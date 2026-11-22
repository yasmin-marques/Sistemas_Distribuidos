package br.ufc.sd.estoque.api;

import br.ufc.sd.estoque.modelo.Notificacao;
import java.util.List;

/**
 * Participação de um cliente no grupo de notificações. Vive no servidor e acumula as
 * mensagens destinadas àquele membro; o cliente a acessa <b>por referência</b>.
 */
public interface AssinaturaRemota extends ObjetoRemoto {

    /** Espera máxima aceita por {@link #aguardar(long)}. */
    long ESPERA_MAXIMA_MS = 30_000;

    /**
     * Devolve as notificações pendentes. Se não houver nenhuma, a resposta fica retida no
     * servidor até chegar uma ou até passar {@code esperaMaximaMs} ("long polling"),
     * o que dá entrega em tempo real sem que o servidor precise chamar o cliente.
     */
    List<Notificacao> aguardar(long esperaMaximaMs);

    /** Usuário dono da assinatura. */
    String usuario();
}
