package br.ufc.quixada.sd.biblioteca;

import java.io.Serializable;
import java.time.LocalDate;


// POJO que representa o empréstimo de um livro para um aluno.

public class Emprestimo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String isbnLivro;
    private String matriculaAluno;
    private LocalDate dataEmprestimo;
    private LocalDate dataDevolucaoPrevista;
    private LocalDate dataDevolucaoEfetiva;

    public Emprestimo() {
    }

    public Emprestimo(String isbnLivro, String matriculaAluno, LocalDate dataEmprestimo,
                       LocalDate dataDevolucaoPrevista) {
        this.isbnLivro = isbnLivro;
        this.matriculaAluno = matriculaAluno;
        this.dataEmprestimo = dataEmprestimo;
        this.dataDevolucaoPrevista = dataDevolucaoPrevista;
        this.dataDevolucaoEfetiva = null;
    }

    public String getIsbnLivro() {
        return isbnLivro;
    }

    public void setIsbnLivro(String isbnLivro) {
        this.isbnLivro = isbnLivro;
    }

    public String getMatriculaAluno() {
        return matriculaAluno;
    }

    public void setMatriculaAluno(String matriculaAluno) {
        this.matriculaAluno = matriculaAluno;
    }

    public LocalDate getDataEmprestimo() {
        return dataEmprestimo;
    }

    public void setDataEmprestimo(LocalDate dataEmprestimo) {
        this.dataEmprestimo = dataEmprestimo;
    }

    public LocalDate getDataDevolucaoPrevista() {
        return dataDevolucaoPrevista;
    }

    public void setDataDevolucaoPrevista(LocalDate dataDevolucaoPrevista) {
        this.dataDevolucaoPrevista = dataDevolucaoPrevista;
    }

    public LocalDate getDataDevolucaoEfetiva() {
        return dataDevolucaoEfetiva;
    }

    public void setDataDevolucaoEfetiva(LocalDate dataDevolucaoEfetiva) {
        this.dataDevolucaoEfetiva = dataDevolucaoEfetiva;
    }

    public boolean isDevolvido() {
        return dataDevolucaoEfetiva != null;
    }

    public boolean estaAtrasado() {
        if (isDevolvido()) {
            return dataDevolucaoEfetiva.isAfter(dataDevolucaoPrevista);
        }
        return LocalDate.now().isAfter(dataDevolucaoPrevista);
    }

    @Override
    public String toString() {
        return "Emprestimo{isbn='" + isbnLivro + "', matricula='" + matriculaAluno
                + "', emprestado=" + dataEmprestimo + ", previsto=" + dataDevolucaoPrevista
                + ", devolvido=" + dataDevolucaoEfetiva + "}";
    }
}
