package br.ufc.sd.consultas;

import br.ufc.sd.consultas.RepositorioConsultas.RegistroConsulta;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consultas (linha de comando): registra consultas e, quando há medicamento ou vacina, publica
 * a solicitação de baixa na fila. Não chama o Estoque nem acessa o banco dele.
 *
 * <pre>
 *   registrar --animal Rex --especie Cachorro --diagnostico "Otite" [--produto 1 --quantidade 2]
 *   listar
 * </pre>
 */
public final class ClienteConsultas {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private ClienteConsultas() { }

    public static void main(String[] args) {
        Path base = Path.of(System.getProperty("consultas.base", "."));
        RepositorioConsultas repositorio = new RepositorioConsultas(base.resolve("consultas/dados/consultas.csv"));
        try {
            if (args.length == 0 || args[0].equals("ajuda")) {
                ajuda();
            } else if (args[0].equals("listar")) {
                List<RegistroConsulta> consultas = repositorio.listar();
                if (consultas.isEmpty()) System.out.println("Nenhuma consulta registrada.");
                consultas.forEach(System.out::println);
            } else if (args[0].equals("registrar")) {
                ConfiguracaoRabbit config = ConfiguracaoRabbit.carregar(base.resolve(".env"));
                System.exit(registrar(opcoes(args), config, repositorio));
            } else {
                erro("Comando desconhecido: " + args[0]);
                ajuda();
                System.exit(1);
            }
        } catch (IllegalArgumentException | IOException e) {
            erro("ERRO: " + e.getMessage());
            System.exit(1);
        }
    }

    private static int registrar(Map<String, String> op, ConfiguracaoRabbit config,
                                 RepositorioConsultas repositorio) throws IOException {
        String animal = obrigatoria(op, "animal");
        String especie = op.getOrDefault("especie", "Cachorro");
        String sintomas = op.getOrDefault("sintomas", "-");
        String diagnostico = op.getOrDefault("diagnostico", "-");
        int idProduto = inteiro(op, "produto", 0);
        int quantidade = inteiro(op, "quantidade", idProduto > 0 ? 1 : 0);
        if (idProduto < 0 || (idProduto > 0 && quantidade <= 0)) {
            throw new IllegalArgumentException("--produto e --quantidade devem ser positivos.");
        }

        int idConsulta = repositorio.proximoId();
        String idMensagem = "";
        if (idProduto > 0) {
            SolicitacaoBaixa solicitacao = SolicitacaoBaixa.nova(idConsulta, idProduto, quantidade);
            idMensagem = solicitacao.idMensagem();
            log("Consulta #" + idConsulta + ": uso de " + quantidade + "x produto #" + idProduto
                    + ". Publicando solicitação de baixa em " + config.descricao());
            log("Mensagem " + idMensagem + ": " + solicitacao.toJson());
            try (PublicadorBaixas publicador = new PublicadorBaixas(config)) {
                publicador.conectar();
                publicador.publicar(solicitacao);
            } catch (FalhaPublicacaoException e) {
                erro("ERRO: a solicitação de baixa NÃO entrou na fila: " + e.getMessage() + ".");
                erro("Consulta #" + idConsulta + " não registrada. Confira o RabbitMQ (docker compose ps) e o .env.");
                return 2;
            }
            log("Broker confirmou: mensagem " + idMensagem + " na fila '" + config.fila()
                    + "'. A baixa só acontece quando o Estoque consumir.");
        }

        RegistroConsulta consulta = new RegistroConsulta(idConsulta, LocalDateTime.now(), animal,
                especie, sintomas, diagnostico, idProduto, quantidade, idMensagem);
        try {
            repositorio.salvar(consulta);
        } catch (IOException e) {
            erro("ERRO ao gravar a consulta #" + idConsulta + ": " + e.getMessage()
                    + (idMensagem.isEmpty() ? "" : " (a mensagem " + idMensagem + " já está na fila)"));
            return 3;
        }
        log("Registrada: " + consulta);
        return 0;
    }

    private static Map<String, String> opcoes(String[] args) {
        Map<String, String> op = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("opção inválida ou sem valor: " + args[i]);
            }
            op.put(args[i].substring(2), args[++i]);
        }
        return op;
    }

    private static String obrigatoria(Map<String, String> op, String nome) {
        String v = op.get(nome);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("informe --" + nome + ".");
        return v;
    }

    private static int inteiro(Map<String, String> op, String nome, int padrao) {
        String v = op.get(nome);
        if (v == null) return padrao;
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + nome + " deve ser um número inteiro: '" + v + "'.");
        }
    }

    private static void log(String texto) {
        System.out.println(LocalTime.now().format(HORA) + " [CONSULTAS] " + texto);
    }

    private static void erro(String texto) {
        System.err.println(LocalTime.now().format(HORA) + " [CONSULTAS] " + texto);
    }

    private static void ajuda() {
        System.out.println("""
                Uso:
                  consultas.ps1 registrar --animal NOME [--especie Cachorro|Gato|Coelho]
                                          [--sintomas TEXTO] [--diagnostico TEXTO]
                                          [--produto ID --quantidade N]
                  consultas.ps1 listar""");
    }
}
