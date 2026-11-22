package br.ufc.sd.estoque.web;

import br.ufc.sd.estoque.api.NaoAutenticadoException;
import br.ufc.sd.estoque.api.RecursoNaoEncontradoException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Seleciona o objeto distribuído e a operação a partir do método HTTP e do caminho:
 * é o papel do despachante do TD02 ({@code objectReference} + {@code methodId}), agora
 * expresso como {@code GET /api/estoque/itens/{id}}.
 *
 * <p>{@link #despachar} nunca lança exceção: qualquer problema vira uma {@link Resposta}
 * de erro com o status HTTP adequado.
 */
public class Roteador {

    @FunctionalInterface
    public interface Operacao {
        Resposta executar(Requisicao requisicao);
    }

    private record Rota(String metodo, String[] segmentos, Operacao operacao) { }

    private final List<Rota> rotas = new ArrayList<>();
    private final Consumer<String> registro;

    public Roteador(Consumer<String> registro) {
        this.registro = registro;
    }

    public Roteador get(String padrao, Operacao op) { return adicionar("GET", padrao, op); }
    public Roteador post(String padrao, Operacao op) { return adicionar("POST", padrao, op); }
    public Roteador delete(String padrao, Operacao op) { return adicionar("DELETE", padrao, op); }

    /** Registra uma operação; variáveis do caminho vão entre chaves: {@code /api/itens/{id}}. */
    public synchronized Roteador adicionar(String metodo, String padrao, Operacao op) {
        rotas.add(new Rota(metodo, dividir(padrao), op));
        return this;
    }

    /** Operações (método HTTP + caminho) registradas sob um prefixo; é a descrição que cada objeto devolve de si. */
    public synchronized List<Map<String, Object>> operacoes(String prefixo) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Rota r : rotas) {
            String caminho = "/" + String.join("/", r.segmentos());
            if (!caminho.equals(prefixo) && caminho.startsWith(prefixo + "/")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("metodo", r.metodo());
                m.put("caminho", caminho);
                lista.add(m);
            }
        }
        return lista;
    }

    public Resposta despachar(Requisicao requisicao) {
        try {
            String[] caminho = dividir(requisicao.caminho());
            Set<String> permitidos = new LinkedHashSet<>();
            List<Rota> copia;
            synchronized (this) {
                copia = new ArrayList<>(rotas);
            }
            for (Rota rota : copia) {
                Map<String, String> variaveis = casar(rota.segmentos(), caminho);
                if (variaveis == null) continue;
                if (rota.metodo().equals(requisicao.metodo())) {
                    return rota.operacao().executar(requisicao.comParametros(variaveis));
                }
                permitidos.add(rota.metodo());
            }
            if (!permitidos.isEmpty()) {
                permitidos.add("OPTIONS");
                return erro(new ErroApi(CodigoErro.METODO_INEXISTENTE, "O recurso " + requisicao.caminho()
                        + " não aceita " + requisicao.metodo() + ". Aceita: " + String.join(", ", permitidos) + "."))
                        .comCabecalho("Allow", String.join(", ", permitidos));
            }
            return erro(new ErroApi(CodigoErro.OBJETO_INEXISTENTE,
                    "Nenhum objeto remoto responde em " + requisicao.caminho() + "."));
        } catch (ErroApi e) {
            return erro(e);
        } catch (NaoAutenticadoException e) {
            return erro(new ErroApi(CodigoErro.NAO_AUTENTICADO, e.getMessage()))
                    .comCabecalho("WWW-Authenticate", "Bearer");
        } catch (RecursoNaoEncontradoException e) {
            return erro(new ErroApi(CodigoErro.OBJETO_INEXISTENTE, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return erro(new ErroApi(CodigoErro.ARGUMENTO_INVALIDO, e.getMessage()));
        } catch (IllegalStateException e) {
            return erro(new ErroApi(CodigoErro.OPERACAO_NEGADA, e.getMessage()));
        } catch (RuntimeException e) {
            registro.accept("[Roteador] Erro interno em " + requisicao + ": " + e);
            return erro(new ErroApi(CodigoErro.ERRO_INTERNO, "Erro interno no servidor."));
        }
    }

    /** Resposta de erro: {@code {"erro":{"codigo":"...","mensagem":"..."}}}. */
    public static Resposta erro(ErroApi e) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        detalhe.put("codigo", e.getCodigo().name());
        detalhe.put("mensagem", e.getMessage() == null ? e.getCodigo().name() : e.getMessage());
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", detalhe);
        return new Resposta(e.status(), corpo, Map.of());
    }

    private static String[] dividir(String caminho) {
        String limpo = caminho.startsWith("/") ? caminho.substring(1) : caminho;
        if (limpo.endsWith("/")) limpo = limpo.substring(0, limpo.length() - 1);
        return limpo.isEmpty() ? new String[0] : limpo.split("/", -1);
    }

    /** @return as variáveis do caminho, ou {@code null} se o caminho não casa com o padrão */
    private static Map<String, String> casar(String[] padrao, String[] caminho) {
        if (padrao.length != caminho.length) return null;
        Map<String, String> variaveis = new LinkedHashMap<>();
        for (int i = 0; i < padrao.length; i++) {
            String p = padrao[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                if (caminho[i].isEmpty()) return null;
                variaveis.put(p.substring(1, p.length() - 1), caminho[i]);
            } else if (!p.equals(caminho[i])) {
                return null;
            }
        }
        return variaveis;
    }
}
