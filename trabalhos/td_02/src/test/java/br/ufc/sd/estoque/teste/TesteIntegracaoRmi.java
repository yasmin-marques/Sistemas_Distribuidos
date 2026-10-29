package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Apoio.erro;
import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.lanca;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.api.EstoqueRemoto;
import br.ufc.sd.estoque.api.NotificacoesRemotas;
import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.cliente.AssinaturaProxy;
import br.ufc.sd.estoque.cliente.ConexaoClinica;
import br.ufc.sd.estoque.cliente.ProxyBase;
import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.Insumo;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.Medicamento;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.modelo.StatusPedido;
import br.ufc.sd.estoque.modelo.TipoMovimentacao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import br.ufc.sd.estoque.modelo.Vacina;
import br.ufc.sd.estoque.protocolo.CargaResposta;
import br.ufc.sd.estoque.protocolo.CodecMensagem;
import br.ufc.sd.estoque.protocolo.CodigoErro;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.FalhaComunicacaoException;
import br.ufc.sd.estoque.protocolo.Mensagem;
import br.ufc.sd.estoque.protocolo.PortaServidora;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import br.ufc.sd.estoque.protocolo.TipoMensagem;
import br.ufc.sd.estoque.servidor.ServidorEstoque;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes de ponta a ponta: servidor real (registro RMI + porta servidora + despachante) e
 * clientes usando os proxies, tudo passando pelo Java RMI na interface de loopback.
 */
final class TesteIntegracaoRmi {

    private static final Map<String, String> SENHAS = Map.of(
            "veterinario", "vet123", "recepcao", "rec123", "estoquista", "est123");

    private TesteIntegracaoRmi() { }

    private static ConexaoClinica conexao(ServidorEstoque s) {
        return new ConexaoClinica("localhost", s.configuracao().porta());
    }

    private static String login(ConexaoClinica c, String usuario) {
        return c.autenticacao().autenticar(usuario, SENHAS.get(usuario)).getToken();
    }

    static void executar(Teste t) {
        inicializacao(t);
        protocolo(t);
        metodosRemotos(t);
        valorEReferencia(t);
        notificacoes(t);
        concorrencia(t);
    }

    // ================================================================== inicialização e registro

    private static void inicializacao(Teste t) {
        t.secao("Servidor RMI: inicialização, registro e localização");

        t.caso("Servidor inicia, registra a porta servidora no registro RMI e o cliente a localiza", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Registry reg = LocateRegistry.getRegistry("localhost", s.configuracao().porta());
                verdadeiro(Arrays.asList(reg.list()).contains(PortaServidora.NOME_REGISTRO),
                        "registro deve listar " + PortaServidora.NOME_REGISTRO);
                Object stub = reg.lookup(PortaServidora.NOME_REGISTRO);
                verdadeiro(stub instanceof PortaServidora, "lookup devolve a interface remota");
                verdadeiro(Proxy.isProxyClass(stub.getClass()), "é um stub dinâmico do RMI: " + stub.getClass());
                igual(List.of("Autenticacao", "Estoque", "Notificacoes"), s.tabela().nomes(), "objetos de serviço");
            }
        });

        t.caso("Servidor encerrado: cliente recebe FalhaComunicacaoException (não trava)", () -> {
            ServidorEstoque s = Apoio.servidor();
            ConexaoClinica c = conexao(s);
            igual(4, c.estoque().listarFornecedores().size(), "chamada com o servidor no ar");
            s.close();
            lanca(FalhaComunicacaoException.class, () -> c.estoque().listarFornecedores());
        });

        t.caso("Servidor reiniciado na mesma porta: o cliente localiza a nova porta servidora sozinho", () -> {
            ServidorEstoque s1 = Apoio.servidor();
            int porta = s1.configuracao().porta();
            ConexaoClinica c = conexao(s1);
            igual(9, c.estoque().listarItens().size(), "antes do reinício");
            s1.close();
            try (ServidorEstoque s2 = ServidorEstoque.iniciar(s1.configuracao().comPorta(porta))) {
                igual(9, c.estoque().listarItens().size(), "depois do reinício (stub antigo descartado)");
                igual(porta, s2.configuracao().porta(), "mesma porta");
            }
        });
    }

    // ================================================================== protocolo sobre RMI

    private static void protocolo(Teste t) {
        t.secao("Protocolo requisição-resposta sobre RMI");

        t.caso("doOperation: cada requisição tem requestId novo e a resposta traz o mesmo id", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                List<String> trafego = new CopyOnWriteArrayList<>();
                c.protocolo().setObservador(trafego::add);
                c.estoque().listarFornecedores();
                c.estoque().buscarItem(1);
                igual(4, trafego.size(), "2 requisições e 2 respostas");
                Mensagem req1 = msg(trafego.get(0)), rep1 = msg(trafego.get(1));
                Mensagem req2 = msg(trafego.get(2)), rep2 = msg(trafego.get(3));
                igual(TipoMensagem.REQUEST, req1.getMessageType(), "tipo da requisição");
                igual(TipoMensagem.REPLY, rep1.getMessageType(), "tipo da resposta");
                igual(req1.getRequestId(), rep1.getRequestId(), "id da 1ª resposta");
                igual(req2.getRequestId(), rep2.getRequestId(), "id da 2ª resposta");
                igual(req1.getRequestId() + 1, req2.getRequestId(), "ids crescentes");
                igual("buscarItem", req2.getMethodId(), "methodId como String");
                igual("Estoque", req2.getObjectReference().objeto(), "objectReference como String");
            }
        });

        t.caso("Bytes malformados enviados direto à porta servidora: resposta REQUISICAO_INVALIDA", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                PortaServidora porta = (PortaServidora) LocateRegistry.getRegistry("localhost", s.configuracao().porta())
                        .lookup(PortaServidora.NOME_REGISTRO);
                Mensagem resposta = CodecMensagem.desempacotar(porta.entregar("lixo{".getBytes(StandardCharsets.UTF_8)));
                igual(TipoMensagem.REPLY, resposta.getMessageType(), "é uma resposta");
                igual(-1, resposta.getRequestId(), "sem requestId legível");
                igual(CodigoErro.REQUISICAO_INVALIDA, codigo(resposta), "código");
                // uma mensagem do tipo Reply enviada como se fosse requisição
                RemoteObjectRef ref = conexao(s).referencia("Estoque", EstoqueRemoto.class);
                byte[] reply = CodecMensagem.empacotar(new Mensagem(TipoMensagem.REPLY, 5, ref, "listarItens",
                        Json.escreverBytes(Map.of())));
                Mensagem r2 = CodecMensagem.desempacotar(porta.entregar(reply));
                igual(CodigoErro.REQUISICAO_INVALIDA, codigo(r2), "Reply não é aceito como requisição");
                igual(5, r2.getRequestId(), "id ecoado");
            }
        });

        t.caso("Objeto inexistente, método inexistente, interface errada e argumentos inválidos", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                erro(CodigoErro.OBJETO_INEXISTENTE, () -> chamar(c, c.referencia("Farmacia", EstoqueRemoto.class),
                        "listarItens", Map.of()));
                erro(CodigoErro.METODO_INEXISTENTE, () -> chamar(c, c.referencia("Estoque", EstoqueRemoto.class),
                        "apagarTudo", Map.of()));
                erro(CodigoErro.REQUISICAO_INVALIDA, () -> chamar(c, c.referencia("Estoque", NotificacoesRemotas.class),
                        "membros", Map.of()));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> chamar(c, c.referencia("Estoque", EstoqueRemoto.class),
                        "buscarItem", Map.of("idItem", "três")));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> chamar(c, c.referencia("Estoque", EstoqueRemoto.class),
                        "buscarItem", Map.of()));
                // o servidor continua atendendo normalmente depois dos erros
                igual(9, c.estoque().listarItens().size(), "servidor segue funcionando");
            }
        });
    }

    private static Mensagem msg(String linhaDoObservador) {
        return CodecMensagem.desempacotar(linhaDoObservador.substring(4).getBytes(StandardCharsets.UTF_8));
    }

    private static CodigoErro codigo(Mensagem resposta) {
        try {
            CargaResposta.ler(resposta.getArguments());
            throw new AssertionError("Esperava resposta de erro.");
        } catch (ExcecaoRemota e) {
            return e.getCodigo();
        }
    }

    private static Object chamar(ConexaoClinica c, RemoteObjectRef ref, String metodo, Map<String, Object> args) {
        return CargaResposta.ler(c.protocolo().doOperation(ref, metodo, Json.escreverBytes(args)));
    }

    // ================================================================== métodos remotos

    private static void metodosRemotos(Teste t) {
        t.secao("Métodos remotos");

        t.caso("autenticar: aceita credenciais certas, recusa erradas e exige token nas alterações", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                verdadeiro(!login(c, "veterinario").isBlank(), "token gerado");
                erro(CodigoErro.NAO_AUTENTICADO, () -> c.autenticacao().autenticar("veterinario", "errada"));
                erro(CodigoErro.NAO_AUTENTICADO, () -> c.autenticacao().autenticar("intruso", "x"));
                erro(CodigoErro.NAO_AUTENTICADO, () -> c.estoque().registrarSaida("token-falso", 1, 1, null));
                String token = login(c, "recepcao");
                c.autenticacao().encerrarSessao(token);
                erro(CodigoErro.NAO_AUTENTICADO, () -> c.estoque().registrarEntrada(token, 1, 1));
            }
        });

        t.caso("listarItens, buscarItem e listarFornecedores devolvem os dados do servidor", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                EstoqueRemoto e = conexao(s).estoque();
                List<ItemEstoque> itens = e.listarItens();
                igual(9, itens.size(), "itens");
                verdadeiro(itens.get(0) instanceof Medicamento && itens.get(4) instanceof Vacina
                        && itens.get(6) instanceof Insumo, "subclasses corretas");
                igual("Antirrábica", e.buscarItem(5).getNome(), "buscarItem");
                igual("VetPharma", e.buscarItem(1).getFornecedor().getNome(), "fornecedor agregado");
                igual(4, e.listarFornecedores().size(), "fornecedores");
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.buscarItem(999));
            }
        });

        t.caso("cadastrarItem: aceita as três subclasses e valida as regras de cada uma", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "estoquista");
                EstoqueRemoto e = c.estoque();
                Fornecedor f2 = new Fornecedor(2, null, null, null);
                ItemEstoque med = e.cadastrarItem(tk, new Medicamento(0, "Dipirona 500mg", f2, 30, 5, 9.9,
                        LocalDate.of(2031, 1, 1), "Dipirona", "500 mg", false));
                ItemEstoque vac = e.cadastrarItem(tk, new Vacina(0, "Giárdia", f2, 10, 2, 60, LocalDate.of(2031, 1, 1), "Giardíase", 1));
                ItemEstoque ins = e.cadastrarItem(tk, new Insumo(0, "Gaze estéril", f2, 100, 20, 0.5, null, "pacote", true));
                igual(List.of(10, 11, 12), List.of(med.getId(), vac.getId(), ins.getId()), "ids gerados no servidor");
                igual("PetSaude", ins.getFornecedor().getNome(), "servidor agrega o fornecedor cadastrado");
                igual(12, e.listarItens().size(), "estoque cresceu");
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.cadastrarItem(tk, new Insumo(0, "X", new Fornecedor(77, null, null, null),
                        1, 0, 1, null, "un", false)));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.cadastrarItem(tk, new Medicamento(0, "Sem validade", f2, 1, 0, 1,
                        null, "Algo", "1 mg", false)));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.cadastrarItem(tk, new Vacina(0, "Vencida", f2, 1, 0, 1,
                        LocalDate.of(2020, 1, 1), "Raiva", 1)));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.cadastrarItem(tk, new Insumo(0, "Negativo", f2, -5, 0, 1,
                        null, "un", false)));
                erro(CodigoErro.OPERACAO_NEGADA, () -> e.cadastrarItem(tk, new Insumo(0, "gaze ESTÉRIL", f2, 1, 0, 1,
                        null, "un", false)));
            }
        });

        t.caso("registrarEntrada, registrarSaida e listarMovimentacoes com as regras de negócio", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "veterinario");
                EstoqueRemoto e = c.estoque();
                Movimentacao saida = e.registrarSaida(tk, 1, 8, "consulta");
                igual(TipoMovimentacao.SAIDA, saida.getTipo(), "tipo");
                igual(42, saida.getSaldoApos(), "saldo após a saída");
                igual("veterinario", saida.getResponsavel(), "responsável vem da sessão");
                Movimentacao entrada = e.registrarEntrada(tk, 1, 3);
                igual(45, entrada.getSaldoApos(), "saldo após a entrada");
                igual(45, e.buscarItem(1).getQuantidade(), "saldo no servidor");
                igual(2, e.listarMovimentacoes(1).size(), "histórico do item 1");
                igual(0, e.listarMovimentacoes(2).size(), "histórico do item 2");
                erro(CodigoErro.OPERACAO_NEGADA, () -> e.registrarSaida(tk, 3, 6, "x"));     // só há 5
                erro(CodigoErro.OPERACAO_NEGADA, () -> e.registrarSaida(tk, 9, 1, " "));     // controlado sem motivo
                igual(24, e.registrarSaida(tk, 9, 1, "analgesia").getSaldoApos(), "controlado com motivo");
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.registrarSaida(tk, 1, -2, null));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.registrarEntrada(tk, 1, 0));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.registrarEntrada(tk, 404, 1));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.listarMovimentacoes(-1));
            }
        });

        t.caso("Pedido de reposição: abrir, adicionar, confirmar (entrada em lote) e cancelar", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "estoquista");
                EstoqueRemoto e = c.estoque();
                PedidoReposicaoRemoto p = e.abrirPedidoReposicao(tk, 4); // MedCirurgica
                p.adicionarItem(tk, 7, 100);
                p.adicionarItem(tk, 8, 10);
                p.adicionarItem(tk, 7, 50);                               // soma com a linha existente
                igual(2, p.consultar().getItens().size(), "duas linhas");
                igual(160, p.consultar().totalUnidades(), "total de unidades");
                erro(CodigoErro.OPERACAO_NEGADA, () -> p.adicionarItem(tk, 1, 5)); // item de outro fornecedor
                List<Movimentacao> movs = p.confirmarRecebimento(tk);
                igual(2, movs.size(), "uma entrada por linha");
                igual(350, e.buscarItem(7).getQuantidade(), "seringas após o recebimento");
                igual(22, e.buscarItem(8).getQuantidade(), "luvas após o recebimento");
                igual(StatusPedido.RECEBIDO, p.consultar().getStatus(), "status");
                erro(CodigoErro.OPERACAO_NEGADA, () -> p.confirmarRecebimento(tk));
                PedidoReposicaoRemoto vazio = e.abrirPedidoReposicao(tk, 1);
                erro(CodigoErro.OPERACAO_NEGADA, () -> vazio.confirmarRecebimento(tk));
                igual(StatusPedido.CANCELADO, vazio.cancelar(tk).getStatus(), "cancelado");
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> e.abrirPedidoReposicao(tk, 99));
            }
        });
    }

    // ================================================================== valor x referência

    private static void valorEReferencia(Teste t) {
        t.secao("Passagem por valor e por referência");

        t.caso("Por valor (resultado): alterar a cópia recebida não altera o objeto do servidor", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                EstoqueRemoto e = conexao(s).estoque();
                ItemEstoque copia = e.buscarItem(1);
                copia.setQuantidade(99_999);
                copia.setNome("alterado no cliente");
                copia.getFornecedor().setNome("fornecedor alterado");
                ItemEstoque doServidor = e.buscarItem(1);
                igual(50, doServidor.getQuantidade(), "quantidade no servidor");
                igual("Amoxicilina 250mg", doServidor.getNome(), "nome no servidor");
                igual("VetPharma", doServidor.getFornecedor().getNome(), "fornecedor no servidor");
                verdadeiro(copia != e.buscarItem(1), "cada chamada devolve uma nova cópia");
            }
        });

        t.caso("Por valor (argumento): o servidor executa sobre a cópia do objeto local enviado", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "estoquista");
                Insumo local = new Insumo(0, "Atadura", new Fornecedor(4, "nome do cliente", null, null), 20, 5,
                        3.5, null, "rolo", false);
                ItemEstoque cadastrado = c.estoque().cadastrarItem(tk, local);
                igual(0, local.getId(), "objeto local não ganhou id");
                igual("nome do cliente", local.getFornecedor().getNome(), "objeto local intacto");
                local.setQuantidade(1);
                igual(20, c.estoque().buscarItem(cadastrado.getId()).getQuantidade(),
                        "mudar o objeto local depois do envio não afeta o servidor");
            }
        });

        t.caso("Por referência (resultado): duas referências ao mesmo pedido enxergam o mesmo estado", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c1 = conexao(s);
                ConexaoClinica c2 = conexao(s);                 // outro "cliente"
                String tk1 = login(c1, "estoquista");
                String tk2 = login(c2, "veterinario");
                PedidoReposicaoRemoto p1 = c1.estoque().abrirPedidoReposicao(tk1, 1);
                RemoteObjectRef ref = ((ProxyBase) p1).referencia();
                verdadeiro(ref.objeto().startsWith("PedidoReposicao-"), "nome do objeto: " + ref.objeto());
                igual("PedidoReposicaoRemoto", ref.interfaceRemota(), "interface da referência");
                verdadeiro(s.tabela().nomes().contains(ref.objeto()), "o pedido está na tabela do servidor");
                PedidoReposicaoRemoto p2 = c2.pedido(ref);
                p2.adicionarItem(tk2, 1, 7);                     // altera pelo cliente 2
                igual(7, p1.consultar().totalUnidades(), "cliente 1 vê a alteração do cliente 2");
                p1.cancelar(tk1);
                igual(StatusPedido.CANCELADO, p2.consultar().getStatus(), "cliente 2 vê o cancelamento");
            }
        });

        t.caso("Por referência (argumento): sairGrupo recebe a referência e o servidor a resolve no objeto real", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "recepcao");
                AssinaturaRemota a = c.notificacoes().entrarGrupo(tk);
                String nome = ((ProxyBase) a).referencia().objeto();
                igual("recepcao", a.usuario(), "método invocado pela referência");
                igual(1, s.central().quantidadeMembros(), "membro no servidor");
                c.notificacoes().sairGrupo(a);
                igual(0, s.central().quantidadeMembros(), "saiu do grupo");
                verdadeiro(!s.tabela().nomes().contains(nome), "a assinatura saiu da tabela (fim do ciclo de vida)");
                erro(CodigoErro.OBJETO_INEXISTENTE, () -> a.aguardar(0));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> c.notificacoes().sairGrupo(a));
            }
        });

        t.caso("Referências forjadas ou do tipo errado são recusadas", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "estoquista");
                AssinaturaRemota forjada = new AssinaturaProxy(c.protocolo(),
                        c.referencia("Assinatura-00000000", AssinaturaRemota.class));
                erro(CodigoErro.OBJETO_INEXISTENTE, () -> forjada.aguardar(0));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> c.notificacoes().sairGrupo(forjada));
                PedidoReposicaoRemoto p = c.estoque().abrirPedidoReposicao(tk, 1);
                RemoteObjectRef refPedido = ((ProxyBase) p).referencia();
                Map<String, Object> args = Map.of("assinatura", refPedido.paraJson());
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> chamar(c, c.referencia("Notificacoes", NotificacoesRemotas.class),
                        "sairGrupo", args));
            }
        });
    }

    // ================================================================== notificações

    private static void notificacoes(Teste t) {
        t.secao("Notificações (substituto do multicast do TD01)");

        t.caso("Dois clientes no grupo recebem NOTIFICACAO, ALERTA e ATUALIZACAO", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c1 = conexao(s), c2 = conexao(s);
                AssinaturaRemota a1 = c1.notificacoes().entrarGrupo(login(c1, "veterinario"));
                AssinaturaRemota a2 = c2.notificacoes().entrarGrupo(login(c2, "recepcao"));
                String tk = login(c1, "estoquista");
                igual(List.of("recepcao", "veterinario"), c1.notificacoes().membros(), "membros");
                for (TipoNotificacao tipo : TipoNotificacao.values()) {
                    c1.notificacoes().publicar(tk, tipo, "teste " + tipo);
                }
                for (AssinaturaRemota a : List.of(a1, a2)) {
                    Apoio.Caixa caixa = new Apoio.Caixa(a);
                    for (TipoNotificacao tipo : TipoNotificacao.values()) {
                        Notificacao n = caixa.esperar(x -> x.getMensagem().equals("estoquista: teste " + tipo));
                        igual(tipo, n.getTipo(), "tipo recebido");
                    }
                }
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> c1.notificacoes().publicar(tk, TipoNotificacao.ALERTA, "  "));
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> chamar(c1, c1.referencia("Notificacoes", NotificacoesRemotas.class),
                        "publicar", Map.of("token", tk, "tipo", "SPAM", "mensagem", "x")));
            }
        });

        t.caso("leaveGroup: quem sai não recebe mais; ao entrar de novo volta a receber", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c1 = conexao(s), c2 = conexao(s);
                String tk1 = login(c1, "veterinario"), tk2 = login(c2, "recepcao");
                AssinaturaRemota a1 = c1.notificacoes().entrarGrupo(tk1);
                AssinaturaRemota a2 = c2.notificacoes().entrarGrupo(tk2);
                c2.notificacoes().sairGrupo(a2);
                c1.notificacoes().publicar(tk1, TipoNotificacao.NOTIFICACAO, "só para quem ficou");
                new Apoio.Caixa(a1).esperar(n -> n.getMensagem().endsWith("só para quem ficou"));
                igual(List.of("veterinario"), c1.notificacoes().membros(), "membros após a saída");
                AssinaturaRemota a2b = c2.notificacoes().entrarGrupo(tk2);
                c1.notificacoes().publicar(tk1, TipoNotificacao.NOTIFICACAO, "de volta");
                List<Notificacao> chegaram = new Apoio.Caixa(a2b).coletar(500);
                igual(1, chegaram.size(), "só a mensagem enviada depois de voltar: " + chegaram);
                verdadeiro(chegaram.get(0).getMensagem().endsWith("de volta"), "mensagem recebida");
            }
        });

        t.caso("Operações de estoque geram ATUALIZACAO e ALERTA de estoque baixo para todos", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c1 = conexao(s), c2 = conexao(s);
                AssinaturaRemota a2 = c2.notificacoes().entrarGrupo(login(c2, "recepcao"));
                String tk = login(c1, "estoquista");
                c1.estoque().registrarSaida(tk, 2, 21, "campanha de vermifugação"); // 30 -> 9 (mínimo 10)
                Apoio.Caixa caixa = new Apoio.Caixa(a2);
                caixa.esperar(n -> n.getTipo() == TipoNotificacao.ATUALIZACAO && n.getMensagem().contains("Saldo: 9"));
                caixa.esperar(n -> n.getTipo() == TipoNotificacao.ALERTA && n.getMensagem().contains("Estoque baixo")
                        && n.getMensagem().contains("Vermífugo"));
            }
        });

        t.caso("Entrega em tempo real: aguardar retorna assim que a mensagem é publicada", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "veterinario");
                AssinaturaRemota a = c.notificacoes().entrarGrupo(tk);
                ExecutorService ex = Executors.newSingleThreadExecutor();
                try {
                    long inicio = System.nanoTime();
                    Future<List<Notificacao>> espera = ex.submit(() -> a.aguardar(20_000));
                    Thread.sleep(300);
                    s.central().publicar(TipoNotificacao.ALERTA, "urgente");
                    List<Notificacao> ns = espera.get(5, TimeUnit.SECONDS);
                    long ms = (System.nanoTime() - inicio) / 1_000_000;
                    igual("urgente", ns.get(0).getMensagem(), "mensagem recebida");
                    verdadeiro(ms < 3_000, "chegou em " + ms + " ms (a espera máxima era 20 s)");
                } finally {
                    ex.shutdownNow();
                }
                erro(CodigoErro.ARGUMENTO_INVALIDO, () -> a.aguardar(AssinaturaRemota.ESPERA_MAXIMA_MS + 1));
            }
        });

        t.caso("Monitor: cada alerta é enviado uma vez e o vencimento é detectado com o passar dos dias", () -> {
            Apoio.RelogioAjustavel relogio = new Apoio.RelogioAjustavel();
            try (ServidorEstoque s = Apoio.servidor(relogio, 30_000)) {
                ConexaoClinica c = conexao(s);
                String tk = login(c, "estoquista");
                AssinaturaRemota a = c.notificacoes().entrarGrupo(tk);
                s.monitor().verificarTodos();
                List<Notificacao> primeira = a.aguardar(1_000);
                igual(3, primeira.size(), "alertas iniciais (estoque baixo, pomada e V10): " + primeira);
                s.monitor().verificarTodos();
                igual(0, a.aguardar(300).size(), "nenhum alerta repetido");
                relogio.avancarDias(4);                          // a pomada vence
                s.monitor().verificarTodos();
                Apoio.Caixa caixa = new Apoio.Caixa(a);
                caixa.esperar(n -> n.getMensagem().startsWith("Item vencido") && n.getMensagem().contains("Pomada"));
                erro(CodigoErro.OPERACAO_NEGADA, () -> c.estoque().registrarSaida(tk, 4, 1, "curativo"));
                c.estoque().registrarEntrada(tk, 3, 20);         // anti-inflamatório volta ao normal
                c.estoque().registrarSaida(tk, 3, 16, "uso");    // e fica baixo de novo: novo alerta
                caixa.esperar(n -> n.getTipo() == TipoNotificacao.ALERTA && n.getMensagem().contains("Anti-inflamatório"));
            }
        });

        t.caso("Lease: assinatura abandonada pelo cliente é encerrada e retirada da tabela", () -> {
            try (ServidorEstoque s = Apoio.servidor(java.time.Clock.systemDefaultZone(), 400)) {
                ConexaoClinica c = conexao(s);
                AssinaturaRemota a = c.notificacoes().entrarGrupo(login(c, "recepcao"));
                igual(1, s.central().quantidadeMembros(), "membro ativo");
                long limite = System.currentTimeMillis() + 5_000;
                while (s.central().quantidadeMembros() > 0 && System.currentTimeMillis() < limite) Thread.sleep(50);
                igual(0, s.central().quantidadeMembros(), "assinatura expirada");
                erro(CodigoErro.OBJETO_INEXISTENTE, () -> a.aguardar(0));
                igual(3, s.tabela().tamanho(), "só os objetos de serviço restam na tabela");
            }
        });
    }

    // ================================================================== concorrência

    private static void concorrencia(Teste t) {
        t.secao("Vários clientes e concorrência");

        t.caso("30 clientes disputam 12 caixas de luvas: exatamente 12 saídas, saldo nunca negativo", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                int clientes = 30;
                ExecutorService ex = Executors.newFixedThreadPool(clientes);
                CountDownLatch largada = new CountDownLatch(1);
                AtomicInteger aceitas = new AtomicInteger(), negadas = new AtomicInteger();
                List<Future<?>> fs = new ArrayList<>();
                try {
                    for (int i = 0; i < clientes; i++) {
                        ConexaoClinica c = conexao(s);
                        String tk = login(c, i % 2 == 0 ? "veterinario" : "estoquista");
                        fs.add(ex.submit(() -> {
                            largada.await();
                            try {
                                c.estoque().registrarSaida(tk, 8, 1, "procedimento");
                                aceitas.incrementAndGet();
                            } catch (ExcecaoRemota e) {
                                if (e.getCodigo() != CodigoErro.OPERACAO_NEGADA) throw e;
                                negadas.incrementAndGet();
                            }
                            return null;
                        }));
                    }
                    largada.countDown();
                    for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
                } finally {
                    ex.shutdownNow();
                }
                igual(12, aceitas.get(), "saídas aceitas");
                igual(18, negadas.get(), "saídas negadas");
                igual(0, s.estoque().buscarItem(8).getQuantidade(), "saldo final");
                igual(12, s.estoque().listarMovimentacoes(8).size(), "uma movimentação por saída aceita");
            }
        });

        t.caso("8 clientes fazendo entradas e saídas simultâneas: saldo final bate com o histórico", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                int clientes = 8, operacoes = 25;
                ExecutorService ex = Executors.newFixedThreadPool(clientes);
                List<Callable<Void>> tarefas = new ArrayList<>();
                for (int i = 0; i < clientes; i++) {
                    ConexaoClinica c = conexao(s);
                    String tk = login(c, "estoquista");
                    tarefas.add(() -> {
                        for (int k = 0; k < operacoes; k++) {
                            if (k % 2 == 0) c.estoque().registrarEntrada(tk, 7, 2);
                            else c.estoque().registrarSaida(tk, 7, 3, "uso");
                        }
                        return null;
                    });
                }
                try {
                    for (Future<Void> f : ex.invokeAll(tarefas, 60, TimeUnit.SECONDS)) f.get();
                } finally {
                    ex.shutdownNow();
                }
                int entradas = clientes * 13 * 2, saidas = clientes * 12 * 3;
                igual(200 + entradas - saidas, s.estoque().buscarItem(7).getQuantidade(), "saldo final");
                List<Movimentacao> hist = s.estoque().listarMovimentacoes(7);
                igual(clientes * operacoes, hist.size(), "movimentações registradas");
                int saldo = 200;
                List<Movimentacao> ordenado = new ArrayList<>(hist);
                ordenado.sort((x, y) -> Integer.compare(x.getId(), y.getId()));
                for (Movimentacao m : ordenado) {
                    saldo += m.getTipo() == TipoMovimentacao.ENTRADA ? m.getQuantidade() : -m.getQuantidade();
                    igual(saldo, m.getSaldoApos(), "saldo registrado na movimentação #" + m.getId());
                }
            }
        });

        t.caso("10 membros e 5 publicadores simultâneos: todos recebem as 50 mensagens na mesma ordem", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                int membros = 10, publicadores = 5, porPublicador = 10, total = publicadores * porPublicador;
                List<AssinaturaRemota> assinaturas = new ArrayList<>();
                for (int i = 0; i < membros; i++) {
                    ConexaoClinica c = conexao(s);
                    assinaturas.add(c.notificacoes().entrarGrupo(login(c, "recepcao")));
                }
                ExecutorService ex = Executors.newFixedThreadPool(membros + publicadores);
                try {
                    List<Future<List<Long>>> recepcoes = new ArrayList<>();
                    for (AssinaturaRemota a : assinaturas) {
                        recepcoes.add(ex.submit(() -> {
                            List<Long> ids = new ArrayList<>();
                            long limite = System.currentTimeMillis() + 20_000;
                            while (ids.size() < total && System.currentTimeMillis() < limite) {
                                for (Notificacao n : a.aguardar(2_000)) ids.add(n.getId());
                            }
                            return ids;
                        }));
                    }
                    List<Future<?>> envios = new ArrayList<>();
                    for (int p = 0; p < publicadores; p++) {
                        ConexaoClinica c = conexao(s);
                        String tk = login(c, "estoquista");
                        int numero = p;
                        envios.add(ex.submit(() -> {
                            for (int k = 0; k < porPublicador; k++) {
                                c.notificacoes().publicar(tk, TipoNotificacao.NOTIFICACAO, "p" + numero + "-m" + k);
                            }
                            return null;
                        }));
                    }
                    for (Future<?> f : envios) f.get(30, TimeUnit.SECONDS);
                    List<Long> referencia = null;
                    for (Future<List<Long>> f : recepcoes) {
                        List<Long> ids = f.get(30, TimeUnit.SECONDS);
                        igual(total, ids.size(), "mensagens recebidas por um membro");
                        igual(total, new java.util.HashSet<>(ids).size(), "sem duplicatas");
                        if (referencia == null) referencia = ids;
                        igual(referencia, ids, "mesma ordem em todos os membros");
                    }
                    List<Long> ordenada = new ArrayList<>(referencia);
                    Collections.sort(ordenada);
                    igual(ordenada, referencia, "ordem total (sequência crescente)");
                } finally {
                    ex.shutdownNow();
                }
            }
        });
    }
}
