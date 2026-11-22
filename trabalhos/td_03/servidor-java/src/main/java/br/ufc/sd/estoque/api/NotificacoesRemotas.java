package br.ufc.sd.estoque.api;

import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import java.util.List;

/**
 * Grupo de notificações da clínica: substitui o grupo multicast 230.0.0.1 do TD01.
 * Objeto remoto de nome fixo {@value #NOME}.
 */
public interface NotificacoesRemotas extends ObjetoRemoto {

    String NOME = "Notificacoes";

    /**
     * Equivalente ao {@code joinGroup}: cria no servidor uma assinatura (fila de mensagens
     * deste membro) e devolve uma <b>referência</b> a ela.
     */
    AssinaturaRemota entrarGrupo(String token);

    /**
     * Equivalente ao {@code leaveGroup}. Recebe a referência da assinatura como argumento
     * (passagem por referência no sentido cliente → servidor).
     */
    void sairGrupo(AssinaturaRemota assinatura);

    /** Pede ao servidor que envie uma mensagem a todos os membros do grupo. */
    Notificacao publicar(String token, TipoNotificacao tipo, String mensagem);

    /** Usuários com assinatura ativa. */
    List<String> membros();
}
