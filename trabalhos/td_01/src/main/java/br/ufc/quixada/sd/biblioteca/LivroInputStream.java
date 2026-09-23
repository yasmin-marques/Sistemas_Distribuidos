package br.ufc.quixada.sd.biblioteca;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

// Exercício 2 - Sockets e Streams

public class LivroInputStream extends InputStream {

    private final InputStream origem;
    private final ObjectInputStream objectInputStream;

    public LivroInputStream(InputStream origem) throws IOException {
        this.origem = origem;
        this.objectInputStream = new ObjectInputStream(origem);
    }

    // Lê a quantidade de livros gravada pelo LivroOutputStream e, em
    // seguida, desserializa cada objeto Livro, devolvendo o array completo. 

    public Livro[] lerLivros() throws IOException, ClassNotFoundException {
        int quantidade = objectInputStream.readInt();
        Livro[] livros = new Livro[quantidade];
        for (int i = 0; i < quantidade; i++) {
            livros[i] = (Livro) objectInputStream.readObject();
        }
        return livros;
    }

    @Override
    public int read() throws IOException {
        return origem.read();
    }

    @Override
    public void close() throws IOException {
        objectInputStream.close();
    }
}
