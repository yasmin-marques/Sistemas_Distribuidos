package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import br.ufc.sd.estoque.web.ReferenciaRemota;
import br.ufc.sd.estoque.web.Requisicao;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import br.ufc.sd.estoque.web.TabelaObjetosDistribuidos;

/**
 * Objeto distribuído {@code Notificacoes} (o grupo, que no TD01 era o multicast).
 * <pre>
 *   GET    /api/notificacoes   descrição do objeto (nome, interface e operações)
 *   POST /api/notificacoes/assinaturas   entrarGrupo(token)      joinGroup  -> REFERÊNCIA
 *   POST /api/notificacoes/saida         sairGrupo(assinatura)   leaveGroup (recebe a REFERÊNCIA como argumento)
 *   POST /api/notificacoes/mensagens     publicar(token, tipo, mensagem)
 *   GET  /api/notificacoes/membros       membros()
 * </pre>
 */
final class ControladorNotificacoes extends ControladorBase {

    static final String CAMINHO = "/api/notificacoes";

    private final NotificacoesRemotas servo;

    ControladorNotificacoes(NotificacoesRemotas servo, TabelaObjetosDistribuidos tabela) {
        super(tabela);
        this.servo = servo;
    }

    @Override
    void registrar(Roteador r) {
        descrever(r, NotificacoesRemotas.NOME, NotificacoesRemotas.class, CAMINHO);
        r.post(CAMINHO + "/assinaturas", q -> porReferencia(q, servo.entrarGrupo(q.token())));
        r.post(CAMINHO + "/saida", this::sair);
        r.post(CAMINHO + "/mensagens", this::publicar);
        r.get(CAMINHO + "/membros", q -> Resposta.ok(servo.membros()));
    }

    /** A referência chega no corpo; o servidor a converte de volta no objeto real. */
    private Resposta sair(Requisicao q) {
        ReferenciaRemota ref = ReferenciaRemota.deJson(q.corpo().objeto("assinatura").mapa());
        servo.sairGrupo(tabela.resolver(ref, AssinaturaRemota.class));
        return Resposta.semConteudo();
    }

    private Resposta publicar(Requisicao q) {
        ObjetoJson a = q.corpo();
        var n = servo.publicar(q.token(), CodecEntidades.enumeracao(TipoNotificacao.class, a.texto("tipo")),
                a.texto("mensagem"));
        return Resposta.criado(null, CodecEntidades.notificacaoParaJson(n));
    }
}
