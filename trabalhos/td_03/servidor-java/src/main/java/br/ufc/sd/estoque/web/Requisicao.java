package br.ufc.sd.estoque.web;

import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.json.ObjetoJson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mensagem de requisição já desempacotada (o que {@code getRequest} entrega ao servidor):
 * método HTTP, caminho, parâmetros, cabeçalhos e corpo JSON.
 */
public final class Requisicao {

    private final long id;
    private final String metodo;
    private final String caminho;
    private final Map<String, String> consulta;
    private final Map<String, String> cabecalhos; // chaves em minúsculas
    private final byte[] corpo;
    private final String cliente;
    private final String baseUrl;
    private final Map<String, String> parametros; // variáveis do caminho, ex.: {id}

    public Requisicao(long id, String metodo, String caminho, Map<String, String> consulta,
                      Map<String, String> cabecalhos, byte[] corpo, String cliente, String baseUrl) {
        this(id, metodo, caminho, consulta, cabecalhos, corpo, cliente, baseUrl, Map.of());
    }

    private Requisicao(long id, String metodo, String caminho, Map<String, String> consulta,
                       Map<String, String> cabecalhos, byte[] corpo, String cliente, String baseUrl,
                       Map<String, String> parametros) {
        this.id = id;
        this.metodo = metodo;
        this.caminho = caminho;
        this.consulta = Map.copyOf(consulta);
        Map<String, String> h = new HashMap<>();
        cabecalhos.forEach((k, v) -> h.put(k.toLowerCase(Locale.ROOT), v));
        this.cabecalhos = Collections.unmodifiableMap(h);
        this.corpo = corpo == null ? new byte[0] : corpo;
        this.cliente = cliente;
        this.baseUrl = baseUrl;
        this.parametros = Map.copyOf(parametros);
    }

    /** Cópia desta requisição com as variáveis extraídas do caminho (feita pelo roteador). */
    Requisicao comParametros(Map<String, String> novos) {
        return new Requisicao(id, metodo, caminho, consulta, cabecalhos, corpo, cliente, baseUrl, novos);
    }

    public long id() { return id; }
    public String metodo() { return metodo; }
    public String caminho() { return caminho; }
    public String cliente() { return cliente; }

    /** Endereço do servidor como o cliente o enxerga, ex.: {@code http://192.168.0.10:8080}. */
    public String baseUrl() { return baseUrl; }

    public String cabecalho(String nome) {
        return cabecalhos.get(nome.toLowerCase(Locale.ROOT));
    }

    /** Token do cabeçalho {@code Authorization: Bearer <token>}, ou {@code null}. */
    public String token() {
        String h = cabecalho("Authorization");
        if (h == null) return null;
        String[] p = h.trim().split("\\s+", 2);
        if (p.length == 2 && p[0].equalsIgnoreCase("Bearer") && !p[1].isBlank()) return p[1].trim();
        return null;
    }

    /** Variável do caminho (ex.: {@code id} em {@code /itens/{id}}). */
    public String parametro(String nome) {
        String v = parametros.get(nome);
        if (v == null) throw new IllegalArgumentException("Parâmetro '" + nome + "' ausente no caminho.");
        return v;
    }

    public int parametroInteiro(String nome) {
        String v = parametro(nome);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parâmetro '" + nome + "' deve ser um inteiro, mas foi '" + v + "'.");
        }
    }

    /** Parâmetro da query string ({@code ?nome=valor}) ou {@code null}. */
    public String consulta(String nome) {
        return consulta.get(nome);
    }

    public long consultaLonga(String nome, long padrao) {
        String v = consulta.get(nome);
        if (v == null || v.isBlank()) return padrao;
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O parâmetro '" + nome + "' deve ser um inteiro, mas foi '" + v + "'.");
        }
    }

    public boolean temCorpo() {
        return corpo.length > 0;
    }

    /** Corpo como objeto JSON ({@code {}} se vier vazio). */
    public ObjetoJson corpo() {
        if (corpo.length == 0) return ObjetoJson.vazio();
        try {
            return ObjetoJson.de(Json.lerBytes(corpo));
        } catch (IllegalArgumentException e) {
            throw new ErroApi(CodigoErro.REQUISICAO_INVALIDA, "Corpo da requisição inválido: " + e.getMessage());
        }
    }

    @Override
    public String toString() {
        return metodo + " " + caminho;
    }
}
