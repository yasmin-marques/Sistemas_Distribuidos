package br.ufc.quixada.sd.biblioteca;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

public class ClienteBiblioteca {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        String host = args.length > 0 ? args[0] : "localhost";

        try (Socket socket = new Socket(host, ServidorBiblioteca.PORTA);
             ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            boolean continuar = true;
            while (continuar) {
                exibirMenu();
                String opcao = teclado.readLine();
                if (opcao == null) {
                    break;
                }
                switch (opcao.trim()) {
                    case "1":
                        System.out.print("ISBN: ");
                        String isbnCad = teclado.readLine();
                        System.out.print("Título: ");
                        String titulo = teclado.readLine();
                        System.out.print("Autor: ");
                        String autor = teclado.readLine();
                        System.out.print("Ano: ");
                        int ano = Integer.parseInt(teclado.readLine().trim());
                        enviarErecerber(saida, entrada,
                                new Requisicao(TipoOperacao.CADASTRAR_LIVRO,
                                        new Livro(isbnCad, titulo, autor, ano, true)));
                        break;
                    case "2":
                        System.out.print("ISBN a buscar: ");
                        enviarErecerber(saida, entrada,
                                new Requisicao(TipoOperacao.BUSCAR_LIVRO, teclado.readLine()));
                        break;
                    case "3":
                        enviarErecerber(saida, entrada, new Requisicao(TipoOperacao.LISTAR_LIVROS));
                        break;
                    case "4":
                        System.out.print("ISBN a remover: ");
                        enviarErecerber(saida, entrada,
                                new Requisicao(TipoOperacao.REMOVER_LIVRO, teclado.readLine()));
                        break;
                    case "5":
                        System.out.print("ISBN do livro: ");
                        String isbnEmp = teclado.readLine();
                        System.out.print("Matrícula do aluno: ");
                        String matriculaEmp = teclado.readLine();
                        System.out.print("Dias para devolução: ");
                        int dias = Integer.parseInt(teclado.readLine().trim());
                        enviarErecerber(saida, entrada,
                                new Requisicao(TipoOperacao.REGISTRAR_EMPRESTIMO, isbnEmp, matriculaEmp, dias));
                        break;
                    case "6":
                        System.out.print("ISBN do livro: ");
                        String isbnDev = teclado.readLine();
                        System.out.print("Matrícula do aluno: ");
                        String matriculaDev = teclado.readLine();
                        enviarErecerber(saida, entrada,
                                new Requisicao(TipoOperacao.REGISTRAR_DEVOLUCAO, isbnDev, matriculaDev));
                        break;
                    case "7":
                        enviarErecerber(saida, entrada, new Requisicao(TipoOperacao.LISTAR_EMPRESTIMOS_ABERTO));
                        break;
                    case "0":
                        saida.writeObject(new Requisicao(TipoOperacao.SAIR));
                        saida.flush();
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opção inválida.");
                }
            }
        }
    }

    private static void exibirMenu() {
        System.out.println("\n== Biblioteca (RPC via sockets) ==");
        System.out.println("1 - Cadastrar livro");
        System.out.println("2 - Buscar livro por ISBN");
        System.out.println("3 - Listar livros");
        System.out.println("4 - Remover livro");
        System.out.println("5 - Registrar empréstimo");
        System.out.println("6 - Registrar devolução");
        System.out.println("7 - Listar empréstimos em aberto");
        System.out.println("0 - Sair");
        System.out.print("Opção: ");
    }

    @SuppressWarnings("unchecked")
    private static void enviarErecerber(ObjectOutputStream saida, ObjectInputStream entrada, Requisicao requisicao)
            throws IOException, ClassNotFoundException {
        // Empacota a requisição e envia para o servidor.
        saida.writeObject(requisicao);
        saida.flush();
        // Desempacota a resposta enviada pelo servidor.
        Resposta resposta = (Resposta) entrada.readObject();
        System.out.println((resposta.isSucesso() ? "[OK] " : "[FALHA] ") + resposta.getMensagem());
        Object dado = resposta.getDado();
        if (dado instanceof List) {
            for (Object item : (List<Object>) dado) {
                System.out.println("  " + item);
            }
        } else if (dado != null) {
            System.out.println("  " + dado);
        }
    }
}
