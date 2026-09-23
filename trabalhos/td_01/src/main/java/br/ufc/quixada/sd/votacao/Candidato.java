package br.ufc.quixada.sd.votacao;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

// POJO que representa um candidato na votação

public class Candidato implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String nome;
    private final AtomicInteger votos = new AtomicInteger(0);

    public Candidato(int id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    public int getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public int getVotos() {
        return votos.get();
    }

    public void votar() {
        votos.incrementAndGet();
    }

    @Override
    public String toString() {
        return id + " - " + nome + " (" + votos.get() + " voto(s))";
    }
}
