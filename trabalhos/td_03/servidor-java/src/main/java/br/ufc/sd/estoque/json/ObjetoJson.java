package br.ufc.sd.estoque.json;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Acesso tipado e validado aos campos de um objeto JSON.
 *
 * <p>Todo campo ausente ou de tipo errado gera {@link IllegalArgumentException} com uma
 * mensagem que identifica o campo — o servidor transforma isso em erro
 * {@code ARGUMENTO_INVALIDO} na resposta.
 */
public final class ObjetoJson {

    private final Map<String, Object> campos;

    private ObjetoJson(Map<String, Object> campos) {
        this.campos = campos;
    }

    @SuppressWarnings("unchecked")
    public static ObjetoJson de(Object valor) {
        if (!(valor instanceof Map)) {
            throw new IllegalArgumentException("Esperava um objeto JSON.");
        }
        return new ObjetoJson((Map<String, Object>) valor);
    }

    public static ObjetoJson vazio() {
        return new ObjetoJson(Collections.emptyMap());
    }

    public Map<String, Object> mapa() {
        return Collections.unmodifiableMap(campos);
    }

    public boolean tem(String campo) {
        return campos.get(campo) != null;
    }

    public Object valor(String campo) {
        return campos.get(campo);
    }

    public String texto(String campo) {
        Object v = campos.get(campo);
        if (!(v instanceof String s)) throw invalido(campo, "texto");
        return s;
    }

    public String textoOpcional(String campo) {
        Object v = campos.get(campo);
        if (v == null) return null;
        if (!(v instanceof String s)) throw invalido(campo, "texto");
        return s;
    }

    public long longo(String campo) {
        Object v = campos.get(campo);
        if (!(v instanceof Long l)) throw invalido(campo, "inteiro");
        return l;
    }

    public int inteiro(String campo) {
        long l = longo(campo);
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Argumento '" + campo + "' fora do intervalo de int.");
        }
        return (int) l;
    }

    public double decimal(String campo) {
        Object v = campos.get(campo);
        if (v instanceof Long l) return l;
        if (v instanceof Double d) return d;
        throw invalido(campo, "número");
    }

    public boolean booleano(String campo) {
        Object v = campos.get(campo);
        if (!(v instanceof Boolean b)) throw invalido(campo, "booleano");
        return b;
    }

    public ObjetoJson objeto(String campo) {
        Object v = campos.get(campo);
        if (!(v instanceof Map)) throw invalido(campo, "objeto");
        return de(v);
    }

    @SuppressWarnings("unchecked")
    public List<Object> lista(String campo) {
        Object v = campos.get(campo);
        if (!(v instanceof List)) throw invalido(campo, "lista");
        return (List<Object>) v;
    }

    private static IllegalArgumentException invalido(String campo, String tipo) {
        return new IllegalArgumentException("Argumento '" + campo + "' ausente ou não é " + tipo + ".");
    }

    @Override
    public String toString() {
        return Json.escrever(campos);
    }
}
