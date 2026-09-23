package br.ufc.quixada.sd.biblioteca;

import java.util.List;

// Operações do serviço remoto sobre o recurso Livro.

public interface LivroService {

    void cadastrar(Livro livro);

    Livro buscarPorIsbn(String isbn);

    List<Livro> listarTodos();

    boolean remover(String isbn);

    boolean atualizarDisponibilidade(String isbn, boolean disponivel);
}
