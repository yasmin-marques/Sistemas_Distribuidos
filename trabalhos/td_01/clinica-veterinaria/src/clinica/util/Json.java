package clinica.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Leitura e escrita de JSON (objetos, listas, textos, números, booleanos e null)
public final class Json {

    private Json() { }

    public static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    private static void escrever(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String) {
            escreverTexto((String) valor, sb);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) valor).entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escreverTexto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof Iterable) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : (Iterable<?>) valor) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(item, sb);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("Tipo não suportado em JSON: " + valor.getClass().getName());
        }
    }

    private static void escreverTexto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    public static Object ler(String json) {
        Leitor leitor = new Leitor(json);
        Object valor = leitor.valor();
        leitor.pular();
        if (!leitor.terminou()) throw leitor.erro("conteúdo após o fim do JSON");
        return valor;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> lerObjeto(String json) {
        Object valor = ler(json);
        if (!(valor instanceof Map)) throw new IllegalArgumentException("O JSON recebido não é um objeto.");
        return (Map<String, Object>) valor;
    }

    public static String texto(Map<String, Object> obj, String campo) {
        Object v = obj.get(campo);
        if (v != null && !(v instanceof String)) throw new IllegalArgumentException("Campo '" + campo + "' deveria ser texto.");
        return (String) v;
    }

    public static long inteiro(Map<String, Object> obj, String campo) {
        Object v = obj.get(campo);
        if (!(v instanceof Long)) throw new IllegalArgumentException("Campo '" + campo + "' deveria ser inteiro.");
        return (Long) v;
    }

    public static boolean booleano(Map<String, Object> obj, String campo) {
        return Boolean.TRUE.equals(obj.get(campo));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> lista(Map<String, Object> obj, String campo) {
        Object v = obj.get(campo);
        if (v == null) return new ArrayList<>();
        if (!(v instanceof List)) throw new IllegalArgumentException("Campo '" + campo + "' deveria ser uma lista.");
        return (List<Object>) v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objeto(Map<String, Object> obj, String campo) {
        Object v = obj.get(campo);
        if (v != null && !(v instanceof Map)) throw new IllegalArgumentException("Campo '" + campo + "' deveria ser um objeto.");
        return (Map<String, Object>) v;
    }

    private static final class Leitor {
        private final String s;
        private int i = 0;

        Leitor(String s) {
            if (s == null) throw new IllegalArgumentException("JSON nulo.");
            this.s = s;
        }

        boolean terminou() { return i >= s.length(); }

        IllegalArgumentException erro(String motivo) {
            return new IllegalArgumentException("JSON inválido (posição " + i + "): " + motivo);
        }

        void pular() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        Object valor() {
            pular();
            if (terminou()) throw erro("fim inesperado");
            char c = s.charAt(i);
            switch (c) {
                case '{': return objeto();
                case '[': return lista();
                case '"': return texto();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default:  return numero();
            }
        }

        private Object literal(String palavra, Object valor) {
            if (!s.startsWith(palavra, i)) throw erro("valor desconhecido");
            i += palavra.length();
            return valor;
        }

        private Map<String, Object> objeto() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            i++;
            pular();
            if (!terminou() && s.charAt(i) == '}') { i++; return mapa; }
            while (true) {
                pular();
                if (terminou() || s.charAt(i) != '"') throw erro("esperava o nome de um campo");
                String chave = texto();
                pular();
                if (terminou() || s.charAt(i) != ':') throw erro("esperava ':'");
                i++;
                mapa.put(chave, valor());
                pular();
                if (terminou()) throw erro("objeto não foi fechado");
                char c = s.charAt(i++);
                if (c == '}') return mapa;
                if (c != ',') throw erro("esperava ',' ou '}'");
            }
        }

        private List<Object> lista() {
            List<Object> itens = new ArrayList<>();
            i++;
            pular();
            if (!terminou() && s.charAt(i) == ']') { i++; return itens; }
            while (true) {
                itens.add(valor());
                pular();
                if (terminou()) throw erro("lista não foi fechada");
                char c = s.charAt(i++);
                if (c == ']') return itens;
                if (c != ',') throw erro("esperava ',' ou ']'");
            }
        }

        private String texto() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (!terminou()) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                if (terminou()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw erro("escape \\u incompleto");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw erro("escape \\u inválido");
                        }
                        i += 4;
                        break;
                    default: sb.append(e);   // \" \\ \/
                }
            }
            throw erro("texto não foi fechado");
        }

        private Object numero() {
            int inicio = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (inicio == i) throw erro("valor inesperado '" + s.charAt(i) + "'");
            String t = s.substring(inicio, i);
            try {
                if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) return Long.parseLong(t);
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                throw erro("número inválido '" + t + "'");
            }
        }
    }
}
