package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.lanca;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

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
import br.ufc.sd.estoque.protocolo.CargaResposta;
import br.ufc.sd.estoque.protocolo.CodecMensagem;
import br.ufc.sd.estoque.protocolo.CodigoErro;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.FalhaComunicacaoException;
import br.ufc.sd.estoque.protocolo.Mensagem;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import br.ufc.sd.estoque.protocolo.TipoMensagem;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serialização/desserialização: JSON, entidades (com herança e agregação) e mensagens do protocolo. */
final class TesteRepresentacaoExterna {

    private static final Fornecedor VET = new Fornecedor(1, "VetPharma", "11.111.111/0001-11", "(85) 3333-1001");
    private static final RemoteObjectRef REF =
            new RemoteObjectRef("localhost", 1099, "ClinicaVeterinaria", "Estoque", "EstoqueRemoto");

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

        t.secao("Mensagens do protocolo requisição-resposta");

        t.caso("Mensagem tem os 5 campos da figura e sobrevive ao empacotamento", () -> {
            byte[] args = Json.escreverBytes(Map.of("idItem", 3L));
            Mensagem m = new Mensagem(TipoMensagem.REQUEST, 7, REF, "buscarItem", args);
            byte[] bytes = CodecMensagem.empacotar(m);
            String json = new String(bytes, StandardCharsets.UTF_8);
            for (String campo : List.of("\"messageType\":0", "\"requestId\":7", "\"objectReference\":{",
                    "\"methodId\":\"buscarItem\"", "\"arguments\":{\"idItem\":3}")) {
                verdadeiro(json.contains(campo), "campo " + campo + " em " + json);
            }
            igual(m, CodecMensagem.desempacotar(bytes), "mensagem desempacotada");
        });

        t.caso("RemoteObjectRef: representação externa e validação", () -> {
            igual(REF, RemoteObjectRef.deJson(Json.ler(Json.escrever(REF.paraJson()))), "referência relida");
            lanca(IllegalArgumentException.class, () -> new RemoteObjectRef("h", 0, "s", "o", "i"));
            lanca(IllegalArgumentException.class, () -> new RemoteObjectRef("h", 1099, "s", " ", "i"));
        });

        t.caso("Mensagens malformadas são rejeitadas no desempacotamento", () -> {
            for (String invalida : List.of(
                    "não é json",
                    "{\"messageType\":0,\"requestId\":1,\"methodId\":\"x\",\"arguments\":{}}",
                    "{\"messageType\":5,\"requestId\":1,\"objectReference\":" + Json.escrever(REF.paraJson())
                            + ",\"methodId\":\"x\",\"arguments\":{}}",
                    "{\"messageType\":0,\"requestId\":\"um\",\"objectReference\":" + Json.escrever(REF.paraJson())
                            + ",\"methodId\":\"x\",\"arguments\":{}}",
                    "{\"messageType\":0,\"requestId\":1,\"objectReference\":" + Json.escrever(REF.paraJson())
                            + ",\"methodId\":\"\",\"arguments\":{}}",
                    "{\"messageType\":0,\"requestId\":1,\"objectReference\":" + Json.escrever(REF.paraJson())
                            + ",\"methodId\":\"x\"}")) {
                lanca(IllegalArgumentException.class,
                        () -> CodecMensagem.desempacotar(invalida.getBytes(StandardCharsets.UTF_8)));
            }
            lanca(IllegalArgumentException.class, () -> CodecMensagem.desempacotar(new byte[0]));
            lanca(IllegalArgumentException.class,
                    () -> CodecMensagem.desempacotar(new byte[CodecMensagem.TAMANHO_MAXIMO + 1]));
        });

        t.caso("Carga da resposta: sucesso devolve o resultado, erro vira ExcecaoRemota com código", () -> {
            igual(List.of(1L, 2L), CargaResposta.ler(CargaResposta.sucesso(List.of(1, 2))), "resultado");
            ExcecaoRemota e = lanca(ExcecaoRemota.class,
                    () -> CargaResposta.ler(CargaResposta.erro(CodigoErro.OPERACAO_NEGADA, "sem saldo")));
            igual(CodigoErro.OPERACAO_NEGADA, e.getCodigo(), "código");
            igual("sem saldo", e.getMessage(), "mensagem");
            lanca(FalhaComunicacaoException.class,
                    () -> CargaResposta.ler("{\"status\":\"TALVEZ\"}".getBytes(StandardCharsets.UTF_8)));
        });
    }
}
