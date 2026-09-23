package br.ufc.quixada.sd.biblioteca;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

// Exercício 3 - Servidor do serviço remoto Biblioteca.

public class ServidorBiblioteca {

    static final int PORTA = 5001;

    private static final LivroService livroService = new LivroServiceImpl();
    private static final EmprestimoService emprestimoService = new EmprestimoServiceImpl(livroService);

    public static void main(String[] args) throws IOException {
        try (ServerSocket servidor = new ServerSocket(PORTA)) {
            System.out.println("ServidorBiblioteca (RPC via sockets) ouvindo na porta " + PORTA);
            while (true) {
                Socket cliente = servidor.accept();
                new Thread(() -> atenderCliente(cliente)).start();
            }
        }
    }

    private static void atenderCliente(Socket cliente) {
        System.out.println("Cliente conectado: " + cliente.getRemoteSocketAddress());
        try (Socket socket = cliente;
             ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())) {

            while (true) {
                // Desempacota a requisição enviada pelo cliente.
                Requisicao requisicao = (Requisicao) entrada.readObject();
                if (requisicao.getOperacao() == TipoOperacao.SAIR) {
                    break;
                }
                Resposta resposta = processar(requisicao);
                // Empacota a resposta e envia de volta para o cliente.
                saida.writeObject(resposta);
                saida.flush();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Cliente desconectado: " + e.getMessage());
        }
    }

    private static Resposta processar(Requisicao requisicao) {
        Object[] p = requisicao.getParametros();
        try {
            switch (requisicao.getOperacao()) {
                case CADASTRAR_LIVRO: {
                    Livro livro = (Livro) p[0];
                    livroService.cadastrar(livro);
                    return new Resposta(true, "Livro cadastrado.", null);
                }
                case BUSCAR_LIVRO: {
                    Livro livro = livroService.buscarPorIsbn((String) p[0]);
                    return new Resposta(livro != null, livro != null ? "Encontrado." : "Não encontrado.", livro);
                }
                case LISTAR_LIVROS:
                    return new Resposta(true, "OK", livroService.listarTodos());
                case REMOVER_LIVRO: {
                    boolean ok = livroService.remover((String) p[0]);
                    return new Resposta(ok, ok ? "Removido." : "ISBN não encontrado.", null);
                }
                case REGISTRAR_EMPRESTIMO: {
                    String isbn = (String) p[0];
                    String matricula = (String) p[1];
                    int dias = (Integer) p[2];
                    Emprestimo emprestimo = emprestimoService.registrarEmprestimo(isbn, matricula, dias);
                    boolean ok = emprestimo != null;
                    return new Resposta(ok, ok ? "Empréstimo registrado." : "Livro indisponível.", emprestimo);
                }
                case REGISTRAR_DEVOLUCAO: {
                    boolean ok = emprestimoService.registrarDevolucao((String) p[0], (String) p[1]);
                    return new Resposta(ok, ok ? "Devolução registrada." : "Empréstimo não encontrado.", null);
                }
                case LISTAR_EMPRESTIMOS_ABERTO:
                    return new Resposta(true, "OK", emprestimoService.listarEmAberto());
                default:
                    return new Resposta(false, "Operação desconhecida.", null);
            }
        } catch (RuntimeException e) {
            return new Resposta(false, "Erro ao processar requisição: " + e.getMessage(), null);
        }
    }
}
