package clinica.votacao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Regras da votação, sem nenhuma parte de rede
public class Urna {

    private final Map<Integer, Candidato> candidatos = new LinkedHashMap<>();
    private final Map<String, Integer> votos = new HashMap<>();   // eleitor -> candidato escolhido
    private int proximoId = 1;
    private boolean aberta = true;
    private Resultado resultadoFinal;

    public synchronized Candidato adicionar(String nome, String descricao) {
        exigirAberta();
        if (nome == null || nome.isBlank()) throw new IllegalArgumentException("O candidato precisa de um nome.");
        for (Candidato c : candidatos.values()) {
            if (c.getNome().equalsIgnoreCase(nome.trim())) {
                throw new IllegalArgumentException("Já existe um candidato chamado '" + c.getNome() + "'.");
            }
        }
        Candidato novo = new Candidato(proximoId++, nome.trim(), descricao == null ? "" : descricao.trim());
        candidatos.put(novo.getId(), novo);
        return novo;
    }

    // Remove o candidato e libera os eleitores que tinham votado nele, para que possam votar de novo
    public synchronized int remover(int id) {
        exigirAberta();
        if (candidatos.remove(id) == null) throw new IllegalArgumentException("Candidato " + id + " não encontrado.");
        int antes = votos.size();
        votos.values().removeIf(escolhido -> escolhido == id);
        return antes - votos.size();
    }

    public synchronized List<Candidato> listar() {
        return new ArrayList<>(candidatos.values());
    }

    public synchronized Candidato votar(String eleitor, int idCandidato) {
        exigirAberta();
        Candidato escolhido = candidatos.get(idCandidato);
        if (escolhido == null) throw new IllegalArgumentException("Candidato " + idCandidato + " não encontrado.");
        if (votos.containsKey(eleitor)) throw new IllegalStateException("Você já votou.");
        votos.put(eleitor, idCandidato);
        return escolhido;
    }

    public synchronized boolean jaVotou(String eleitor) {
        return votos.containsKey(eleitor);
    }

    // Fecha a votação e calcula o resultado. Chamadas seguintes devolvem o mesmo resultado
    public synchronized Resultado encerrar() {
        if (aberta) {
            aberta = false;
            Map<Integer, Integer> contagem = new HashMap<>();
            for (int escolhido : votos.values()) contagem.merge(escolhido, 1, Integer::sum);
            resultadoFinal = Resultado.calcular(new ArrayList<>(candidatos.values()), contagem);
        }
        return resultadoFinal;
    }

    public synchronized boolean estaAberta() {
        return aberta;
    }

    /** @return o resultado final, ou {@code null} enquanto a votação estiver aberta. */
    public synchronized Resultado getResultadoFinal() {
        return resultadoFinal;
    }

    private void exigirAberta() {
        if (!aberta) throw new IllegalStateException("A votação já foi encerrada.");
    }
}
