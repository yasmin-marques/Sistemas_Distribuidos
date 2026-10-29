package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.Insumo;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.Medicamento;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.modelo.PedidoReposicao;
import br.ufc.sd.estoque.modelo.Sessao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import br.ufc.sd.estoque.modelo.Vacina;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.FalhaComunicacaoException;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Cliente de terminal do estoque veterinário.
 *
 * <p>Duas threads, como no TD01: esta (interação com o usuário) e a do
 * {@link OuvinteNotificacoes}, que exibe as mensagens do grupo assim que chegam.
 * Vários clientes podem rodar ao mesmo tempo, cada um no seu terminal.
 */
public final class ClienteEstoque {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ConexaoClinica conexao;
    private final BufferedReader entrada;
    private final EstoqueRemoto estoque;
    private final NotificacoesRemotas notificacoes;
    private Sessao sessao;
    private OuvinteNotificacoes ouvinte;
    private PedidoReposicaoRemoto pedido;

    public ClienteEstoque(ConexaoClinica conexao, BufferedReader entrada) {
        this.conexao = conexao;
        this.entrada = entrada;
        this.estoque = conexao.estoque();
        this.notificacoes = conexao.notificacoes();
    }

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
        Charset cs = Charset.forName(System.getProperty("stdout.encoding", Charset.defaultCharset().name()));
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, cs));
        int codigo = new ClienteEstoque(new ConexaoClinica(host, porta), teclado).executar();
        System.exit(codigo);
    }

    /** @return código de saída do processo */
    public int executar() throws IOException {
        imprimir("Cliente do estoque veterinário (TD02 - Java RMI) - servidor " + conexao);
        try {
            if (!autenticar()) return 1;
            entrarNoGrupo();
            ajuda();
            String linha;
            while (true) {
                linha = entrada.readLine();
                if (linha == null) break;
                try {
                    if (!processar(linha.trim())) break;
                } catch (ExcecaoRemota e) {
                    imprimir("Erro do servidor (" + e.getCodigo() + "): " + e.getMessage());
                } catch (FalhaComunicacaoException e) {
                    imprimir("Falha de comunicação: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    imprimir("Entrada inválida: " + e.getMessage());
                }
            }
        } catch (FalhaComunicacaoException e) {
            imprimir("Não foi possível falar com o servidor: " + e.getMessage());
            return 1;
        }
        encerrar();
        imprimir("Até logo!");
        return 0;
    }

    // ------------------------------------------------------------------ sessão e grupo

    private boolean autenticar() throws IOException {
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            String usuario = perguntar("Usuário: ");
            if (usuario == null) return false;
            String senha = perguntar("Senha: ");
            if (senha == null) return false;
            try {
                sessao = conexao.autenticacao().autenticar(usuario.trim(), senha);
                imprimir("Autenticado como " + sessao.getUsuario() + ".");
                return true;
            } catch (ExcecaoRemota e) {
                imprimir("Autenticação recusada: " + e.getMessage());
            }
        }
        return false;
    }

    private void entrarNoGrupo() {
        if (ouvinte != null && ouvinte.ativo()) {
            imprimir("Você já está no grupo.");
            return;
        }
        AssinaturaRemota assinatura = notificacoes.entrarGrupo(sessao.getToken());
        ouvinte = new OuvinteNotificacoes(assinatura, this::exibir, aviso -> imprimir("[aviso] " + aviso));
        ouvinte.iniciar();
        imprimir("Você entrou no grupo de notificações (referência: " + ((ProxyBase) assinatura).referencia() + ").");
    }

    private void sairDoGrupo() {
        if (ouvinte == null || !ouvinte.ativo()) {
            imprimir("Você não está no grupo.");
            return;
        }
        ouvinte.parar();
        notificacoes.sairGrupo(ouvinte.assinatura());
        imprimir("Você saiu do grupo (não recebe mais mensagens).");
    }

    private void encerrar() {
        try {
            if (ouvinte != null && ouvinte.ativo()) sairDoGrupo();
        } catch (RuntimeException ignorado) {
            // o servidor descarta a assinatura sozinho quando o prazo vence
        }
        try {
            if (sessao != null) conexao.autenticacao().encerrarSessao(sessao.getToken());
        } catch (RuntimeException ignorado) {
            // sessão perdida junto com o servidor
        }
    }

    private void exibir(Notificacao n) {
        imprimir("[" + HORA.format(Instant.ofEpochMilli(n.getTimestamp())) + "] " + n);
    }

    // ------------------------------------------------------------------ comandos

    private boolean processar(String linha) throws IOException {
        if (linha.isEmpty()) return true;
        String[] p = linha.split("\\s+");
        String comando = p[0].toLowerCase(Locale.ROOT);
        switch (comando) {
            case "sair" -> {
                return false;
            }
            case "ajuda" -> ajuda();
            case "itens" -> {
                LocalDate hoje = LocalDate.now();
                for (ItemEstoque i : estoque.listarItens()) imprimir("  " + i + situacao(i, hoje));
            }
            case "item" -> imprimir("  " + estoque.buscarItem(inteiro(p, 1, "id")));
            case "fornecedores" -> estoque.listarFornecedores().forEach(f -> imprimir("  " + f));
            case "movimentacoes" -> {
                List<Movimentacao> movs = estoque.listarMovimentacoes(p.length > 1 ? inteiro(p, 1, "id") : 0);
                if (movs.isEmpty()) imprimir("  (nenhuma movimentação)");
                movs.forEach(m -> imprimir("  " + m));
            }
            case "entrada" -> imprimir("Registrado: " + estoque.registrarEntrada(sessao.getToken(),
                    inteiro(p, 1, "id"), inteiro(p, 2, "quantidade")));
            case "saida" -> imprimir("Registrado: " + estoque.registrarSaida(sessao.getToken(),
                    inteiro(p, 1, "id"), inteiro(p, 2, "quantidade"), resto(linha, 3)));
            case "cadastrar" -> cadastrar();
            case "publicar" -> {
                if (p.length < 3) throw new IllegalArgumentException("uso: publicar <NOTIFICACAO|ALERTA|ATUALIZACAO> <mensagem>");
                TipoNotificacao tipo = CodecEntidades.enumeracao(TipoNotificacao.class, p[1].toUpperCase(Locale.ROOT));
                notificacoes.publicar(sessao.getToken(), tipo, resto(linha, 2));
            }
            case "membros" -> imprimir("Membros do grupo: " + notificacoes.membros());
            case "entrar" -> entrarNoGrupo();
            case "deixar" -> sairDoGrupo();
            case "status" -> imprimir(ouvinte != null && ouvinte.ativo()
                    ? "No grupo de notificações como " + sessao.getUsuario() + "." : "Fora do grupo.");
            case "pedido" -> pedido(p);
            case "demo-valor" -> demoValor(p.length > 1 ? inteiro(p, 1, "id") : 1);
            case "demo-referencia" -> demoReferencia();
            case "protocolo" -> {
                boolean ligar = p.length > 1 && p[1].equalsIgnoreCase("on");
                conexao.protocolo().setObservador(ligar ? msg -> imprimir("    " + msg) : null);
                imprimir("Exibição das mensagens do protocolo " + (ligar ? "ligada." : "desligada."));
            }
            default -> imprimir("Comando desconhecido. Digite 'ajuda'.");
        }
        return true;
    }

    private static String situacao(ItemEstoque i, LocalDate hoje) {
        StringBuilder sb = new StringBuilder();
        if (i.vencido(hoje)) sb.append(" <VENCIDO>");
        else if (i.venceEmBreve(hoje)) sb.append(" <vence em breve>");
        if (i.abaixoDoMinimo()) sb.append(" <estoque baixo>");
        return sb.toString();
    }

    private void pedido(String[] p) {
        String acao = p.length > 1 ? p[1].toLowerCase(Locale.ROOT) : "";
        if (!acao.equals("abrir") && pedido == null) {
            throw new IllegalArgumentException("nenhum pedido aberto; use 'pedido abrir <idFornecedor>'");
        }
        switch (acao) {
            case "abrir" -> {
                pedido = estoque.abrirPedidoReposicao(sessao.getToken(), inteiro(p, 2, "idFornecedor"));
                imprimir("Pedido aberto. Referência recebida: " + ((ProxyBase) pedido).referencia());
                imprimir("  " + pedido.consultar());
            }
            case "item" -> imprimir("  " + pedido.adicionarItem(sessao.getToken(), inteiro(p, 2, "idItem"),
                    inteiro(p, 3, "quantidade")));
            case "ver" -> imprimir("  " + pedido.consultar());
            case "confirmar" -> {
                pedido.confirmarRecebimento(sessao.getToken()).forEach(m -> imprimir("  " + m));
                pedido = null;
            }
            case "cancelar" -> {
                imprimir("  " + pedido.cancelar(sessao.getToken()));
                pedido = null;
            }
            default -> throw new IllegalArgumentException(
                    "uso: pedido abrir <idFornecedor> | item <idItem> <qtd> | ver | confirmar | cancelar");
        }
    }

    // ------------------------------------------------------------------ cadastro (passagem por valor)

    private void cadastrar() throws IOException {
        String tipo = obrigatorio("Tipo (medicamento/vacina/insumo): ").toLowerCase(Locale.ROOT);
        if (!List.of("medicamento", "vacina", "insumo").contains(tipo)) {
            throw new IllegalArgumentException("tipo deve ser medicamento, vacina ou insumo");
        }
        String nome = obrigatorio("Nome: ");
        int idFornecedor = numero("Id do fornecedor (veja 'fornecedores'): ");
        int quantidade = numero("Quantidade inicial: ");
        int minimo = numero("Estoque mínimo: ");
        double preco = decimal("Preço unitário (ex.: 12.50): ");
        LocalDate validade = data("Validade (AAAA-MM-DD, vazio = sem validade): ");
        Fornecedor fornecedor = new Fornecedor(idFornecedor, null, null, null);

        ItemEstoque item = switch (tipo) {
            case "medicamento" -> new Medicamento(0, nome, fornecedor, quantidade, minimo, preco, validade,
                    obrigatorio("Princípio ativo: "), obrigatorio("Concentração: "), simNao("Controlado (s/n): "));
            case "vacina" -> new Vacina(0, nome, fornecedor, quantidade, minimo, preco, validade,
                    obrigatorio("Doença-alvo: "), numero("Doses por frasco: "));
            case "insumo" -> new Insumo(0, nome, fornecedor, quantidade, minimo, preco, validade,
                    obrigatorio("Unidade de medida: "), simNao("Estéril (s/n): "));
            default -> throw new IllegalArgumentException("tipo deve ser medicamento, vacina ou insumo");
        };
        imprimir("Objeto local criado no cliente: " + item);
        imprimir("Enviando por valor (JSON): " + Json.escrever(CodecEntidades.itemParaJson(item)));
        ItemEstoque cadastrado = estoque.cadastrarItem(sessao.getToken(), item);
        imprimir("Cópia devolvida pelo servidor: " + cadastrado);
        imprimir("O objeto local continua com id " + item.getId() + ": o servidor trabalhou sobre uma cópia.");
    }

    // ------------------------------------------------------------------ demonstrações

    private void demoValor(int id) {
        imprimir("== Passagem por VALOR ==");
        ItemEstoque copia = estoque.buscarItem(id);
        imprimir("1) buscarItem(" + id + ") devolveu uma cópia: quantidade=" + copia.getQuantidade()
                + ", nome='" + copia.getNome() + "'");
        copia.setQuantidade(99_999);
        copia.setNome("ALTERADO SÓ NO CLIENTE");
        imprimir("2) Alterei a cópia local: quantidade=" + copia.getQuantidade() + ", nome='" + copia.getNome() + "'");
        ItemEstoque doServidor = estoque.buscarItem(id);
        imprimir("3) buscarItem(" + id + ") de novo: quantidade=" + doServidor.getQuantidade()
                + ", nome='" + doServidor.getNome() + "'");
        imprimir("   O objeto do servidor não mudou: o cliente recebeu uma cópia serializada em JSON.");
    }

    private void demoReferencia() {
        imprimir("== Passagem por REFERÊNCIA ==");
        ItemEstoque item = estoque.listarItens().get(0);
        int idFornecedor = item.getFornecedor().getId();
        PedidoReposicaoRemoto p1 = estoque.abrirPedidoReposicao(sessao.getToken(), idFornecedor);
        RemoteObjectRef ref = ((ProxyBase) p1).referencia();
        imprimir("1) abrirPedidoReposicao devolveu uma REFERÊNCIA (o pedido ficou no servidor):");
        imprimir("   " + ref);
        p1.adicionarItem(sessao.getToken(), item.getId(), 5);
        imprimir("2) adicionarItem pela referência alterou o objeto no servidor: " + p1.consultar().getItens());
        PedidoReposicaoRemoto p2 = conexao.pedido(ref);
        imprimir("3) Um segundo proxy criado só com a referência (como se outro cliente a recebesse) vê o mesmo pedido: "
                + p2.consultar().getItens());
        PedidoReposicao cancelado = p2.cancelar(sessao.getToken());
        imprimir("4) Cancelei pelo segundo proxy; o primeiro enxerga: status=" + p1.consultar().getStatus()
                + " (pedido #" + cancelado.getId() + ")");
        imprimir("   Os dois proxies apontam para o MESMO objeto remoto.");
    }

    // ------------------------------------------------------------------ entrada e saída

    private static int inteiro(String[] partes, int indice, String nome) {
        if (partes.length <= indice) throw new IllegalArgumentException("informe " + nome);
        try {
            return Integer.parseInt(partes[indice]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(nome + " deve ser um número inteiro");
        }
    }

    private static String resto(String linha, int pularPalavras) {
        String[] p = linha.split("\\s+", pularPalavras + 1);
        return p.length > pularPalavras ? p[pularPalavras] : null;
    }

    private String perguntar(String rotulo) throws IOException {
        imprimirSemQuebra(rotulo);
        return entrada.readLine();
    }

    private String obrigatorio(String rotulo) throws IOException {
        String v = perguntar(rotulo);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("campo obrigatório");
        return v.trim();
    }

    private int numero(String rotulo) throws IOException {
        try {
            return Integer.parseInt(obrigatorio(rotulo));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("esperava um número inteiro");
        }
    }

    private double decimal(String rotulo) throws IOException {
        try {
            return Double.parseDouble(obrigatorio(rotulo).replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("esperava um número");
        }
    }

    private LocalDate data(String rotulo) throws IOException {
        String v = perguntar(rotulo);
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("data deve estar no formato AAAA-MM-DD");
        }
    }

    private boolean simNao(String rotulo) throws IOException {
        String v = obrigatorio(rotulo).toLowerCase(Locale.ROOT);
        return v.startsWith("s") || v.startsWith("y");
    }

    private synchronized void imprimir(String texto) {
        System.out.println(texto);
        System.out.flush();
    }

    private synchronized void imprimirSemQuebra(String texto) {
        System.out.print(texto);
        System.out.flush();
    }

    private void ajuda() {
        imprimir("""
                Comandos:
                  itens                          lista o estoque
                  item <id>                      detalhes de um item
                  fornecedores                   lista os fornecedores
                  movimentacoes [id]             histórico (de um item ou de todos)
                  entrada <id> <qtd>             registra entrada no estoque
                  saida <id> <qtd> [motivo]      registra saída (controlados exigem motivo)
                  cadastrar                      cria um item no cliente e envia POR VALOR
                  pedido abrir <idFornecedor>    abre um pedido de reposição (recebe uma REFERÊNCIA)
                  pedido item <idItem> <qtd> | pedido ver | pedido confirmar | pedido cancelar
                  publicar <TIPO> <mensagem>     envia NOTIFICACAO, ALERTA ou ATUALIZACAO ao grupo
                  membros | entrar | deixar | status
                  demo-valor [id] | demo-referencia
                  protocolo on|off               mostra as mensagens JSON trocadas
                  ajuda | sair""");
    }
}
