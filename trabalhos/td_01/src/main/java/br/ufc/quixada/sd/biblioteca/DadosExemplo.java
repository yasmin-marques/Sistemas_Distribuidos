package br.ufc.quixada.sd.biblioteca;

final class DadosExemplo {

    private DadosExemplo() {
    }

    static Livro[] criarLivrosExemplo() {
        return new Livro[]{
                new Livro("978-85-7522-000-1", "Sistemas Distribuídos: Princípios e Paradigmas",
                        "Tanenbaum & Van Steen", 2007, true),
                new Livro("978-85-7522-000-2", "Redes de Computadores", "Tanenbaum", 2011, true),
                new Livro("978-85-7522-000-3", "Programação Concorrente em Java", "Goetz", 2006, true)
        };
    }
}
