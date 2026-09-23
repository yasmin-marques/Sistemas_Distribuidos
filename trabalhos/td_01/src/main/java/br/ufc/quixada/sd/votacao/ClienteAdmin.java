package br.ufc.quixada.sd.votacao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

// Cliente do administrador

public class ClienteAdmin {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));

        try (Socket socket = new Socket(host, ServidorVotacao.PORTA_TCP);
             ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())) {

            System.out.print("Senha de administrador: ");
            String senha = teclado.readLine();
            RespostaVotacao login = enviarErecerber(saida, entrada,
                    new RequisicaoVotacao(TipoOperacaoVotacao.LOGIN_ADMIN, senha));
            if (!login.isSucesso()) {
                return;
            }

            boolean continuar = true;
            while (continuar) {
                System.out.println("\n== Administração da votação ==");
                System.out.println("1 - Listar candidatos");
                System.out.println("2 - Adicionar candidato");
                System.out.println("3 - Remover candidato");
                System.out.println("4 - Enviar nota informativa (multicast)");
                System.out.println("5 - Consultar resultado parcial/final");
                System.out.println("0 - Sair");
                System.out.print("Opção: ");
                String opcao = teclado.readLine();
                if (opcao == null) {
                    break;
                }
                switch (opcao.trim()) {
                    case "1":
                        enviarErecerber(saida, entrada, new RequisicaoVotacao(TipoOperacaoVotacao.LISTAR_CANDIDATOS));
                        break;
                    case "2":
                        System.out.print("Nome do candidato: ");
                        enviarErecerber(saida, entrada,
                                new RequisicaoVotacao(TipoOperacaoVotacao.ADICIONAR_CANDIDATO, teclado.readLine()));
                        break;
                    case "3":
                        System.out.print("ID do candidato: ");
                        int id = Integer.parseInt(teclado.readLine().trim());
                        enviarErecerber(saida, entrada, new RequisicaoVotacao(TipoOperacaoVotacao.REMOVER_CANDIDATO, id));
                        break;
                    case "4":
                        System.out.print("Texto da nota: ");
                        enviarErecerber(saida, entrada,
                                new RequisicaoVotacao(TipoOperacaoVotacao.ENVIAR_NOTA, teclado.readLine()));
                        break;
                    case "5":
                        enviarErecerber(saida, entrada, new RequisicaoVotacao(TipoOperacaoVotacao.CONSULTAR_RESULTADO));
                        break;
                    case "0":
                        saida.writeObject(new RequisicaoVotacao(TipoOperacaoVotacao.SAIR));
                        saida.flush();
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opção inválida.");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static RespostaVotacao enviarErecerber(ObjectOutputStream saida, ObjectInputStream entrada,
                                                     RequisicaoVotacao requisicao) throws IOException, ClassNotFoundException {
        saida.writeObject(requisicao);
        saida.flush();
        RespostaVotacao resposta = (RespostaVotacao) entrada.readObject();
        System.out.println((resposta.isSucesso() ? "[OK] " : "[FALHA] ") + resposta.getMensagem());
        Object dado = resposta.getDado();
        if (dado instanceof List) {
            for (Object item : (List<Candidato>) dado) {
                System.out.println("  " + item);
            }
        } else if (dado != null) {
            System.out.println(dado);
        }
        return resposta;
    }
}
