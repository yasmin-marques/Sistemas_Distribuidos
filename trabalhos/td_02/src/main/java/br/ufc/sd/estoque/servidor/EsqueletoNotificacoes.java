package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.modelo.TipoNotificacao;

/** Esqueleto de {@link NotificacoesRemotas}. */
public class EsqueletoNotificacoes extends EsqueletoBase<NotificacoesRemotas> {

    public EsqueletoNotificacoes(NotificacoesRemotas servo, TabelaObjetosRemotos tabela) {
        super(NotificacoesRemotas.class, servo, tabela);
    }

    @Override
    public Object invocar(String metodo, ObjetoJson a) {
        return switch (metodo) {
            // joinGroup: devolve a referência da assinatura criada no servidor
            case "entrarGrupo" -> porReferencia(servo.entrarGrupo(a.texto("token")));
            // leaveGroup: recebe a referência como argumento e a resolve para o objeto local
            case "sairGrupo" -> {
                servo.sairGrupo(resolver(a, "assinatura", AssinaturaRemota.class));
                yield null;
            }
            case "publicar" -> CodecEntidades.notificacaoParaJson(servo.publicar(a.texto("token"),
                    CodecEntidades.enumeracao(TipoNotificacao.class, a.texto("tipo")), a.texto("mensagem")));
            case "membros" -> servo.membros();
            default -> throw metodoInexistente(metodo);
        };
    }
}
