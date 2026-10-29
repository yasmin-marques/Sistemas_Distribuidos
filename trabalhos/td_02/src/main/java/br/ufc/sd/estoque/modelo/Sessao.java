package br.ufc.sd.estoque.modelo;

import java.util.Objects;

/** Sessão aberta após a autenticação; o token acompanha as operações que alteram o estoque. */
public class Sessao {

    private final String token;
    private final String usuario;
    private final long inicio;

    public Sessao(String token, String usuario, long inicio) {
        this.token = Objects.requireNonNull(token, "token");
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.inicio = inicio;
    }

    public String getToken() { return token; }
    public String getUsuario() { return usuario; }
    public long getInicio() { return inicio; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Sessao s)) return false;
        return inicio == s.inicio && token.equals(s.token) && usuario.equals(s.usuario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, usuario, inicio);
    }

    @Override
    public String toString() {
        return "Sessão de " + usuario;
    }
}
