package br.ufc.quixada.sd.biblioteca;

import java.io.FileInputStream;

public class TesteArquivoLeitura {

    public static void main(String[] args) throws Exception {
        String arquivo = args.length > 0 ? args[0] : TesteArquivoEscrita.ARQUIVO;
        try (FileInputStream fileInputStream = new FileInputStream(arquivo);
             LivroInputStream entrada = new LivroInputStream(fileInputStream)) {
            Livro[] livros = entrada.lerLivros();
            System.out.println("Livros lidos de " + arquivo + ":");
            for (Livro livro : livros) {
                System.out.println("  " + livro);
            }
        }
    }
}
