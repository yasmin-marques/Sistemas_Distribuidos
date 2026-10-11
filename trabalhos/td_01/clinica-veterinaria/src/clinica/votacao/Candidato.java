package clinica.votacao;

import clinica.util.Json;
import java.util.LinkedHashMap;
import java.util.Map;

// Candidato da votação (na demonstração, os pets concorrem ao "Pet do Mês")
public class Candidato {

    private final int id;
    private final String nome;
    private final String descricao;

    public Candidato(int id, String nome, String descricao) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
    }

    public int getId() { return id; }
    public String getNome() { return nome; }
    public String getDescricao() { return descricao; }

    public Map<String, Object> toMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", id);
        mapa.put("nome", nome);
        mapa.put("descricao", descricao);
        return mapa;
    }

    public static Candidato fromMapa(Map<String, Object> mapa) {
        return new Candidato((int) Json.inteiro(mapa, "id"), Json.texto(mapa, "nome"), Json.texto(mapa, "descricao"));
    }

    @Override
    public String toString() {
        return id + " - " + nome + (descricao == null || descricao.isEmpty() ? "" : " (" + descricao + ")");
    }
}
