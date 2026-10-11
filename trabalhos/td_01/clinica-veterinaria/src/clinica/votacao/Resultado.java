package clinica.votacao;

import clinica.util.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Apuração: votos e percentual de cada candidato, total e vencedor(es)
public class Resultado {

    public static class Linha {
        private final String candidato;
        private final int votos;
        private final double percentual;

        Linha(String candidato, int votos, double percentual) {
            this.candidato = candidato;
            this.votos = votos;
            this.percentual = percentual;
        }

        public String getCandidato() { return candidato; }
        public int getVotos() { return votos; }
        public double getPercentual() { return percentual; }
    }

    private final List<Linha> linhas;
    private final int total;
    private final List<String> vencedores;

    private Resultado(List<Linha> linhas, int total, List<String> vencedores) {
        this.linhas = linhas;
        this.total = total;
        this.vencedores = vencedores;
    }

    static Resultado calcular(List<Candidato> candidatos, Map<Integer, Integer> votosPorCandidato) {
        int total = 0;
        int maior = 0;
        for (Candidato c : candidatos) {
            int v = votosPorCandidato.getOrDefault(c.getId(), 0);
            total += v;
            maior = Math.max(maior, v);
        }
        List<Linha> linhas = new ArrayList<>();
        List<String> vencedores = new ArrayList<>();
        for (Candidato c : candidatos) {
            int v = votosPorCandidato.getOrDefault(c.getId(), 0);
            linhas.add(new Linha(c.getNome(), v, total == 0 ? 0.0 : v * 100.0 / total));
            if (total > 0 && v == maior) vencedores.add(c.getNome());
        }
        return new Resultado(linhas, total, vencedores);
    }

    public List<Linha> getLinhas() { return linhas; }
    public int getTotal() { return total; }
    public List<String> getVencedores() { return vencedores; }

    public Map<String, Object> toMapa() {
        List<Object> itens = new ArrayList<>();
        for (Linha l : linhas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("candidato", l.candidato);
            item.put("votos", l.votos);
            item.put("percentual", l.percentual);
            itens.add(item);
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("linhas", itens);
        mapa.put("total", total);
        mapa.put("vencedores", vencedores);
        return mapa;
    }

    @SuppressWarnings("unchecked")
    public static Resultado fromMapa(Map<String, Object> mapa) {
        List<Linha> linhas = new ArrayList<>();
        for (Object o : Json.lista(mapa, "linhas")) {
            Map<String, Object> item = (Map<String, Object>) o;
            Object p = item.get("percentual");
            linhas.add(new Linha(Json.texto(item, "candidato"), (int) Json.inteiro(item, "votos"),
                    p instanceof Number ? ((Number) p).doubleValue() : 0.0));
        }
        List<String> vencedores = new ArrayList<>();
        for (Object o : Json.lista(mapa, "vencedores")) vencedores.add((String) o);
        return new Resultado(linhas, (int) Json.inteiro(mapa, "total"), vencedores);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Linha l : linhas) {
            sb.append(String.format(Locale.ROOT, "  %s: %d voto(s) (%.1f%%)%n", l.candidato, l.votos, l.percentual));
        }
        sb.append("  Total: ").append(total).append(" voto(s)\n");
        if (vencedores.isEmpty()) {
            sb.append("  Nenhum voto registrado.");
        } else if (vencedores.size() == 1) {
            sb.append("  Vencedor: ").append(vencedores.get(0));
        } else {
            sb.append("  Empate entre: ").append(String.join(", ", vencedores));
        }
        return sb.toString();
    }
}
