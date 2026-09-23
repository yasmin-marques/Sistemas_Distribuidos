package br.ufc.quixada.sd.biblioteca;

import java.util.List;


// Operações do serviço remoto sobre o recurso Emprestimo.

public interface EmprestimoService {

    Emprestimo registrarEmprestimo(String isbnLivro, String matriculaAluno, int diasParaDevolucao);

    boolean registrarDevolucao(String isbnLivro, String matriculaAluno);

    List<Emprestimo> listarPorAluno(String matriculaAluno);

    List<Emprestimo> listarEmAberto();
}
