package br.ufc.sd.estoque.json;

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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Representação externa (JSON) das entidades — é o que torna possível a passagem por valor.
 *
 * <p>Cada entidade vira um objeto JSON com seus campos; a herança é preservada pelo campo
 * discriminador {@code "classe"} ({@code MEDICAMENTO}, {@code VACINA} ou {@code INSUMO}), e as
 * agregações viram objetos aninhados (o fornecedor dentro do item e dentro do pedido). Datas
 * usam ISO-8601 ({@code "2030-06-30"}); instantes, milissegundos desde 1970, como o
 * {@code timestamp} do TD01.
 */
public final class CodecEntidades {

    private CodecEntidades() { }

    // ------------------------------------------------------------------ Fornecedor

    public static Map<String, Object> fornecedorParaJson(Fornecedor f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("nome", f.getNome());
        m.put("cnpj", f.getCnpj());
        m.put("telefone", f.getTelefone());
        return m;
    }

    public static Fornecedor fornecedorDeJson(Object json) {
        ObjetoJson o = ObjetoJson.de(json);
        return new Fornecedor(o.inteiro("id"), o.textoOpcional("nome"), o.textoOpcional("cnpj"),
                o.textoOpcional("telefone"));
    }

    // ------------------------------------------------------------------ ItemEstoque (polimórfico)

    public static Map<String, Object> itemParaJson(ItemEstoque i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("classe", classeDe(i));
        m.put("id", i.getId());
        m.put("nome", i.getNome());
        m.put("fornecedor", i.getFornecedor() == null ? null : fornecedorParaJson(i.getFornecedor()));
        m.put("quantidade", i.getQuantidade());
        m.put("estoqueMinimo", i.getEstoqueMinimo());
        m.put("precoUnitario", i.getPrecoUnitario());
        m.put("validade", i.getValidade() == null ? null : i.getValidade().toString());
        if (i instanceof Medicamento med) {
            m.put("principioAtivo", med.getPrincipioAtivo());
            m.put("concentracao", med.getConcentracao());
            m.put("controlado", med.isControlado());
        } else if (i instanceof Vacina vac) {
            m.put("doencaAlvo", vac.getDoencaAlvo());
            m.put("dosesPorFrasco", vac.getDosesPorFrasco());
        } else if (i instanceof Insumo ins) {
            m.put("unidadeMedida", ins.getUnidadeMedida());
            m.put("esteril", ins.isEsteril());
        }
        return m;
    }

    private static String classeDe(ItemEstoque i) {
        if (i instanceof Medicamento) return "MEDICAMENTO";
        if (i instanceof Vacina) return "VACINA";
        if (i instanceof Insumo) return "INSUMO";
        throw new IllegalArgumentException("Subclasse de item desconhecida: " + i.getClass().getName());
    }

    public static ItemEstoque itemDeJson(Object json) {
        ObjetoJson o = ObjetoJson.de(json);
        String classe = o.texto("classe");
        ItemEstoque item = switch (classe) {
            case "MEDICAMENTO" -> {
                Medicamento m = new Medicamento();
                m.setPrincipioAtivo(o.textoOpcional("principioAtivo"));
                m.setConcentracao(o.textoOpcional("concentracao"));
                m.setControlado(o.tem("controlado") && o.booleano("controlado"));
                yield m;
            }
            case "VACINA" -> {
                Vacina v = new Vacina();
                v.setDoencaAlvo(o.textoOpcional("doencaAlvo"));
                v.setDosesPorFrasco(o.inteiro("dosesPorFrasco"));
                yield v;
            }
            case "INSUMO" -> {
                Insumo in = new Insumo();
                in.setUnidadeMedida(o.textoOpcional("unidadeMedida"));
                in.setEsteril(o.tem("esteril") && o.booleano("esteril"));
                yield in;
            }
            default -> throw new IllegalArgumentException("Classe de item desconhecida: '" + classe + "'.");
        };
        item.setId(o.tem("id") ? o.inteiro("id") : 0);
        item.setNome(o.textoOpcional("nome"));
        item.setFornecedor(o.tem("fornecedor") ? fornecedorDeJson(o.valor("fornecedor")) : null);
        item.setQuantidade(o.inteiro("quantidade"));
        item.setEstoqueMinimo(o.inteiro("estoqueMinimo"));
        item.setPrecoUnitario(o.decimal("precoUnitario"));
        item.setValidade(data(o.textoOpcional("validade")));
        return item;
    }

    private static LocalDate data(String texto) {
        if (texto == null) return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida '" + texto + "' (use AAAA-MM-DD).");
        }
    }

    // ------------------------------------------------------------------ Movimentacao

    public static Map<String, Object> movimentacaoParaJson(Movimentacao mv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mv.getId());
        m.put("tipo", mv.getTipo().name());
        m.put("idItem", mv.getIdItem());
        m.put("nomeItem", mv.getNomeItem());
        m.put("quantidade", mv.getQuantidade());
        m.put("saldoApos", mv.getSaldoApos());
        m.put("responsavel", mv.getResponsavel());
        m.put("motivo", mv.getMotivo());
        m.put("instante", mv.getInstante());
        return m;
    }

    public static Movimentacao movimentacaoDeJson(Object json) {
        ObjetoJson o = ObjetoJson.de(json);
        return new Movimentacao(o.inteiro("id"), enumeracao(TipoMovimentacao.class, o.texto("tipo")),
                o.inteiro("idItem"), o.textoOpcional("nomeItem"), o.inteiro("quantidade"), o.inteiro("saldoApos"),
                o.textoOpcional("responsavel"), o.textoOpcional("motivo"), o.longo("instante"));
    }

    // ------------------------------------------------------------------ Notificacao

    public static Map<String, Object> notificacaoParaJson(Notificacao n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("tipo", n.getTipo().name());
        m.put("mensagem", n.getMensagem());
        m.put("timestamp", n.getTimestamp());
        return m;
    }

    public static Notificacao notificacaoDeJson(Object json) {
        ObjetoJson o = ObjetoJson.de(json);
        return new Notificacao(o.longo("id"), enumeracao(TipoNotificacao.class, o.texto("tipo")),
                o.texto("mensagem"), o.longo("timestamp"));
    }

    // ------------------------------------------------------------------ PedidoReposicao

    public static Map<String, Object> pedidoParaJson(PedidoReposicao p) {
        List<Object> itens = new ArrayList<>();
        for (ItemPedido i : p.getItens()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idItem", i.getIdItem());
            m.put("nomeItem", i.getNomeItem());
            m.put("quantidade", i.getQuantidade());
            itens.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("fornecedor", fornecedorParaJson(p.getFornecedor()));
        m.put("itens", itens);
        m.put("status", p.getStatus().name());
        m.put("solicitante", p.getSolicitante());
        m.put("criadoEm", p.getCriadoEm());
        return m;
    }

    public static PedidoReposicao pedidoDeJson(Object json) {
        ObjetoJson o = ObjetoJson.de(json);
        List<ItemPedido> itens = new ArrayList<>();
        for (Object item : o.lista("itens")) {
            ObjetoJson i = ObjetoJson.de(item);
            itens.add(new ItemPedido(i.inteiro("idItem"), i.textoOpcional("nomeItem"), i.inteiro("quantidade")));
        }
        return new PedidoReposicao(o.inteiro("id"), fornecedorDeJson(o.valor("fornecedor")), itens,
                enumeracao(StatusPedido.class, o.texto("status")), o.textoOpcional("solicitante"),
                o.longo("criadoEm"));
    }

    // ------------------------------------------------------------------ Sessao

    public static Map<String, Object> sessaoParaJson(Sessao s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", s.getToken());
        m.put("usuario", s.getUsuario());
        m.put("inicio", s.getInicio());
        return m;
    }

    public static Sessao sessaoDeJson(Object json) {
        ObjetoJson o = ObjetoJson.de(json);
        return new Sessao(o.texto("token"), o.texto("usuario"), o.longo("inicio"));
    }

    // ------------------------------------------------------------------ utilitários

    public static <T> List<Object> listaParaJson(List<T> itens, Function<T, Map<String, Object>> codificar) {
        List<Object> json = new ArrayList<>(itens.size());
        for (T item : itens) json.add(codificar.apply(item));
        return json;
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> listaDeJson(Object json, Function<Object, T> decodificar) {
        if (!(json instanceof List)) throw new IllegalArgumentException("Esperava uma lista JSON.");
        List<T> itens = new ArrayList<>();
        for (Object item : (List<Object>) json) itens.add(decodificar.apply(item));
        return itens;
    }

    public static <E extends Enum<E>> E enumeracao(Class<E> tipo, String nome) {
        try {
            return Enum.valueOf(tipo, nome);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Valor '" + nome + "' inválido para " + tipo.getSimpleName() + ".");
        }
    }
}
