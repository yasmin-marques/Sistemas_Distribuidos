package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.ObjetoRemoto;
import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.protocolo.CargaResposta;
import br.ufc.sd.estoque.protocolo.ProtocoloCliente;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Proxy (stub do cliente) de um objeto remoto. Implementa a mesma interface do objeto real;
 * cada método empacota os argumentos em JSON, chama {@code doOperation} e desempacota o
 * resultado. Quem usa o proxy chama métodos normais, sem ver o protocolo.
 */
public abstract class ProxyBase implements ObjetoRemoto {

    protected final ProtocoloCliente protocolo;
    private final RemoteObjectRef referencia;

    protected ProxyBase(ProtocoloCliente protocolo, RemoteObjectRef referencia, Class<? extends ObjetoRemoto> tipo) {
        this.protocolo = Objects.requireNonNull(protocolo);
        this.referencia = Objects.requireNonNull(referencia);
        if (!tipo.getSimpleName().equals(referencia.interfaceRemota())) {
            throw new IllegalArgumentException("A referência " + referencia + " não é de " + tipo.getSimpleName() + ".");
        }
    }

    /** A referência remota que este proxy representa. */
    public RemoteObjectRef referencia() {
        return referencia;
    }

    /**
     * Invoca o método remoto.
     *
     * @param argumentos pares nome/valor, já na representação externa
     * @return o resultado (valor JSON)
     */
    protected Object invocar(String metodo, Object... argumentos) {
        if (argumentos.length % 2 != 0) throw new IllegalArgumentException("Argumentos devem vir em pares nome/valor.");
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < argumentos.length; i += 2) args.put((String) argumentos[i], argumentos[i + 1]);
        byte[] resposta = protocolo.doOperation(referencia, metodo, Json.escreverBytes(args));
        return CargaResposta.ler(resposta);
    }

    /** Converte um objeto remoto recebido como argumento na sua referência (passagem por referência). */
    protected static Map<String, Object> referenciaDe(ObjetoRemoto objeto) {
        if (!(objeto instanceof ProxyBase proxy)) {
            throw new IllegalArgumentException("Só é possível passar objetos remotos obtidos do servidor.");
        }
        return proxy.referencia.paraJson();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProxyBase p && p.getClass() == getClass() && p.referencia.equals(referencia);
    }

    @Override
    public int hashCode() {
        return referencia.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " -> " + referencia;
    }
}
