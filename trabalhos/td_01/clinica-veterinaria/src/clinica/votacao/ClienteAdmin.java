package clinica.votacao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// Cliente do administrador: gerencia candidatos e envia notas informativas aos eleitores
public class ClienteAdmin {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Usuário: ");
        String usuario = teclado.readLine();
        System.out.print("Senha: ");
        String senha = teclado.readLine();

        try (ClienteVotacao cliente = new ClienteVotacao(host, ServidorVotacao.PORTA_TCP)) {
            RespostaVotacao login = cliente.login(usuario, senha);
            login.imprimir();
            if (!login.isSucesso()) return;
            if (!"ADMIN".equals(login.getPapel())) {
                System.out.println("Esta conta é de eleitor; use o ClienteEleitor.");
                return;
            }
            menu(cliente, teclado);
        }
    }

    private static void menu(ClienteVotacao cliente, BufferedReader teclado) throws IOException {
        while (true) {
            System.out.println("\n1 - Listar | 2 - Adicionar candidato | 3 - Remover candidato"
                    + " | 4 - Enviar nota | 5 - Ver resultado | 0 - Sair");
            System.out.print("Opção: ");
            String opcao = teclado.readLine();
            if (opcao == null) return;
            try {
                switch (opcao.trim()) {
                    case "1":
                        cliente.listar().imprimir();
                        break;
                    case "2":
                        System.out.print("Nome: ");
                        String nome = teclado.readLine();
                        System.out.print("Descrição: ");
                        cliente.adicionarCandidato(nome, teclado.readLine()).imprimir();
                        break;
                    case "3":
                        System.out.print("ID do candidato: ");
                        cliente.removerCandidato(Integer.parseInt(teclado.readLine().trim())).imprimir();
                        break;
                    case "4":
                        System.out.print("Texto da nota: ");
                        cliente.enviarNota(teclado.readLine()).imprimir();
                        break;
                    case "5":
                        cliente.resultado().imprimir();
                        break;
                    case "0":
                        return;
                    default:
                        System.out.println("Opção inválida.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Digite um número.");
            }
        }
    }
}
