package br.ufc.quixada.sd.biblioteca;

import java.net.ServerSocket;
import java.net.Socket;

// Exercício 2.d - Origem: Cliente remoto via TCP

public class ServidorTCPStream {

    static final int PORTA = 5000;

    public static void main(String[] args) throws Exception {
        try (ServerSocket servidor = new ServerSocket(PORTA)) {
            System.out.println("ServidorTCPStream aguardando conexões na porta " + PORTA + "...");
            while (true) {
                try (Socket cliente = servidor.accept();
                     LivroInputStream entrada = new LivroInputStream(cliente.getInputStream())) {
                    System.out.println("Cliente conectado: " + cliente.getRemoteSocketAddress());
                    Livro[] livros = entrada.lerLivros();
                    System.out.println("Livros recebidos via TCP:");
                    for (Livro livro : livros) {
                        System.out.println("  " + livro);
                    }
                }
            }
        }
    }
}
