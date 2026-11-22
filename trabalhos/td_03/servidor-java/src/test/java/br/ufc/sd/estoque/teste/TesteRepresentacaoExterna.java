package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.lanca;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

import br.ufc.sd.estoque.api.NaoAutenticadoException;
import br.ufc.sd.estoque.api.RecursoNaoEncontradoException;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.Insumo;
import br.ufc.sd.estoque.modelo.ItemEstoque;
import br.ufc.sd.estoque.modelo.ItemPedido;
import br.ufc.sd.estoque.modelo.Medicamento;
import br.ufc.sd.estoque.modelo.Movimentacao;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.modelo.PedidoReposicao;
import br.ufc.sd.estoque.modelo.Sessao;
import br.ufc.sd.estoque.modelo.StatusPedido;
import br.ufc.sd.estoque.modelo.TipoMovimentacao;
import br.ufc.sd.estoque.modelo.TipoNotificacao;
import br.ufc.sd.estoque.modelo.Vacina;
import br.ufc.sd.estoque.web.CodigoErro;
import br.ufc.sd.estoque.web.ErroApi;
import br.ufc.sd.estoque.web.ReferenciaRemota;
import br.ufc.sd.estoque.web.Requisicao;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Representação externa: JSON, entidades (com herança e agregação), referências remotas e o roteador. */
final class TesteRepresentacaoExterna {

    private static final Fornecedor VET = new Fornecedor(1, "VetPharma", "11.111.111/0001-11", "(85) 3333-1001");
    private static final ReferenciaRemota REF =
            new ReferenciaRemota("PedidoReposicao-ab12cd34", "PedidoReposicaoRemoto",
                    "http://localhost:8080/api/pedidos/PedidoReposicao-ab12cd34");

    private TesteRepresentacaoExterna() { }

    static void executar(Teste t) {
        t.secao("Representação externa de dados (JSON)");

        t.caso("JSON: ida e volta com acentos, aspas, escapes e tipos variados", () -> {
            Map<String, Object> original = new LinkedHashMap<>();
            original.put("texto", "aspas \" barra \\ linha1\nlinha2\ttab ação ✓ \u0001");
            original.put("inteiro", 123_456_789_012L);
            original.put("decimal", 2.5);
            original.put("ligado", true);
            original.put("nada", null);
            original.put("lista", Arrays.asList(1L, "dois", Arrays.asList(true, false)));
            igual(original, Json.ler(Json.escrever(original)), "objeto relido");
        });

        t.caso("JSON: textos inválidos são rejeitados", () -> {
            for (String invalido : List.of("", "{", "{\"a\":}", "[1,2", "{\"a\":1,}", "tru", "\"sem fim",
                    "{\"a\":1}{", "{\"a\":1,\"a\":2}", "\"\\x\"", "01a")) {
                lanca(IllegalArgumentException.class, () -> Json.ler(invalido));
            }
        });

        t.caso("JSON: aninhamento excessivo é recusado (proteção da pilha do servidor)", () -> {
            String profundo = "[".repeat(Json.PROFUNDIDADE_MAXIMA + 5) + "]".repeat(Json.PROFUNDIDADE_MAXIMA + 5);
            lanca(IllegalArgumentException.class, () -> Json.ler(profundo));
            Json.ler("[".repeat(10) + "]".repeat(10));
        });

        t.caso("ObjetoJson valida tipos e campos ausentes", () -> {
            ObjetoJson o = ObjetoJson.de(Json.ler("{\"n\":5,\"t\":\"x\",\"grande\":99999999999}"));
            igual(5, o.inteiro("n"), "inteiro");
            lanca(IllegalArgumentException.class, () -> o.inteiro("t"));
            lanca(IllegalArgumentException.class, () -> o.texto("ausente"));
            lanca(IllegalArgumentException.class, () -> o.inteiro("grande"));
            lanca(IllegalArgumentException.class, () -> ObjetoJson.de(List.of()));
        });

        t.caso("Herança preservada: Medicamento, Vacina e Insumo voltam com a subclasse e todos os campos", () -> {
            List<ItemEstoque> itens = List.of(
                    new Medicamento(3, "Tramadol 50mg", VET, 25, 5, 18.70, LocalDate.of(2028, 8, 31), "Tramadol", "50 mg", true),
                    new Vacina(5, "Antirrábica", VET, 40, 15, 39.90, LocalDate.of(2027, 12, 31), "Raiva", 2),
                    new Insumo(7, "Luvas", VET, 12, 5, 32.0, null, "caixa", false));
            for (ItemEstoque original : itens) {
                String json = Json.escrever(CodecEntidades.itemParaJson(original));
                ItemEstoque relido = CodecEntidades.itemDeJson(Json.ler(json));
                igual(original.getClass(), relido.getClass(), "subclasse");
                igual(original, relido, "item relido");
                verdadeiro(original != relido, "deve ser outro objeto (cópia)");
            }
        });

        t.caso("Agregação preservada: fornecedor aninhado no item e no pedido", () -> {
            Map<String, Object> json = CodecEntidades.itemParaJson(
                    new Insumo(1, "Gaze", VET, 1, 0, 1.0, null, "pacote", true));
            igual(CodecEntidades.fornecedorParaJson(VET), json.get("fornecedor"), "fornecedor dentro do item");
            PedidoReposicao p = new PedidoReposicao(4, VET, List.of(new ItemPedido(1, "Gaze", 10),
                    new ItemPedido(2, "Seringa", 3)), StatusPedido.ABERTO, "estoquista", 1_700_000_000_000L);
            PedidoReposicao relido = CodecEntidades.pedidoDeJson(Json.ler(Json.escrever(CodecEntidades.pedidoParaJson(p))));
            igual(p, relido, "pedido relido");
            igual(VET, relido.getFornecedor(), "fornecedor do pedido");
        });

        t.caso("Movimentação, notificação e sessão: ida e volta", () -> {
            Movimentacao m = new Movimentacao(9, TipoMovimentacao.SAIDA, 3, "Tramadol", 2, 23, "veterinario",
                    "dor pós-operatória", 1_700_000_000_123L);
            igual(m, CodecEntidades.movimentacaoDeJson(Json.ler(Json.escrever(CodecEntidades.movimentacaoParaJson(m)))), "movimentação");
            Notificacao n = new Notificacao(42, TipoNotificacao.ALERTA, "Estoque baixo", 123_456_789L);
            String jsonN = Json.escrever(CodecEntidades.notificacaoParaJson(n));
            verdadeiro(jsonN.contains("\"tipo\":\"ALERTA\"") && jsonN.contains("\"mensagem\":\"Estoque baixo\"")
                    && jsonN.contains("\"timestamp\":123456789"), "formato do TD01: " + jsonN);
            igual(n, CodecEntidades.notificacaoDeJson(Json.ler(jsonN)), "notificação");
            Sessao s = new Sessao("abc-123", "recepcao", 77L);
            igual(s, CodecEntidades.sessaoDeJson(Json.ler(Json.escrever(CodecEntidades.sessaoParaJson(s)))), "sessão");
        });

        t.caso("Entidade com dados inválidos na representação externa é rejeitada", () -> {
            lanca(IllegalArgumentException.class, () -> CodecEntidades.itemDeJson(Json.ler("{\"classe\":\"FERRAMENTA\"}")));
            lanca(IllegalArgumentException.class, () -> CodecEntidades.itemDeJson(Json.ler(
                    "{\"classe\":\"INSUMO\",\"quantidade\":\"dez\",\"estoqueMinimo\":1,\"precoUnitario\":1}")));
            lanca(IllegalArgumentException.class, () -> CodecEntidades.itemDeJson(Json.ler(
                    "{\"classe\":\"INSUMO\",\"quantidade\":1,\"estoqueMinimo\":1,\"precoUnitario\":1,\"validade\":\"31/12/2030\"}")));
            lanca(IllegalArgumentException.class, () -> CodecEntidades.notificacaoDeJson(Json.ler(
                    "{\"id\":1,\"tipo\":\"SPAM\",\"mensagem\":\"x\",\"timestamp\":1}")));
        });

        t.secao("Referência remota (passagem por referência)");

        t.caso("ReferenciaRemota: representação externa de ida e volta e validação", () -> {
            igual(REF, ReferenciaRemota.deJson(Json.ler(Json.escrever(REF.paraJson()))), "referência relida");
            // o href é opcional na entrada: o servidor identifica o objeto pelo nome
            igual(new ReferenciaRemota("Assinatura-1", "AssinaturaRemota", null), ReferenciaRemota.deJson(
                    Json.ler("{\"objeto\":\"Assinatura-1\",\"interface\":\"AssinaturaRemota\"}")), "sem href");
            lanca(IllegalArgumentException.class, () -> new ReferenciaRemota(" ", "i", "h"));
            lanca(IllegalArgumentException.class, () -> new ReferenciaRemota("o", null, "h"));
            lanca(IllegalArgumentException.class, () -> ReferenciaRemota.deJson(Json.ler("{\"objeto\":\"x\"}")));
        });

        t.caso("Roteador: seleciona por método e caminho, extrai variáveis e responde 404/405", () -> {
            Roteador r = new Roteador(msg -> { });
            r.get("/api/itens/{id}", q -> Resposta.ok(Map.of("id", q.parametro("id"))));
            r.post("/api/itens/{id}", q -> Resposta.ok(null));
            Resposta ok = r.despachar(requisicao("GET", "/api/itens/42"));
            igual(200, ok.status(), "GET casa");
            igual(Map.of("id", "42"), ok.corpo(), "variável do caminho");
            igual(200, r.despachar(requisicao("GET", "/api/itens/42/")).status(), "barra final é ignorada");
            Resposta metodo = r.despachar(requisicao("DELETE", "/api/itens/42"));
            igual(405, metodo.status(), "método não permitido");
            verdadeiro(metodo.cabecalhos().get("Allow").contains("GET"), "Allow lista os métodos aceitos");
            igual(404, r.despachar(requisicao("GET", "/api/outra")).status(), "caminho inexistente");
            igual(404, r.despachar(requisicao("GET", "/api/itens")).status(), "faltou a variável");
        });

        t.caso("Roteador: exceções de negócio viram o status HTTP certo e nunca escapam", () -> {
            Roteador r = new Roteador(msg -> { });
            r.get("/a", q -> { throw new IllegalArgumentException("arg"); });
            r.get("/b", q -> { throw new IllegalStateException("regra"); });
            r.get("/c", q -> { throw new NaoAutenticadoException("token"); });
            r.get("/d", q -> { throw new RecursoNaoEncontradoException("sumiu"); });
            r.get("/e", q -> { throw new NullPointerException("bug"); });
            r.get("/f", q -> {
                q.corpo().inteiro("x"); // o corpo veio vazio
                return Resposta.ok(null);
            });
            igual(400, r.despachar(requisicao("GET", "/a")).status(), "IllegalArgumentException");
            igual(409, r.despachar(requisicao("GET", "/b")).status(), "IllegalStateException");
            Resposta nao = r.despachar(requisicao("GET", "/c"));
            igual(401, nao.status(), "NaoAutenticadoException");
            igual("Bearer", nao.cabecalhos().get("WWW-Authenticate"), "desafio de autenticação");
            igual(404, r.despachar(requisicao("GET", "/d")).status(), "RecursoNaoEncontradoException");
            Resposta interno = r.despachar(requisicao("GET", "/e"));
            igual(500, interno.status(), "erro inesperado");
            verdadeiro(!interno.corpo().toString().contains("bug"), "o erro interno não vaza detalhes");
            igual(400, r.despachar(requisicao("GET", "/f")).status(), "campo ausente no corpo");
        });

        t.caso("Requisicao: token Bearer, corpo JSON e parâmetros", () -> {
            Requisicao q = new Requisicao(1, "POST", "/x", Map.of("espera", "250"),
                    Map.of("Authorization", "Bearer abc-123"), "{\"n\":5}".getBytes(StandardCharsets.UTF_8),
                    "127.0.0.1", "http://localhost:1");
            igual("abc-123", q.token(), "token");
            igual(5, q.corpo().inteiro("n"), "corpo");
            igual(250L, q.consultaLonga("espera", 0), "query string");
            igual(7L, q.consultaLonga("outra", 7), "padrão");
            Requisicao sem = new Requisicao(2, "GET", "/x", Map.of(), Map.of("authorization", "Basic xyz"), null,
                    "127.0.0.1", "http://localhost:1");
            igual(null, sem.token(), "esquema que não é Bearer");
            igual(0, sem.corpo().mapa().size(), "corpo vazio vira objeto vazio");
            Requisicao ruim = new Requisicao(3, "POST", "/x", Map.of(), Map.of(), "{ruim".getBytes(StandardCharsets.UTF_8),
                    "127.0.0.1", "http://localhost:1");
            ErroApi e = lanca(ErroApi.class, ruim::corpo);
            igual(CodigoErro.REQUISICAO_INVALIDA, e.getCodigo(), "JSON malformado");
        });
    }

    private static Requisicao requisicao(String metodo, String caminho) {
        return new Requisicao(1, metodo, caminho, Map.of(), Map.of(), null, "127.0.0.1", "http://localhost:1");
    }
}
