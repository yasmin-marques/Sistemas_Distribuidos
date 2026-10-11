package clinica.stream;

import clinica.modelo.Medicamento;
import java.io.StreamCorruptedException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

// Converte um Medicamento em uma linha "id;nome;fabricante;quantidade;preco;validade"
final class CodecMedicamento {

    private CodecMedicamento() { }

    static String codificar(Medicamento m) {
        return m.getId() + ";"
                + escapar(m.getNome()) + ";"
                + escapar(m.getFabricante()) + ";"
                + m.getQuantidade() + ";"
                + m.getPreco() + ";"
                + (m.getValidade() == null ? "" : m.getValidade().toString());
    }

    static Medicamento decodificar(String linha) throws StreamCorruptedException {
        List<String> campos = dividir(linha);
        if (campos.size() != 6) {
            throw new StreamCorruptedException(
                    "Esperava 6 campos por medicamento, mas a linha tem " + campos.size() + ": " + linha);
        }
        try {
            Medicamento m = new Medicamento();
            m.setId(Integer.parseInt(campos.get(0)));
            m.setNome(campos.get(1));
            m.setFabricante(campos.get(2));
            m.setQuantidade(Integer.parseInt(campos.get(3)));
            m.setPreco(Double.parseDouble(campos.get(4)));
            m.setValidade(campos.get(5).isEmpty() ? null : LocalDate.parse(campos.get(5)));
            return m;
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new StreamCorruptedException("Campo inválido na linha '" + linha + "': " + e.getMessage());
        }
    }

    private static String escapar(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case ';':  sb.append("\\;");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    // Divide a linha em campos respeitando os escapes.
    private static List<String> dividir(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '\\' && i + 1 < linha.length()) {
                char prox = linha.charAt(++i);
                switch (prox) {
                    case 'n': atual.append('\n'); break;
                    case 'r': atual.append('\r'); break;
                    default:  atual.append(prox);   // \\ e \;
                }
            } else if (c == ';') {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos;
    }
}
