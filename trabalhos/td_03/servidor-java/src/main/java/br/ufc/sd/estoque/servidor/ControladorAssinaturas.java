package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.web.Requisicao;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import br.ufc.sd.estoque.web.TabelaObjetosDistribuidos;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Objetos distribuídos {@code Assinatura-xxxxxxxx}: a participação de um membro no grupo,
 * acessada <b>pela referência</b> recebida em {@code entrarGrupo}.
 * <pre>
 *   GET    /api/notificacoes/assinaturas/{objeto}                      usuario()
 *   GET    /api/notificacoes/assinaturas/{objeto}/mensagens?espera=ms  aguardar(ms)   (long polling)
 *   DELETE /api/notificacoes/assinaturas/{objeto}                      sairGrupo(assinatura)
 * </pre>
 * Se não há mensagem, a resposta fica retida no servidor até chegar uma ou até {@code espera}
 * milissegundos: o cliente recebe em tempo real sem precisar abrir porta (funciona atrás de NAT).
 */
final class ControladorAssinaturas extends ControladorBase {

    static final String CAMINHO = ControladorNotificacoes.CAMINHO + "/assinaturas";

    private final NotificacoesRemotas grupo;

    ControladorAssinaturas(NotificacoesRemotas grupo, TabelaObjetosDistribuidos tabela) {
        super(tabela);
        this.grupo = grupo;
    }

    @Override
    void registrar(Roteador r) {
        r.get(CAMINHO + "/{objeto}", q -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usuario", assinatura(q).usuario());
            return Resposta.ok(m);
        });
        r.get(CAMINHO + "/{objeto}/mensagens", q -> {
            long espera = q.consultaLonga("espera", 0);
            return Resposta.ok(CodecEntidades.listaParaJson(assinatura(q).aguardar(espera),
                    CodecEntidades::notificacaoParaJson));
        });
        r.delete(CAMINHO + "/{objeto}", q -> {
            grupo.sairGrupo(assinatura(q));
            return Resposta.semConteudo();
        });
    }

    private AssinaturaRemota assinatura(Requisicao q) {
        return tabela.localizar(q.parametro("objeto"), AssinaturaRemota.class);
    }
}
