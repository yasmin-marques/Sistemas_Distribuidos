package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.AutenticacaoRemota;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.modelo.Sessao;
import br.ufc.sd.estoque.protocolo.ProtocoloCliente;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;

public class AutenticacaoProxy extends ProxyBase implements AutenticacaoRemota {

    public AutenticacaoProxy(ProtocoloCliente protocolo, RemoteObjectRef referencia) {
        super(protocolo, referencia, AutenticacaoRemota.class);
    }

    @Override
    public Sessao autenticar(String usuario, String senha) {
        return CodecEntidades.sessaoDeJson(invocar("autenticar", "usuario", usuario, "senha", senha));
    }

    @Override
    public void encerrarSessao(String token) {
        invocar("encerrarSessao", "token", token);
    }
}
