package br.ufc.sd.estoque.json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura e escrita de JSON (RFC 8259) sem bibliotecas externas.
 *
 * <p>Tipos Java usados: {@code Map<String,Object>} (objeto), {@code List<Object>} (lista),
 * {@code String}, {@code Long} (inteiros), {@code Double} (decimais), {@code Boolean} e {@code null}.
 * O leitor limita a profundidade de aninhamento para que uma mensagem maliciosa não
 * estoure a pilha do servidor.
 */
public final class Json {

    /** Profundidade máxima de objetos/listas aninhados aceita pelo leitor. */
    public static final int PROFUNDIDADE_MAXIMA = 64;

    private Json() { }

    // ------------------------------------------------------------------ escrita

    public static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    public static byte[] escreverBytes(Object valor) {
        return escrever(valor).getBytes(StandardCharsets.UTF_8);
    }

    private static void escrever(Object valor, StringBuilder sb) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            escreverTexto(s, sb);
        } else if (valor instanceof Double || valor instanceof Float) {
            double d = ((Number) valor).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON não representa NaN nem infinito.");
            }
            sb.append(d);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escreverTexto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (valor instanceof Iterable<?> itens) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : itens) {
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
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ leitura

    public static Object ler(String json) {
        Leitor leitor = new Leitor(json);
        Object valor = leitor.valor(0);
        leitor.pular();
        if (!leitor.terminou()) throw leitor.erro("conteúdo após o fim do JSON");
        return valor;
    }

    public static Object lerBytes(byte[] dados) {
        if (dados == null) throw new IllegalArgumentException("JSON nulo.");
        return ler(new String(dados, StandardCharsets.UTF_8));
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

        Object valor(int profundidade) {
            if (profundidade > PROFUNDIDADE_MAXIMA) throw erro("aninhamento excessivo");
            pular();
            if (terminou()) throw erro("fim inesperado");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> objeto(profundidade);
                case '[' -> lista(profundidade);
                case '"' -> texto();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numero();
            };
        }

        private Object literal(String palavra, Object valor) {
            if (!s.startsWith(palavra, i)) throw erro("valor desconhecido");
            i += palavra.length();
            return valor;
        }

        private Map<String, Object> objeto(int profundidade) {
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
                if (mapa.containsKey(chave)) throw erro("campo repetido '" + chave + "'");
                mapa.put(chave, valor(profundidade + 1));
                pular();
                if (terminou()) throw erro("objeto não foi fechado");
                char c = s.charAt(i++);
                if (c == '}') return mapa;
                if (c != ',') throw erro("esperava ',' ou '}'");
            }
        }

        private List<Object> lista(int profundidade) {
            List<Object> itens = new ArrayList<>();
            i++;
            pular();
            if (!terminou() && s.charAt(i) == ']') { i++; return itens; }
            while (true) {
                itens.add(valor(profundidade + 1));
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
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"', '\\', '/' -> sb.append(e);
                    case 'u' -> {
                        if (i + 4 > s.length()) throw erro("escape \\u incompleto");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw erro("escape \\u inválido");
                        }
                        i += 4;
                    }
                    default -> throw erro("escape inválido '\\" + e + "'");
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
