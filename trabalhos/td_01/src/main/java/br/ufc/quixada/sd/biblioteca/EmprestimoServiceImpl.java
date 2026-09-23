package br.ufc.quixada.sd.biblioteca;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


// Implementação do serviço de empréstimos. Depende do LivroService para
// checar/atualizar a disponibilidade do livro emprestado.

public class EmprestimoServiceImpl implements EmprestimoService {

    private final List<Emprestimo> emprestimos = new CopyOnWriteArrayList<>();
    private final LivroService livroService;

    public EmprestimoServiceImpl(LivroService livroService) {
        this.livroService = livroService;
    }

    @Override
    public synchronized Emprestimo registrarEmprestimo(String isbnLivro, String matriculaAluno,
                                                         int diasParaDevolucao) {
        Livro livro = livroService.buscarPorIsbn(isbnLivro);
        if (livro == null || !livro.isDisponivel()) {
            return null;
        }
        livroService.atualizarDisponibilidade(isbnLivro, false);
        Emprestimo emprestimo = new Emprestimo(isbnLivro, matriculaAluno, LocalDate.now(),
                LocalDate.now().plusDays(diasParaDevolucao));
        emprestimos.add(emprestimo);
        return emprestimo;
    }

    @Override
    public synchronized boolean registrarDevolucao(String isbnLivro, String matriculaAluno) {
        for (Emprestimo emprestimo : emprestimos) {
            if (emprestimo.getIsbnLivro().equals(isbnLivro)
                    && emprestimo.getMatriculaAluno().equals(matriculaAluno)
                    && !emprestimo.isDevolvido()) {
                emprestimo.setDataDevolucaoEfetiva(LocalDate.now());
                livroService.atualizarDisponibilidade(isbnLivro, true);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Emprestimo> listarPorAluno(String matriculaAluno) {
        List<Emprestimo> resultado = new ArrayList<>();
        for (Emprestimo emprestimo : emprestimos) {
            if (emprestimo.getMatriculaAluno().equals(matriculaAluno)) {
                resultado.add(emprestimo);
            }
        }
        return resultado;
    }

    @Override
    public List<Emprestimo> listarEmAberto() {
        List<Emprestimo> resultado = new ArrayList<>();
        for (Emprestimo emprestimo : emprestimos) {
            if (!emprestimo.isDevolvido()) {
                resultado.add(emprestimo);
            }
        }
        return resultado;
    }
}
