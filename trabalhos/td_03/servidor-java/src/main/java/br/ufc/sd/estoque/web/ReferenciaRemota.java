package br.ufc.sd.estoque.web;

import br.ufc.sd.estoque.json.ObjetoJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Referência a um objeto distribuído (era a {@code RemoteObjectRef} do TD02).
 *
 * <p>Na API REST a referência é o próprio endereço do objeto: {@code href} é a URL onde ele
 * responde, {@code objeto} é o nome na tabela do servidor e {@code interfaceRemota} diz quais
 * operações ele oferece. Por ser só dados, a referência viaja dentro de resultados e argumentos
 * — é assim que objetos são passados <b>por referência</b>: o objeto não sai do servidor.
 */
public record ReferenciaRemota(String objeto, String interfaceRemota, String href) {

    public ReferenciaRemota {
        exigir(objeto, "objeto");
        exigir(interfaceRemota, "interface");
    }

    private static void exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Referência remota sem '" + campo + "'.");
        }
    }

    public Map<String, Object> paraJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("objeto", objeto);
        m.put("interface", interfaceRemota);
        m.put("href", href);
        return m;
    }

    /** O {@code href} é opcional na entrada: o servidor identifica o objeto pelo nome. */
    public static ReferenciaRemota deJson(Object valor) {
        ObjetoJson o = ObjetoJson.de(valor);
        return new ReferenciaRemota(o.texto("objeto"), o.texto("interface"), o.textoOpcional("href"));
    }

    @Override
    public String toString() {
        return objeto + " (" + interfaceRemota + ")" + (href == null ? "" : " @ " + href);
    }
}
