package br.ufc.quixada.sd.biblioteca;

// Exercício 2 - (b) Origem: System.in.

public class TesteEntradaPadrao {

    public static void main(String[] args) throws Exception {
        LivroInputStream entrada = new LivroInputStream(System.in);
        Livro[] livros = entrada.lerLivros();
        System.err.println("Livros recebidos pela entrada padrão:");
        for (Livro livro : livros) {
            System.err.println("  " + livro);
        }
    }
}
