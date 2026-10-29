package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.AutenticacaoRemota;
import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.protocolo.PortaServidora;
import br.ufc.sd.estoque.protocolo.ProtocoloCliente;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;

/**
 * Ponto de entrada do cliente: conhece o endereço do registro RMI e os nomes dos objetos de
 * serviço, e fornece os proxies correspondentes.
 */
public final class ConexaoClinica {

    private final ProtocoloCliente protocolo = new ProtocoloCliente();
    private final String host;
    private final int porta;
    private final String servico;

    public ConexaoClinica(String host, int porta) {
        this(host, porta, PortaServidora.NOME_REGISTRO);
    }

    public ConexaoClinica(String host, int porta, String servico) {
        this.host = host;
        this.porta = porta;
        this.servico = servico;
    }

    public ProtocoloCliente protocolo() {
        return protocolo;
    }

    public RemoteObjectRef referencia(String objeto, Class<?> interfaceRemota) {
        return new RemoteObjectRef(host, porta, servico, objeto, interfaceRemota.getSimpleName());
    }

    public AutenticacaoRemota autenticacao() {
        return new AutenticacaoProxy(protocolo, referencia(AutenticacaoRemota.NOME, AutenticacaoRemota.class));
    }

    public EstoqueRemoto estoque() {
        return new EstoqueProxy(protocolo, referencia(EstoqueRemoto.NOME, EstoqueRemoto.class));
    }

    public NotificacoesRemotas notificacoes() {
        return new NotificacoesProxy(protocolo, referencia(NotificacoesRemotas.NOME, NotificacoesRemotas.class));
    }

    /** Proxy para um pedido cuja referência já se conhece (por exemplo, recebida de outro cliente). */
    public PedidoReposicaoRemoto pedido(RemoteObjectRef referencia) {
        return new PedidoReposicaoProxy(protocolo, referencia);
    }

    @Override
    public String toString() {
        return host + ":" + porta + "/" + servico;
    }
}
