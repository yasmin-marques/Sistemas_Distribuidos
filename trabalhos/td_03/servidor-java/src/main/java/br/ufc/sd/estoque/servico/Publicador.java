package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;

/** Quem envia mensagens ao grupo de notificações. Desacopla os serviços de estoque da central. */
public interface Publicador {

    Notificacao publicar(TipoNotificacao tipo, String mensagem);
}
