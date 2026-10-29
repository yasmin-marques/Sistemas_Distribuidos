package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.protocolo.ProtocoloCliente;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.util.List;

public class AssinaturaProxy extends ProxyBase implements AssinaturaRemota {

    public AssinaturaProxy(ProtocoloCliente protocolo, RemoteObjectRef referencia) {
        super(protocolo, referencia, AssinaturaRemota.class);
    }

    @Override
    public List<Notificacao> aguardar(long esperaMaximaMs) {
        return CodecEntidades.listaDeJson(invocar("aguardar", "esperaMaximaMs", esperaMaximaMs),
                CodecEntidades::notificacaoDeJson);
    }

    @Override
    public String usuario() {
        Object valor = invocar("usuario");
        if (!(valor instanceof String s)) throw new IllegalArgumentException("Resposta inválida para usuario().");
        return s;
    }
}
