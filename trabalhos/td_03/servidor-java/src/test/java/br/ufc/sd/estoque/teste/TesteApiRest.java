package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.lanca;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

import br.ufc.sd.estoque.api.PedidoReposicaoRemoto;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.Insumo;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.Medicamento;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import br.ufc.sd.estoque.modelo.Vacina;
import br.ufc.sd.estoque.servidor.ServidorEstoque;
import br.ufc.sd.estoque.teste.Apoio.Api;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Testes de integração da API REST: sobem um servidor real e o chamam por HTTP, pelo mesmo
 * contrato que os clientes Python e JavaScript usam.
 */
final class TesteApiRest {

    private TesteApiRest() { }

    static void executar(Teste t) {
        servidorEProtocolo(t);
        metodosRemotos(t);
        valorEReferencia(t);
        notificacoes(t);
        concorrencia(t);
    }

    private static Map<String, Object> corpo(Object... paresChaveValor) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) m.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        return m;
    }

    // ================================================================== servidor e protocolo

    private static void servidorEProtocolo(Teste t) {
        t.secao("Servidor HTTP: inicialização e protocolo requisição-resposta");

        t.caso("O servidor sobe e descreve os objetos distribuídos (pelo menos 3) com o endereço de cada um", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s);
                ObjetoJson indice = a.exigir(200, a.get("/api")).objeto();
                List<Object> objetos = indice.lista("objetos");
                verdadeiro(objetos.size() >= 3, "objetos distribuídos: " + objetos);
                List<String> nomes = new ArrayList<>();
                for (Object o : objetos) {
                    ObjetoJson ref = ObjetoJson.de(o);
                    nomes.add(ref.texto("objeto"));
                    verdadeiro(ref.texto("href").startsWith(a.base + "/api/"), "href aponta para o servidor: " + ref);
                    ObjetoJson descricao = a.exigir(200, a.get(ref.texto("href"))).objeto(); // o próprio endereço se descreve
                    igual(ref.texto("interface"), descricao.texto("interface"), "interface do objeto");
                    verdadeiro(descricao.lista("operacoes").size() >= 2, "operações listadas: " + descricao);
                }
                igual(List.of("Autenticacao", "Estoque", "Notificacoes"), nomes, "objetos de serviço");
            }
        });

        t.caso("Servidor encerrado: o cliente recebe erro de conexão (não trava)", () -> {
            Api a;
            try (ServidorEstoque s = Apoio.servidor()) {
                a = new Api(s);
                a.exigir(200, a.get("/api"));
            }
            lanca(UncheckedIOException.class, () -> a.get("/api"));
        });

        t.caso("Cada resposta traz X-Request-Id e o status HTTP que corresponde ao resultado", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s);
                Api.Resultado r1 = a.get("/api/estoque/itens/1");
                Api.Resultado r2 = a.get("/api/estoque/itens/1");
                verdadeiro(r1.cabecalho("X-Request-Id") != null, "X-Request-Id presente");
                verdadeiro(!r1.cabecalho("X-Request-Id").equals(r2.cabecalho("X-Request-Id")), "ids diferentes");
                verdadeiro(r1.cabecalho("Content-Type").startsWith("application/json"), "corpo é JSON");
            }
        });

        t.caso("CORS: o navegador pode chamar a API de outra origem (pré-voo OPTIONS e cabeçalhos)", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s);
                Api.Resultado pre = a.exigir(204, a.chamar("OPTIONS", "/api/estoque/itens", null, null));
                verdadeiro(pre.cabecalho("Access-Control-Allow-Headers").contains("Authorization"), "permite Authorization");
                verdadeiro(pre.cabecalho("Access-Control-Allow-Methods").contains("POST"), "permite POST");
                igual("*", a.get("/api/estoque/itens").cabecalho("Access-Control-Allow-Origin"), "origem liberada");
            }
        });

        t.caso("Requisições inválidas: JSON malformado, caminho/método inexistente, argumento ausente ou corpo enorme", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("estoquista");
                a.erro(400, "REQUISICAO_INVALIDA", a.chamar("POST", "/api/autenticacao/sessoes",
                        "{xx".getBytes(StandardCharsets.UTF_8), null));
                a.erro(400, "REQUISICAO_INVALIDA", a.chamar("POST", "/api/autenticacao/sessoes",
                        "[1,2]".getBytes(StandardCharsets.UTF_8), null));
                a.erro(404, "OBJETO_INEXISTENTE", a.get("/api/nao-existe"));
                Api.Resultado metodo = a.erro(405, "METODO_INEXISTENTE", a.delete("/api/estoque/itens"));
                verdadeiro(metodo.cabecalho("Allow").contains("GET"), "Allow lista os métodos aceitos");
                a.erro(400, "ARGUMENTO_INVALIDO", a.get("/api/estoque/itens/abc"));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens/1/saidas", corpo()));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens/1/saidas", corpo("quantidade", "muitas")));
                a.erro(400, "REQUISICAO_INVALIDA", a.chamar("POST", "/api/estoque/itens/1/saidas",
                        new byte[1_048_577], a.token));
                a.exigir(200, a.get("/api")); // o servidor continua de pé
            }
        });
    }

    // ================================================================== métodos remotos

    private static void metodosRemotos(Teste t) {
        t.secao("Métodos remotos (operações dos objetos distribuídos)");

        t.caso("autenticar: aceita credenciais certas, recusa erradas e exige token nas alterações", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s);
                a.entrar("veterinario");
                verdadeiro(!a.token.isBlank(), "token gerado");
                a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", "/api/autenticacao/sessoes",
                        corpo("usuario", "veterinario", "senha", "errada"), null));
                a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", "/api/autenticacao/sessoes",
                        corpo("usuario", "intruso", "senha", "x"), null));
                a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", "/api/autenticacao/sessoes", corpo(), null));
                Api.Resultado semToken = a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", "/api/estoque/itens/1/saidas",
                        corpo("quantidade", 1), null));
                igual("Bearer", semToken.cabecalho("WWW-Authenticate"), "desafio de autenticação");
                a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", "/api/estoque/itens/1/saidas",
                        corpo("quantidade", 1), "token-falso"));
                Api b = new Api(s).entrar("recepcao");
                String token = b.token;
                a.exigir(204, b.delete("/api/autenticacao/sessoes/atual"));
                a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", "/api/estoque/itens/1/entradas",
                        corpo("quantidade", 1), token));
                a.erro(401, "NAO_AUTENTICADO", a.chamar("DELETE", "/api/autenticacao/sessoes/atual", null, null));
            }
        });

        t.caso("listarItens, buscarItem e listarFornecedores devolvem os dados do servidor", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s);
                List<Object> itens = a.exigir(200, a.get("/api/estoque/itens")).lista();
                igual(9, itens.size(), "itens");
                igual("MEDICAMENTO", ObjetoJson.de(itens.get(0)).texto("classe"), "primeiro é medicamento");
                igual("VACINA", ObjetoJson.de(itens.get(4)).texto("classe"), "quinto é vacina");
                igual("INSUMO", ObjetoJson.de(itens.get(6)).texto("classe"), "sétimo é insumo");
                igual("Antirrábica", a.exigir(200, a.get("/api/estoque/itens/5")).objeto().texto("nome"), "buscarItem");
                igual("VetPharma", a.get("/api/estoque/itens/1").objeto().objeto("fornecedor").texto("nome"),
                        "fornecedor agregado");
                igual(4, a.exigir(200, a.get("/api/estoque/fornecedores")).lista().size(), "fornecedores");
                a.erro(404, "OBJETO_INEXISTENTE", a.get("/api/estoque/itens/999"));
            }
        });

        t.caso("cadastrarItem: aceita as três subclasses e valida as regras de cada uma", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("estoquista");
                Fornecedor f2 = new Fornecedor(2, null, null, null);
                Api.Resultado med = a.exigir(201, a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Medicamento(0, "Dipirona 500mg", f2, 30, 5, 9.9, LocalDate.of(2031, 1, 1), "Dipirona", "500 mg", false))));
                Api.Resultado vac = a.exigir(201, a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Vacina(0, "Giárdia", f2, 10, 2, 60, LocalDate.of(2031, 1, 1), "Giardíase", 1))));
                Api.Resultado ins = a.exigir(201, a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Insumo(0, "Gaze estéril", f2, 100, 20, 0.5, null, "pacote", true))));
                igual(List.of(10L, 11L, 12L), List.of(med.objeto().longo("id"), vac.objeto().longo("id"),
                        ins.objeto().longo("id")), "ids gerados no servidor");
                igual(a.base + "/api/estoque/itens/12", ins.cabecalho("Location"), "Location do item criado");
                igual("PetSaude", ins.objeto().objeto("fornecedor").texto("nome"), "servidor agrega o fornecedor cadastrado");
                igual("INSUMO", a.get(ins.cabecalho("Location")).objeto().texto("classe"), "o item existe no servidor");
                igual(12, a.get("/api/estoque/itens").lista().size(), "estoque cresceu");
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Insumo(0, "X", new Fornecedor(77, null, null, null), 1, 0, 1, null, "un", false))));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Medicamento(0, "Sem validade", f2, 1, 0, 1, null, "Algo", "1 mg", false))));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Vacina(0, "Vencida", f2, 1, 0, 1, LocalDate.of(2020, 1, 1), "Raiva", 1))));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Insumo(0, "Negativo", f2, -5, 0, 1, null, "un", false))));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens", corpo("classe", "FERRAMENTA")));
                a.erro(409, "OPERACAO_NEGADA", a.post("/api/estoque/itens", CodecEntidades.itemParaJson(
                        new Insumo(0, "gaze ESTÉRIL", f2, 1, 0, 1, null, "un", false))));
            }
        });

        t.caso("registrarEntrada, registrarSaida e listarMovimentacoes com as regras de negócio", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("veterinario");
                ObjetoJson saida = a.exigir(201, a.post("/api/estoque/itens/1/saidas",
                        corpo("quantidade", 8, "motivo", "consulta"))).objeto();
                igual("SAIDA", saida.texto("tipo"), "tipo");
                igual(42, saida.inteiro("saldoApos"), "saldo após a saída");
                igual("veterinario", saida.texto("responsavel"), "responsável vem da sessão");
                igual(45, a.exigir(201, a.post("/api/estoque/itens/1/entradas", corpo("quantidade", 3)))
                        .objeto().inteiro("saldoApos"), "saldo após a entrada");
                igual(45, a.get("/api/estoque/itens/1").objeto().inteiro("quantidade"), "saldo no servidor");
                igual(2, a.get("/api/estoque/movimentacoes?item=1").lista().size(), "histórico do item 1");
                igual(0, a.get("/api/estoque/movimentacoes?item=2").lista().size(), "histórico do item 2");
                igual(2, a.get("/api/estoque/movimentacoes").lista().size(), "histórico geral");
                a.erro(409, "OPERACAO_NEGADA", a.post("/api/estoque/itens/3/saidas", corpo("quantidade", 6, "motivo", "x")));
                a.erro(409, "OPERACAO_NEGADA", a.post("/api/estoque/itens/9/saidas", corpo("quantidade", 1, "motivo", " ")));
                a.erro(409, "OPERACAO_NEGADA", a.post("/api/estoque/itens/9/saidas", corpo("quantidade", 1)));
                igual(24, a.exigir(201, a.post("/api/estoque/itens/9/saidas", corpo("quantidade", 1, "motivo", "analgesia")))
                        .objeto().inteiro("saldoApos"), "controlado com motivo");
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens/1/saidas", corpo("quantidade", -2)));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/itens/1/entradas", corpo("quantidade", 0)));
                a.erro(404, "OBJETO_INEXISTENTE", a.post("/api/estoque/itens/404/entradas", corpo("quantidade", 1)));
                a.erro(400, "ARGUMENTO_INVALIDO", a.get("/api/estoque/movimentacoes?item=-1"));
                a.erro(400, "ARGUMENTO_INVALIDO", a.get("/api/estoque/movimentacoes?item=x"));
            }
        });

        t.caso("Pedido de reposição: abrir, adicionar, confirmar (entrada em lote) e cancelar", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("estoquista");
                String pedido = a.exigir(201, a.post("/api/estoque/pedidos", corpo("idFornecedor", 4))) // MedCirurgica
                        .objeto().texto("href");
                a.exigir(200, a.post(pedido + "/itens", corpo("idItem", 7, "quantidade", 100)));
                a.exigir(200, a.post(pedido + "/itens", corpo("idItem", 8, "quantidade", 10)));
                ObjetoJson visto = a.exigir(200, a.post(pedido + "/itens", corpo("idItem", 7, "quantidade", 50))).objeto();
                igual(2, visto.lista("itens").size(), "duas linhas (a repetida soma)");
                a.erro(409, "OPERACAO_NEGADA", a.post(pedido + "/itens", corpo("idItem", 1, "quantidade", 5))); // outro fornecedor
                List<Object> movs = a.exigir(200, a.post(pedido + "/recebimento", null)).lista();
                igual(2, movs.size(), "uma entrada por linha");
                igual(350, a.get("/api/estoque/itens/7").objeto().inteiro("quantidade"), "seringas após o recebimento");
                igual(22, a.get("/api/estoque/itens/8").objeto().inteiro("quantidade"), "luvas após o recebimento");
                igual("RECEBIDO", a.get(pedido).objeto().texto("status"), "status");
                a.erro(409, "OPERACAO_NEGADA", a.post(pedido + "/recebimento", null));
                String vazio = a.post("/api/estoque/pedidos", corpo("idFornecedor", 1)).objeto().texto("href");
                a.erro(409, "OPERACAO_NEGADA", a.post(vazio + "/recebimento", null));
                igual("CANCELADO", a.exigir(200, a.post(vazio + "/cancelamento", null)).objeto().texto("status"), "cancelado");
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/estoque/pedidos", corpo("idFornecedor", 99)));
                a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", pedido + "/cancelamento", null, null));
            }
        });
    }

    // ================================================================== valor x referência

    private static void valorEReferencia(Teste t) {
        t.secao("Passagem por valor e por referência");

        t.caso("Por valor (resultado): alterar a cópia recebida não altera o objeto do servidor", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s);
                Map<String, Object> copia = a.get("/api/estoque/itens/1").objeto().mapa().entrySet().stream()
                        .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
                copia.put("quantidade", 99_999L);
                copia.put("nome", "ALTERADO SÓ NO CLIENTE");
                ItemEstoque doServidor = s.estoque().buscarItem(1);
                igual(50, doServidor.getQuantidade(), "quantidade no servidor");
                igual("Amoxicilina 250mg", doServidor.getNome(), "nome no servidor");
                igual(50, a.get("/api/estoque/itens/1").objeto().inteiro("quantidade"), "nova consulta");
            }
        });

        t.caso("Por valor (argumento): o servidor executa sobre a cópia do objeto local enviado", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("recepcao");
                Insumo local = new Insumo(0, "Gaze", new Fornecedor(4, null, null, null), 10, 2, 1.5, null, "pacote", false);
                ObjetoJson devolvido = a.exigir(201, a.post("/api/estoque/itens", CodecEntidades.itemParaJson(local))).objeto();
                igual(10L, devolvido.longo("id"), "o servidor gerou o id");
                igual(0, local.getId(), "o objeto local continua com id 0");
                ItemEstoque noServidor = s.estoque().buscarItem(10);
                verdadeiro(noServidor != local, "o servidor guarda outra instância");
                local.setQuantidade(777);
                igual(10, s.estoque().buscarItem(10).getQuantidade(), "alterar o objeto local depois não afeta o servidor");
                igual("MedCirurgica", noServidor.getFornecedor().getNome(), "fornecedor agregado no servidor");
            }
        });

        t.caso("Por referência (resultado): duas referências ao mesmo pedido enxergam o mesmo estado", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("estoquista"), b = new Api(s).entrar("veterinario");
                Api.Resultado r = a.exigir(201, a.post("/api/estoque/pedidos", corpo("idFornecedor", 1)));
                ObjetoJson ref = r.objeto();
                igual("PedidoReposicaoRemoto", ref.texto("interface"), "interface");
                igual(ref.texto("href"), r.cabecalho("Location"), "Location = href da referência");
                verdadeiro(ref.texto("objeto").startsWith("PedidoReposicao-"), "nome do objeto: " + ref.texto("objeto"));
                String href = ref.texto("href");
                a.exigir(200, a.post(href + "/itens", corpo("idItem", 1, "quantidade", 5)));
                igual(1, b.exigir(200, b.get(href)).objeto().lista("itens").size(), "b enxerga a alteração feita por a");
                igual("CANCELADO", b.exigir(200, b.post(href + "/cancelamento", null)).objeto().texto("status"), "b cancela");
                igual("CANCELADO", a.get(href).objeto().texto("status"), "a enxerga o cancelamento");
                // o objeto vive no servidor: a tabela guarda a mesma instância que atendeu as chamadas
                PedidoReposicaoRemoto servo = s.tabela().localizar(ref.texto("objeto"), PedidoReposicaoRemoto.class);
                igual(1, servo.consultar().getItens().size(), "estado no objeto do servidor");
                a.erro(409, "OPERACAO_NEGADA", a.post(href + "/itens", corpo("idItem", 1, "quantidade", 1)));
            }
        });

        t.caso("Por referência (argumento): sairGrupo recebe a referência e o servidor a resolve no objeto real", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("recepcao");
                Api.Resultado r = a.exigir(201, a.post("/api/notificacoes/assinaturas", null));
                ObjetoJson ref = r.objeto();
                igual("AssinaturaRemota", ref.texto("interface"), "interface");
                igual(List.of("recepcao"), a.get("/api/notificacoes/membros").lista(), "membro no grupo");
                int antes = s.tabela().tamanho();
                a.exigir(204, a.post("/api/notificacoes/saida", corpo("assinatura", ref.mapa())));
                igual(List.of(), a.get("/api/notificacoes/membros").lista(), "saiu do grupo");
                igual(antes - 1, s.tabela().tamanho(), "o objeto saiu da tabela");
                a.erro(404, "OBJETO_INEXISTENTE", a.get(ref.texto("href") + "/mensagens?espera=0"));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/notificacoes/saida", corpo("assinatura", ref.mapa())));
            }
        });

        t.caso("Referências forjadas, de outro tipo ou sem token são recusadas", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("estoquista");
                String pedido = a.post("/api/estoque/pedidos", corpo("idFornecedor", 1)).objeto().texto("objeto");
                ObjetoJson assinatura = a.post("/api/notificacoes/assinaturas", null).objeto();
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/notificacoes/saida", corpo("assinatura",
                        corpo("objeto", "Assinatura-00000000", "interface", "AssinaturaRemota"))));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/notificacoes/saida", corpo("assinatura",
                        corpo("objeto", pedido, "interface", "AssinaturaRemota")))); // nome de um pedido
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/notificacoes/saida", corpo("assinatura",
                        corpo("objeto", assinatura.texto("objeto"), "interface", "PedidoReposicaoRemoto")))); // tipo trocado
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/notificacoes/saida", corpo("assinatura", corpo("objeto", "x"))));
                a.erro(400, "ARGUMENTO_INVALIDO", a.post("/api/notificacoes/saida", corpo()));
                a.erro(404, "OBJETO_INEXISTENTE", a.get("/api/pedidos/PedidoReposicao-00000000"));
                a.erro(404, "OBJETO_INEXISTENTE", a.get("/api/pedidos/Estoque"));            // objeto de serviço, tipo errado
                a.erro(404, "OBJETO_INEXISTENTE", a.get("/api/pedidos/" + assinatura.texto("objeto")));
                a.erro(404, "OBJETO_INEXISTENTE",
                        a.get("/api/notificacoes/assinaturas/" + pedido + "/mensagens?espera=0"));
                igual(List.of("estoquista"), a.get("/api/notificacoes/membros").lista(), "a assinatura verdadeira continua ativa");
                a.erro(401, "NAO_AUTENTICADO", a.chamar("POST", "/api/notificacoes/assinaturas", null, null));
            }
        });
    }

    // ================================================================== notificações

    private static Apoio.Caixa entrar(Api a) {
        ObjetoJson ref = a.exigir(201, a.post("/api/notificacoes/assinaturas", null)).objeto();
        return new Apoio.Caixa(a, ref.texto("href"));
    }

    private static void notificacoes(Teste t) {
        t.secao("Notificações (substituto do multicast do TD01)");

        t.caso("Dois clientes no grupo recebem NOTIFICACAO, ALERTA e ATUALIZACAO", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api c1 = new Api(s).entrar("veterinario"), c2 = new Api(s).entrar("recepcao");
                Apoio.Caixa caixa1 = entrar(c1), caixa2 = entrar(c2);
                Api publicador = new Api(s).entrar("estoquista");
                igual(List.of("recepcao", "veterinario"), c1.get("/api/notificacoes/membros").lista(), "membros");
                for (TipoNotificacao tipo : TipoNotificacao.values()) {
                    publicador.exigir(201, publicador.post("/api/notificacoes/mensagens",
                            corpo("tipo", tipo.name(), "mensagem", "teste " + tipo)));
                }
                for (Apoio.Caixa caixa : List.of(caixa1, caixa2)) {
                    for (TipoNotificacao tipo : TipoNotificacao.values()) {
                        ObjetoJson n = caixa.esperar(x -> x.texto("mensagem").equals("estoquista: teste " + tipo));
                        igual(tipo.name(), n.texto("tipo"), "tipo recebido");
                    }
                }
                publicador.erro(400, "ARGUMENTO_INVALIDO", publicador.post("/api/notificacoes/mensagens",
                        corpo("tipo", "ALERTA", "mensagem", "  ")));
                publicador.erro(400, "ARGUMENTO_INVALIDO", publicador.post("/api/notificacoes/mensagens",
                        corpo("tipo", "SPAM", "mensagem", "x")));
            }
        });

        t.caso("leaveGroup: quem sai não recebe mais; ao entrar de novo volta a receber", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api c1 = new Api(s).entrar("veterinario"), c2 = new Api(s).entrar("recepcao");
                Apoio.Caixa caixa1 = entrar(c1);
                ObjetoJson ref2 = c2.exigir(201, c2.post("/api/notificacoes/assinaturas", null)).objeto();
                c2.exigir(204, c2.delete(ref2.texto("href")));   // sair pelo endereço do próprio objeto
                c1.exigir(201, c1.post("/api/notificacoes/mensagens",
                        corpo("tipo", "NOTIFICACAO", "mensagem", "só para quem ficou")));
                caixa1.esperar(n -> n.texto("mensagem").endsWith("só para quem ficou"));
                igual(List.of("veterinario"), c1.get("/api/notificacoes/membros").lista(), "membros após a saída");
                Apoio.Caixa caixa2b = entrar(c2);
                c1.exigir(201, c1.post("/api/notificacoes/mensagens", corpo("tipo", "NOTIFICACAO", "mensagem", "de volta")));
                List<ObjetoJson> chegaram = caixa2b.coletar(500);
                igual(1, chegaram.size(), "só a mensagem enviada depois de voltar: " + chegaram);
                verdadeiro(chegaram.get(0).texto("mensagem").endsWith("de volta"), "mensagem recebida");
            }
        });

        t.caso("Operações de estoque geram ATUALIZACAO e ALERTA de estoque baixo para todos", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api c1 = new Api(s).entrar("estoquista"), c2 = new Api(s).entrar("recepcao");
                Apoio.Caixa caixa = entrar(c2);
                c1.exigir(201, c1.post("/api/estoque/itens/2/saidas",
                        corpo("quantidade", 21, "motivo", "campanha de vermifugação"))); // 30 -> 9 (mínimo 10)
                caixa.esperar(n -> n.texto("tipo").equals("ATUALIZACAO") && n.texto("mensagem").contains("Saldo: 9"));
                caixa.esperar(n -> n.texto("tipo").equals("ALERTA") && n.texto("mensagem").contains("Estoque baixo")
                        && n.texto("mensagem").contains("Vermífugo"));
            }
        });

        t.caso("Entrega em tempo real: a espera (long polling) termina assim que a mensagem é publicada", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                Api a = new Api(s).entrar("veterinario");
                String href = a.exigir(201, a.post("/api/notificacoes/assinaturas", null)).objeto().texto("href");
                ExecutorService ex = Executors.newSingleThreadExecutor();
                try {
                    long inicio = System.nanoTime();
                    Future<Api.Resultado> espera = ex.submit(() -> a.get(href + "/mensagens?espera=20000"));
                    Thread.sleep(300);
                    s.central().publicar(TipoNotificacao.ALERTA, "urgente");
                    List<Object> ns = a.exigir(200, espera.get(5, TimeUnit.SECONDS)).lista();
                    long ms = (System.nanoTime() - inicio) / 1_000_000;
                    igual("urgente", ObjetoJson.de(ns.get(0)).texto("mensagem"), "mensagem recebida");
                    verdadeiro(ms >= 250 && ms < 3_000, "chegou em " + ms + " ms (a espera máxima era 20 s)");
                } finally {
                    ex.shutdownNow();
                }
                a.erro(400, "ARGUMENTO_INVALIDO", a.get(href + "/mensagens?espera=30001"));
                a.erro(400, "ARGUMENTO_INVALIDO", a.get(href + "/mensagens?espera=muito"));
                igual(0, a.exigir(200, a.get(href + "/mensagens?espera=100")).lista().size(), "sem mensagem: lista vazia");
            }
        });

        t.caso("Monitor: cada alerta é enviado uma vez e o vencimento é detectado com o passar dos dias", () -> {
            Apoio.RelogioAjustavel relogio = new Apoio.RelogioAjustavel();
            try (ServidorEstoque s = Apoio.servidor(relogio, 30_000)) {
                Api a = new Api(s).entrar("estoquista");
                Apoio.Caixa caixa = entrar(a);
                s.monitor().verificarTodos();
                List<ObjetoJson> primeira = caixa.coletar(800);
                igual(3, primeira.size(), "alertas iniciais (estoque baixo, pomada e V10): " + primeira);
                s.monitor().verificarTodos();
                igual(0, caixa.coletar(300).size(), "nenhum alerta repetido");
                relogio.avancarDias(4);                          // a pomada vence
                s.monitor().verificarTodos();
                caixa.esperar(n -> n.texto("mensagem").startsWith("Item vencido") && n.texto("mensagem").contains("Pomada"));
                a.erro(409, "OPERACAO_NEGADA", a.post("/api/estoque/itens/4/saidas", corpo("quantidade", 1, "motivo", "curativo")));
                a.exigir(201, a.post("/api/estoque/itens/3/entradas", corpo("quantidade", 20))); // volta ao normal
                a.exigir(201, a.post("/api/estoque/itens/3/saidas", corpo("quantidade", 16, "motivo", "uso"))); // baixo de novo
                caixa.esperar(n -> n.texto("tipo").equals("ALERTA") && n.texto("mensagem").contains("Anti-inflamatório"));
            }
        });

        t.caso("Lease: assinatura abandonada pelo cliente é encerrada e retirada da tabela", () -> {
            try (ServidorEstoque s = Apoio.servidor(java.time.Clock.systemDefaultZone(), 400)) {
                Api a = new Api(s).entrar("recepcao");
                ObjetoJson ref = a.exigir(201, a.post("/api/notificacoes/assinaturas", null)).objeto();
                igual(1, s.central().quantidadeMembros(), "membro ativo");
                long limite = System.currentTimeMillis() + 5_000;
                while (s.central().quantidadeMembros() > 0 && System.currentTimeMillis() < limite) Thread.sleep(50);
                igual(0, s.central().quantidadeMembros(), "assinatura expirada");
                a.erro(404, "OBJETO_INEXISTENTE", a.get(ref.texto("href") + "/mensagens?espera=0"));
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
                        Api c = new Api(s).entrar(i % 2 == 0 ? "veterinario" : "estoquista");
                        fs.add(ex.submit(() -> {
                            largada.await();
                            Api.Resultado r = c.post("/api/estoque/itens/8/saidas", corpo("quantidade", 1, "motivo", "procedimento"));
                            if (r.status() == 201) aceitas.incrementAndGet();
                            else if (r.status() == 409) negadas.incrementAndGet();
                            else throw new AssertionError("status inesperado " + r.status() + ": " + r.texto());
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
                    Api c = new Api(s).entrar("estoquista");
                    tarefas.add(() -> {
                        for (int k = 0; k < operacoes; k++) {
                            if (k % 2 == 0) c.exigir(201, c.post("/api/estoque/itens/7/entradas", corpo("quantidade", 2)));
                            else c.exigir(201, c.post("/api/estoque/itens/7/saidas", corpo("quantidade", 3, "motivo", "uso")));
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
                var hist = new ArrayList<>(s.estoque().listarMovimentacoes(7));
                igual(clientes * operacoes, hist.size(), "movimentações registradas");
                hist.sort((x, y) -> Integer.compare(x.getId(), y.getId()));
                int saldo = 200;
                for (var m : hist) {
                    saldo += m.getTipo().name().equals("ENTRADA") ? m.getQuantidade() : -m.getQuantidade();
                    igual(saldo, m.getSaldoApos(), "saldo registrado na movimentação #" + m.getId());
                }
            }
        });

        t.caso("10 membros e 5 publicadores simultâneos: todos recebem as 50 mensagens na mesma ordem", () -> {
            try (ServidorEstoque s = Apoio.servidor()) {
                int membros = 10, publicadores = 5, porPublicador = 10, total = publicadores * porPublicador;
                List<String> assinaturas = new ArrayList<>();
                List<Api> apis = new ArrayList<>();
                for (int i = 0; i < membros; i++) {
                    Api c = new Api(s).entrar("recepcao");
                    apis.add(c);
                    assinaturas.add(c.exigir(201, c.post("/api/notificacoes/assinaturas", null)).objeto().texto("href"));
                }
                ExecutorService ex = Executors.newFixedThreadPool(membros + publicadores);
                try {
                    List<Future<List<Long>>> recepcoes = new ArrayList<>();
                    for (int i = 0; i < membros; i++) {
                        Api c = apis.get(i);
                        String href = assinaturas.get(i);
                        recepcoes.add(ex.submit(() -> {
                            List<Long> ids = new ArrayList<>();
                            long limite = System.currentTimeMillis() + 20_000;
                            while (ids.size() < total && System.currentTimeMillis() < limite) {
                                for (Object o : c.exigir(200, c.get(href + "/mensagens?espera=2000")).lista()) {
                                    ids.add(ObjetoJson.de(o).longo("id"));
                                }
                            }
                            return ids;
                        }));
                    }
                    List<Future<?>> envios = new ArrayList<>();
                    for (int p = 0; p < publicadores; p++) {
                        Api c = new Api(s).entrar("estoquista");
                        int numero = p;
                        envios.add(ex.submit(() -> {
                            for (int k = 0; k < porPublicador; k++) {
                                c.exigir(201, c.post("/api/notificacoes/mensagens",
                                        corpo("tipo", "NOTIFICACAO", "mensagem", "p" + numero + "-m" + k)));
                            }
                            return null;
                        }));
                    }
                    for (Future<?> f : envios) f.get(30, TimeUnit.SECONDS);
                    List<Long> referencia = null;
                    for (Future<List<Long>> f : recepcoes) {
                        List<Long> ids = f.get(30, TimeUnit.SECONDS);
                        igual(total, ids.size(), "mensagens recebidas por um membro");
                        igual(total, new HashSet<>(ids).size(), "sem duplicatas");
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
