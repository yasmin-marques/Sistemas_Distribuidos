package br.ufc.quixada.sd.biblioteca;

import java.io.FileOutputStream;

public class TesteArquivoEscrita {

    static final String ARQUIVO = "livros.dat";

    public static void main(String[] args) throws Exception {
        Livro[] livros = DadosExemplo.criarLivrosExemplo();
        try (FileOutputStream arquivo = new FileOutputStream(ARQUIVO);
             LivroOutputStream saida = new LivroOutputStream(arquivo, livros, livros.length)) {
            saida.enviarLivros();
            System.out.println(livros.length + " livro(s) gravado(s) em " + ARQUIVO);
        }
    }
}
