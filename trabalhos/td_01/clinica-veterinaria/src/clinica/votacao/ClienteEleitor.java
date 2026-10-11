package clinica.votacao;

import clinica.multicast.Grupo;
import clinica.multicast.Notificacao;
import clinica.multicast.OuvinteMulticast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

// Cliente do eleitor. A thread principal cuida do menu (TCP) e uma segunda
// thread escuta as notas informativas enviadas pelos administradores (multicast).

public class ClienteEleitor {

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
            if (!"ELEITOR".equals(login.getPapel())) {
                System.out.println("Esta conta é de administrador; use o ClienteAdmin.");
                return;
            }

            Grupo grupo = login.getGrupo();
            try (OuvinteMulticast ouvinte = new OuvinteMulticast(grupo, ClienteEleitor::exibirNota)) {
                ouvinte.entrar();
                menu(cliente, teclado);
            }
        }
    }

    private static void menu(ClienteVotacao cliente, BufferedReader teclado) throws IOException {
        while (true) {
            System.out.println("\n1 - Listar candidatos | 2 - Votar | 3 - Ver resultado | 0 - Sair");
            System.out.print("Opção: ");
            String opcao = teclado.readLine();
            if (opcao == null) return;
            try {
                switch (opcao.trim()) {
                    case "1":
                        cliente.listar().imprimir();
                        break;
                    case "2":
                        System.out.print("ID do candidato: ");
                        cliente.votar(Integer.parseInt(teclado.readLine().trim())).imprimir();
                        break;
                    case "3":
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

    private static void exibirNota(Notificacao nota) {
        System.out.println("\n>>> Nota da administração: " + nota.getMensagem());
    }
}
