package clinica.votacao;

import clinica.multicast.Grupo;
import clinica.util.Json;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Resposta do servidor de votação, já convertida do JSON recebido
public class RespostaVotacao {

    private final Map<String, Object> campos;

    RespostaVotacao(Map<String, Object> campos) {
        this.campos = campos;
    }

    public boolean isSucesso() { return Json.booleano(campos, "sucesso"); }

    public String getMensagem() { return Json.texto(campos, "mensagem"); }

    public String getPapel() { return Json.texto(campos, "papel"); }

    // Segundos que faltam para o fim da votação (0 se já encerrou, -1 se a resposta não informa)
    public long getRestante() { return campos.containsKey("restante") ? Json.inteiro(campos, "restante") : -1; }

    /** @return o grupo multicast informado no login, ou {@code null}. */
    public Grupo getGrupo() {
        if (Json.texto(campos, "grupo") == null) return null;
        return new Grupo(Json.texto(campos, "grupo"), (int) Json.inteiro(campos, "porta"));
    }

    /** @return a lista de candidatos enviada, ou {@code null} se a resposta não trouxe uma. */
    @SuppressWarnings("unchecked")
    public List<Candidato> getCandidatos() {
        if (!campos.containsKey("candidatos")) return null;
        List<Candidato> lista = new ArrayList<>();
        for (Object o : Json.lista(campos, "candidatos")) lista.add(Candidato.fromMapa((Map<String, Object>) o));
        return lista;
    }

    /** @return o resultado final, ou {@code null} se a votação ainda está aberta. */
    public Resultado getResultado() {
        Map<String, Object> mapa = Json.objeto(campos, "resultado");
        return mapa == null ? null : Resultado.fromMapa(mapa);
    }

    public void imprimir() {
        System.out.println((isSucesso() ? "[OK] " : "[FALHA] ") + getMensagem());
        List<Candidato> candidatos = getCandidatos();
        if (candidatos != null) {
            System.out.println("Candidatos:");
            for (Candidato c : candidatos) System.out.println("  " + c);
        }
        Resultado resultado = getResultado();
        if (resultado != null) System.out.println("Resultado final:\n" + resultado);
    }
}
