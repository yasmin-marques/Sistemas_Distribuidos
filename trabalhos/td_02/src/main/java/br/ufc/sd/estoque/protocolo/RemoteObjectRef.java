package br.ufc.sd.estoque.protocolo;

import br.ufc.sd.estoque.json.ObjetoJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Referência a um objeto remoto (campo {@code objectReference} da mensagem).
 *
 * <p>Equivale à referência remota do livro (endereço + porta + identificador do objeto +
 * interface): {@code host}/{@code porta} localizam o registro RMI, {@code servico} é o
 * nome sob o qual a porta servidora foi registrada, {@code objeto} identifica o objeto na
 * tabela de objetos remotos do servidor e {@code interfaceRemota} diz quais métodos ele
 * oferece. Como o enunciado permite, objeto e interface são Strings.
 *
 * <p>Por ser só dados, a referência pode viajar dentro de argumentos e resultados: é assim
 * que objetos remotos são passados por referência.
 */
public record RemoteObjectRef(String host, int porta, String servico, String objeto, String interfaceRemota) {

    public RemoteObjectRef {
        exigirTexto(host, "host");
        exigirTexto(servico, "servico");
        exigirTexto(objeto, "objeto");
        exigirTexto(interfaceRemota, "interfaceRemota");
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("Porta inválida na referência remota: " + porta);
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Referência remota sem '" + campo + "'.");
        }
    }

    /** Endereço da porta servidora onde o objeto vive: {@code host:porta/servico}. */
    public String endereco() {
        return host + ":" + porta + "/" + servico;
    }

    /** Referência para outro objeto que vive na mesma porta servidora. */
    public RemoteObjectRef paraObjeto(String outroObjeto, String outraInterface) {
        return new RemoteObjectRef(host, porta, servico, outroObjeto, outraInterface);
    }

    public Map<String, Object> paraJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("host", host);
        m.put("porta", porta);
        m.put("servico", servico);
        m.put("objeto", objeto);
        m.put("interface", interfaceRemota);
        return m;
    }

    public static RemoteObjectRef deJson(Object valor) {
        ObjetoJson o = ObjetoJson.de(valor);
        return new RemoteObjectRef(o.texto("host"), o.inteiro("porta"), o.texto("servico"),
                o.texto("objeto"), o.texto("interface"));
    }

    @Override
    public String toString() {
        return objeto + " (" + interfaceRemota + ") @ " + endereco();
    }
}
