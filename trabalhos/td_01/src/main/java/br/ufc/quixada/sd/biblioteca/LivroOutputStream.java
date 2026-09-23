package br.ufc.quixada.sd.biblioteca;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

// Exercício 1 - Sockets e Streams

// (i)   o OutputStream de destino;
// (ii)  o array de objetos a transmitir;
// (iii) a quantidade de objetos do array que devem ser enviados.

public class LivroOutputStream extends OutputStream {

    private final OutputStream destino;
    private final Livro[] livros;
    private final int quantidade;
    private final ObjectOutputStream objectOutputStream;

    public LivroOutputStream(OutputStream destino, Livro[] livros, int quantidade) throws IOException {
        if (quantidade > livros.length) {
            throw new IllegalArgumentException("quantidade maior que o tamanho do array informado");
        }
        this.destino = destino;
        this.livros = livros;
        this.quantidade = quantidade;
        this.objectOutputStream = new ObjectOutputStream(destino);
    }

    public void enviarLivros() throws IOException {
        objectOutputStream.writeInt(quantidade);
        for (int i = 0; i < quantidade; i++) {
            objectOutputStream.writeObject(livros[i]);
        }
        objectOutputStream.flush();
    }

    // OutputStream é uma classe abstrata; write(int) é o único método
    // obrigatório e aqui simplesmente delega para o stream de destino.
    @Override
    public void write(int b) throws IOException {
        destino.write(b);
    }

    @Override
    public void flush() throws IOException {
        objectOutputStream.flush();
    }

    @Override
    public void close() throws IOException {
        objectOutputStream.close();
    }
}
