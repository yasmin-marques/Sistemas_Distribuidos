package br.ufc.quixada.sd.biblioteca;

// Exercício 1 - (b) Destino: System.out.

public class TesteSaidaPadrao {

    public static void main(String[] args) throws Exception {
        Livro[] livros = DadosExemplo.criarLivrosExemplo();
        LivroOutputStream saida = new LivroOutputStream(System.out, livros, livros.length);
        saida.enviarLivros();
        saida.flush();
        // Não fechamos System.out (close() encerraria o descritor da JVM);
        // fazemos apenas o flush para garantir que os bytes saiam do buffer.
    }
}
