package br.ufc.quixada.sd.biblioteca;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


// Implementação do serviço de livros com armazenamento em memória.

public class LivroServiceImpl implements LivroService {

    private final Map<String, Livro> acervo = new ConcurrentHashMap<>();

    public LivroServiceImpl() {
        // Alguns dados iniciais para facilitar os testes manuais.
        cadastrar(new Livro("978-85-7522-000-1", "Sistemas Distribuídos: Princípios e Paradigmas",
                "Tanenbaum & Van Steen", 2007, true));
        cadastrar(new Livro("978-85-7522-000-2", "Redes de Computadores", "Tanenbaum", 2011, true));
        cadastrar(new Livro("978-85-7522-000-3", "Programação Concorrente em Java", "Goetz", 2006, true));
    }

    @Override
    public void cadastrar(Livro livro) {
        acervo.put(livro.getIsbn(), livro);
    }

    @Override
    public Livro buscarPorIsbn(String isbn) {
        return acervo.get(isbn);
    }

    @Override
    public List<Livro> listarTodos() {
        return new ArrayList<>(acervo.values());
    }

    @Override
    public boolean remover(String isbn) {
        return acervo.remove(isbn) != null;
    }

    @Override
    public boolean atualizarDisponibilidade(String isbn, boolean disponivel) {
        Livro livro = acervo.get(isbn);
        if (livro == null) {
            return false;
        }
        livro.setDisponivel(disponivel);
        return true;
    }
}
