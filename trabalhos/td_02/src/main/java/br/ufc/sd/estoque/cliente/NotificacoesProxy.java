package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import br.ufc.sd.estoque.protocolo.ProtocoloCliente;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.util.List;

public class NotificacoesProxy extends ProxyBase implements NotificacoesRemotas {

    public NotificacoesProxy(ProtocoloCliente protocolo, RemoteObjectRef referencia) {
        super(protocolo, referencia, NotificacoesRemotas.class);
    }

    @Override
    public AssinaturaRemota entrarGrupo(String token) {
        return new AssinaturaProxy(protocolo, RemoteObjectRef.deJson(invocar("entrarGrupo", "token", token)));
    }

    @Override
    public void sairGrupo(AssinaturaRemota assinatura) {
        invocar("sairGrupo", "assinatura", referenciaDe(assinatura));
    }

    @Override
    public Notificacao publicar(String token, TipoNotificacao tipo, String mensagem) {
        if (tipo == null) throw new IllegalArgumentException("Tipo de notificação ausente.");
        return CodecEntidades.notificacaoDeJson(invocar("publicar", "token", token, "tipo", tipo.name(),
                "mensagem", mensagem));
    }

    @Override
    public List<String> membros() {
        return CodecEntidades.listaDeJson(invocar("membros"), valor -> {
            if (!(valor instanceof String s)) throw new IllegalArgumentException("Membro inválido na resposta.");
            return s;
        });
    }
}
