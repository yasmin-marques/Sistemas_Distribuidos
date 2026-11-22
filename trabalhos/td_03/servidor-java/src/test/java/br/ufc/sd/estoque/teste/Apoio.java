package br.ufc.sd.estoque.teste;

import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.servidor.ServidorEstoque;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Utilitários compartilhados pelos testes. */
final class Apoio {

    private Apoio() { }

    /** Relógio que o teste pode adiantar (para simular o passar dos dias e o vencimento). */
    static final class RelogioAjustavel extends Clock {
        private volatile Instant agora;
        private final ZoneId zona;

        RelogioAjustavel() {
            this(Instant.now(), ZoneId.systemDefault());
        }

        private RelogioAjustavel(Instant agora, ZoneId zona) {
            this.agora = agora;
            this.zona = zona;
        }

        void avancarDias(long dias) {
            agora = agora.plus(Duration.ofDays(dias));
        }

        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId z) { return new RelogioAjustavel(agora, z); }
        @Override public Instant instant() { return agora; }
    }

    /** Inicia um servidor real numa porta livre (porta 0: o sistema escolhe). */
    static ServidorEstoque servidor(Clock relogio, long leaseMs) throws IOException {
        return ServidorEstoque.iniciar(new ServidorEstoque.Configuracao(0, true, 3_600_000, leaseMs, false,
                relogio, msg -> { }));
    }

    static ServidorEstoque servidor() throws IOException {
        return servidor(Clock.systemDefaultZone(), 30_000);
    }

    /**
     * Cliente HTTP de teste. Fala com o servidor só pelo contrato da API (HTTP + JSON), como os
     * clientes Python e JavaScript do projeto.
     */
    static final class Api {
        private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        final String base;
        String token;

        Api(ServidorEstoque servidor) {
            this("http://localhost:" + servidor.porta());
        }

        Api(String base) {
            this.base = base;
        }

        /** Resposta HTTP: status, cabeçalhos e corpo JSON já interpretado. */
        record Resultado(int status, String texto, Map<String, java.util.List<String>> cabecalhos) {
            Object json() { return texto.isEmpty() ? null : Json.ler(texto); }
            ObjetoJson objeto() { return ObjetoJson.de(json()); }
            @SuppressWarnings("unchecked") List<Object> lista() { return (List<Object>) json(); }
            String cabecalho(String nome) {
                return cabecalhos.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(nome))
                        .map(e -> e.getValue().get(0)).findFirst().orElse(null);
            }
            String codigoErro() { return objeto().objeto("erro").texto("codigo"); }
            String mensagemErro() { return objeto().objeto("erro").texto("mensagem"); }
        }

        /** Faz a chamada; {@code caminho} pode ser relativo ({@code /api/...}) ou uma URL completa. */
        Resultado chamar(String metodo, String caminho, Object corpo, String tokenUsado) {
            String url = caminho.startsWith("http") ? caminho : base + caminho;
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
            if (tokenUsado != null) b.header("Authorization", "Bearer " + tokenUsado);
            if (corpo == null) {
                b.method(metodo, HttpRequest.BodyPublishers.noBody());
            } else {
                byte[] bytes = corpo instanceof byte[] cru ? cru : Json.escreverBytes(corpo);
                b.header("Content-Type", "application/json").method(metodo, HttpRequest.BodyPublishers.ofByteArray(bytes));
            }
            try {
                HttpResponse<byte[]> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
                return new Resultado(r.statusCode(), new String(r.body(), StandardCharsets.UTF_8), r.headers().map());
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        Resultado get(String caminho) { return chamar("GET", caminho, null, null); }
        Resultado post(String caminho, Object corpo) { return chamar("POST", caminho, corpo, token); }
        Resultado delete(String caminho) { return chamar("DELETE", caminho, null, token); }

        /** Autentica e guarda o token para as próximas chamadas. */
        Api entrar(String usuario) {
            Resultado r = chamar("POST", "/api/autenticacao/sessoes",
                    Map.of("usuario", usuario, "senha", senha(usuario)), null);
            Teste.igual(201, r.status(), "login de " + usuario + ": " + r.texto());
            token = r.objeto().texto("token");
            return this;
        }

        /** Exige o status e devolve o resultado. */
        Resultado exigir(int status, Resultado r) {
            Teste.igual(status, r.status(), "status HTTP (corpo: " + r.texto() + ")");
            return r;
        }

        /** Confere status e código do erro devolvido. */
        Resultado erro(int status, String codigo, Resultado r) {
            Teste.igual(status, r.status(), "status HTTP (corpo: " + r.texto() + ")");
            Teste.igual(codigo, r.codigoErro(), "código do erro (" + r.texto() + ")");
            return r;
        }

        private static String senha(String usuario) {
            return switch (usuario) {
                case "veterinario" -> "vet123";
                case "recepcao" -> "rec123";
                default -> "est123";
            };
        }
    }

    /**
     * Caixa de entrada de um membro do grupo: busca no servidor, pela referência da assinatura,
     * e guarda o que ainda não foi conferido (uma busca pode trazer várias notificações).
     */
    static final class Caixa {
        private final Api api;
        private final String hrefAssinatura;
        private final List<ObjetoJson> pendentes = new ArrayList<>();
        private final List<ObjetoJson> todas = new ArrayList<>();

        Caixa(Api api, String hrefAssinatura) {
            this.api = api;
            this.hrefAssinatura = hrefAssinatura;
        }

        private void buscar(long ms) {
            Api.Resultado r = api.get(hrefAssinatura + "/mensagens?espera=" + ms);
            api.exigir(200, r);
            for (Object o : r.lista()) {
                ObjetoJson n = ObjetoJson.de(o);
                pendentes.add(n);
                todas.add(n);
            }
        }

        /** Espera (até 5 s) uma notificação que satisfaça o critério e a retira da caixa. */
        ObjetoJson esperar(Predicate<ObjetoJson> criterio) {
            long limite = System.currentTimeMillis() + 5_000;
            while (true) {
                for (Iterator<ObjetoJson> it = pendentes.iterator(); it.hasNext(); ) {
                    ObjetoJson n = it.next();
                    if (criterio.test(n)) {
                        it.remove();
                        return n;
                    }
                }
                long resta = limite - System.currentTimeMillis();
                if (resta <= 0) throw new AssertionError("Notificação esperada não chegou em 5 s. Recebidas: " + todas);
                buscar(Math.min(2_000, resta));
            }
        }

        /** Tudo o que chegar durante {@code ms} (além do que já está na caixa). */
        List<ObjetoJson> coletar(long ms) {
            long limite = System.currentTimeMillis() + ms;
            long resta;
            while ((resta = limite - System.currentTimeMillis()) > 0) buscar(resta);
            List<ObjetoJson> r = new ArrayList<>(pendentes);
            pendentes.clear();
            return r;
        }
    }
}
