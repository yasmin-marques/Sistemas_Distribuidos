package br.ufc.quixada.sd.biblioteca;

import java.net.Socket;


// Exercício 1 - (d)

public class ClienteTCPStream {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = ServidorTCPStream.PORTA;

        try (Socket socket = new Socket(host, porta)) {
            Livro[] livros = DadosExemplo.criarLivrosExemplo();
            LivroOutputStream saida = new LivroOutputStream(socket.getOutputStream(), livros, livros.length);
            saida.enviarLivros();
            saida.flush();
            System.out.println(livros.length + " livro(s) enviado(s) para " + host + ":" + porta);
        }
    }
}
