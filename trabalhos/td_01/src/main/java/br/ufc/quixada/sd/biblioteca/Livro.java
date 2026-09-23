package br.ufc.quixada.sd.biblioteca;

import java.io.Serializable;

// POJO que representa um livro do acervo da biblioteca

public class Livro implements Serializable {

    private static final long serialVersionUID = 1L;

    private String isbn;
    private String titulo;
    private String autor;
    private int anoPublicacao;
    private boolean disponivel;

    public Livro() {
    }

    public Livro(String isbn, String titulo, String autor, int anoPublicacao, boolean disponivel) {
        this.isbn = isbn;
        this.titulo = titulo;
        this.autor = autor;
        this.anoPublicacao = anoPublicacao;
        this.disponivel = disponivel;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getAutor() {
        return autor;
    }

    public void setAutor(String autor) {
        this.autor = autor;
    }

    public int getAnoPublicacao() {
        return anoPublicacao;
    }

    public void setAnoPublicacao(int anoPublicacao) {
        this.anoPublicacao = anoPublicacao;
    }

    public boolean isDisponivel() {
        return disponivel;
    }

    public void setDisponivel(boolean disponivel) {
        this.disponivel = disponivel;
    }

    @Override
    public String toString() {
        return "Livro{isbn='" + isbn + "', titulo='" + titulo + "', autor='" + autor
                + "', ano=" + anoPublicacao + ", disponivel=" + disponivel + "}";
    }
}
