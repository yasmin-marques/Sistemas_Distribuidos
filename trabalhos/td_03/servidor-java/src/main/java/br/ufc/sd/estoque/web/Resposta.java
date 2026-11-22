package br.ufc.sd.estoque.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mensagem de resposta (o {@code reply} do protocolo requisição-resposta): status HTTP,
 * cabeçalhos e, opcionalmente, um corpo na representação externa (valor JSON).
 */
public record Resposta(int status, Object corpo, Map<String, String> cabecalhos) {

    public Resposta {
        cabecalhos = Map.copyOf(cabecalhos);
    }

    public static Resposta ok(Object corpo) {
        return new Resposta(200, corpo, Map.of());
    }

    /** 201 Created, com o endereço do recurso criado no cabeçalho {@code Location}. */
    public static Resposta criado(String location, Object corpo) {
        Map<String, String> h = new LinkedHashMap<>();
        if (location != null) h.put("Location", location);
        return new Resposta(201, corpo, h);
    }

    public static Resposta semConteudo() {
        return new Resposta(204, null, Map.of());
    }

    public Resposta comCabecalho(String nome, String valor) {
        Map<String, String> h = new LinkedHashMap<>(cabecalhos);
        h.put(nome, valor);
        return new Resposta(status, corpo, h);
    }
}
