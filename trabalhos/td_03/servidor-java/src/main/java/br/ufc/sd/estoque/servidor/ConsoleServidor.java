package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Locale;

/** Comandos do operador no terminal do servidor (os mesmos do servidor multicast do TD01). */
final class ConsoleServidor {

    static final String OPERADOR = "console-servidor";

    private final ServidorEstoque servidor;
    private final BufferedReader teclado;

    ConsoleServidor(ServidorEstoque servidor, BufferedReader teclado) {
        this.servidor = servidor;
        this.teclado = teclado;
    }

    void executar() throws IOException {
        ajuda();
        String linha;
        while ((linha = teclado.readLine()) != null) {
            String[] partes = linha.trim().split("\\s+", 2);
            String comando = partes[0].toUpperCase(Locale.ROOT);
            String resto = partes.length > 1 ? partes[1].trim() : "";
            try {
                switch (comando) {
                    case "" -> { }
                    case "SAIR" -> {
                        return;
                    }
                    case "AJUDA" -> ajuda();
                    case "NOTIFICACAO", "ALERTA", "ATUALIZACAO" -> {
                        if (resto.isEmpty()) {
                            System.out.println("Uso: " + comando + " <mensagem>");
                        } else {
                            servidor.central().publicar(TipoNotificacao.valueOf(comando), resto);
                        }
                    }
                    case "ESTOQUE" -> {
                        for (ItemEstoque i : servidor.estoque().listarItens()) System.out.println("  " + i);
                        System.out.printf(Locale.ROOT, "  Valor total em estoque: R$ %.2f%n", servidor.estoque().valorTotal());
                    }
                    case "BAIXAR" -> {
                        String[] a = resto.split("\\s+", 3);
                        Movimentacao m = servidor.estoque().saidaComo(OPERADOR, Integer.parseInt(a[0]),
                                Integer.parseInt(a[1]), a.length > 2 ? a[2] : "baixa pelo console");
                        System.out.println("Baixa feita: " + m);
                    }
                    case "REPOR" -> {
                        String[] a = resto.split("\\s+");
                        Movimentacao m = servidor.estoque().entradaComo(OPERADOR, Integer.parseInt(a[0]),
                                Integer.parseInt(a[1]), "reposição pelo console");
                        System.out.println("Reposição feita: " + m);
                    }
                    case "MOVIMENTACOES" -> servidor.estoque().listarMovimentacoes(0)
                            .forEach(m -> System.out.println("  " + m));
                    case "CLIENTES" -> {
                        System.out.println("Sessões abertas: " + servidor.autenticacao().usuariosConectados());
                        System.out.println("Membros do grupo: " + servidor.central().membros());
                    }
                    case "OBJETOS" -> servidor.tabela().nomes().forEach(n -> System.out.println("  " + n));
                    case "VERIFICAR" -> servidor.monitor().verificarTodos();
                    default -> System.out.println("Comando inválido. Digite AJUDA.");
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.out.println("Uso: " + comando.toLowerCase(Locale.ROOT) + " <id> <quantidade>");
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("Erro: " + e.getMessage());
            }
        }
    }

    private static void ajuda() {
        System.out.println("Comandos do servidor:");
        System.out.println("  NOTIFICACAO|ALERTA|ATUALIZACAO <mensagem>  envia ao grupo");
        System.out.println("  baixar <id> <qtd> [motivo]   dá baixa no estoque (o monitor avisa se ficar baixo)");
        System.out.println("  repor <id> <qtd>             repõe o estoque (envia ATUALIZACAO)");
        System.out.println("  estoque | movimentacoes | clientes | objetos | verificar | ajuda | sair");
    }
}
