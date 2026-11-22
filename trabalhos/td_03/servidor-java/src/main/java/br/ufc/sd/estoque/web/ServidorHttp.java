package br.ufc.sd.estoque.web;

import br.ufc.sd.estoque.json.Json;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Porta servidora HTTP. Executa o ciclo requisição-resposta da figura do enunciado:
 * <pre>
 *   getRequest(exchange)         -> Requisicao   (lê método, URL, cabeçalhos e corpo JSON)
 *   roteador.despachar(req)      -> Resposta     (seleciona o objeto distribuído e executa a operação)
 *   sendReply(exchange, resposta)                (status HTTP + corpo JSON de volta ao cliente)
 * </pre>
 * Usa o servidor HTTP que já vem no JDK ({@code com.sun.net.httpserver}); a aplicação não
 * cria sockets nem usa RMI. Cada requisição roda numa thread trabalhadora, então clientes
 * diferentes são atendidos em paralelo — inclusive os que ficam esperando notificações.
 */
public final class ServidorHttp implements AutoCloseable {

    /** Limite do corpo de uma requisição. */
    public static final int TAMANHO_MAXIMO_CORPO = 1_048_576;
    /** Limite de requisições executando ao mesmo tempo (as demais esperam na fila). */
    public static final int MAXIMO_TRABALHADORES = 256;

    private final HttpServer http;
    private final Roteador roteador;
    private final Consumer<String> registro;
    private final boolean detalhado;
    private final ExecutorService trabalhadores;
    private final AtomicLong sequencia = new AtomicLong();

    public ServidorHttp(int porta, boolean somenteLocal, Roteador roteador, Consumer<String> registro,
                        boolean detalhado) throws IOException {
        this.roteador = Objects.requireNonNull(roteador);
        this.registro = Objects.requireNonNull(registro);
        this.detalhado = detalhado;
        InetSocketAddress endereco = somenteLocal
                ? new InetSocketAddress(InetAddress.getLoopbackAddress(), porta)
                : new InetSocketAddress(porta);
        this.http = HttpServer.create(endereco, 128);
        AtomicInteger contador = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(MAXIMO_TRABALHADORES, MAXIMO_TRABALHADORES, 30,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
                    Thread t = new Thread(r, "trabalhador-http-" + contador.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        pool.allowCoreThreadTimeOut(true);
        this.trabalhadores = pool;
        http.setExecutor(trabalhadores);
        http.createContext("/", this::atender);
    }

    public void iniciar() {
        http.start();
    }

    /** Porta em que o servidor realmente escuta (útil quando se pede a porta 0). */
    public int porta() {
        return http.getAddress().getPort();
    }

    // ------------------------------------------------------------------ ciclo requisição-resposta

    private void atender(HttpExchange troca) throws IOException {
        try {
            if ("OPTIONS".equals(troca.getRequestMethod())) { // pré-voo CORS dos navegadores
                responderPreVoo(troca);
                return;
            }
            Requisicao requisicao;
            Resposta resposta;
            try {
                requisicao = getRequest(troca);
                resposta = roteador.despachar(requisicao);
            } catch (ErroApi e) {
                requisicao = null;
                resposta = Roteador.erro(e);
            }
            if (requisicao != null && (detalhado || !ruidosa(requisicao))) {
                registro.accept("[" + requisicao.cliente() + "] #" + requisicao.id() + " " + requisicao + " -> "
                        + resposta.status());
            }
            sendReply(troca, resposta, requisicao == null ? null : String.valueOf(requisicao.id()));
        } catch (RuntimeException e) {
            registro.accept("[ServidorHttp] Falha ao atender: " + e);
            try {
                sendReply(troca, Roteador.erro(new ErroApi(CodigoErro.ERRO_INTERNO, "Erro interno no servidor.")), null);
            } catch (RuntimeException | IOException ignorada) {
                // a conexão já caiu
            }
        } finally {
            troca.close();
        }
    }

    /** Chamadas de espera por notificações só são registradas no modo detalhado. */
    private static boolean ruidosa(Requisicao r) {
        return r.metodo().equals("GET") && r.caminho().endsWith("/mensagens");
    }

    /** Obtém uma requisição de um cliente: desempacota método, caminho, cabeçalhos e corpo. */
    Requisicao getRequest(HttpExchange troca) throws IOException {
        Headers h = troca.getRequestHeaders();
        Map<String, String> cabecalhos = new HashMap<>();
        h.forEach((k, v) -> {
            if (!v.isEmpty()) cabecalhos.put(k, v.get(0));
        });
        byte[] corpo = lerCorpo(troca.getRequestBody());
        String host = cabecalhos.getOrDefault("Host", "localhost:" + porta());
        String idCliente = cabecalhos.get("X-Request-Id");
        long id = sequencia.incrementAndGet();
        if (idCliente != null && idCliente.matches("\\d{1,15}")) id = Long.parseLong(idCliente);
        return new Requisicao(id, troca.getRequestMethod(), troca.getRequestURI().getRawPath() == null ? "/"
                : decodificar(troca.getRequestURI().getRawPath()), consulta(troca.getRequestURI().getRawQuery()),
                cabecalhos, corpo, troca.getRemoteAddress().getAddress().getHostAddress(), "http://" + host);
    }

    /** Envia a mensagem de resposta ao cliente: status, cabeçalhos e corpo JSON. */
    void sendReply(HttpExchange troca, Resposta resposta, String idRequisicao) throws IOException {
        Headers h = troca.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Expose-Headers", "Location, X-Request-Id, Allow");
        h.set("Cache-Control", "no-store");
        if (idRequisicao != null) h.set("X-Request-Id", idRequisicao);
        resposta.cabecalhos().forEach(h::set);
        if (resposta.corpo() == null) {
            troca.sendResponseHeaders(resposta.status(), -1);
            return;
        }
        byte[] bytes = Json.escreverBytes(resposta.corpo());
        h.set("Content-Type", "application/json; charset=utf-8");
        troca.sendResponseHeaders(resposta.status(), bytes.length);
        try (OutputStream saida = troca.getResponseBody()) {
            saida.write(bytes);
        }
    }

    private static void responderPreVoo(HttpExchange troca) throws IOException {
        Headers h = troca.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id");
        h.set("Access-Control-Max-Age", "600");
        troca.sendResponseHeaders(204, -1);
    }

    private static byte[] lerCorpo(InputStream entrada) throws IOException {
        byte[] dados = entrada.readNBytes(TAMANHO_MAXIMO_CORPO + 1);
        if (dados.length > TAMANHO_MAXIMO_CORPO) {
            throw new ErroApi(CodigoErro.REQUISICAO_INVALIDA,
                    "Corpo maior que " + TAMANHO_MAXIMO_CORPO + " bytes.");
        }
        return dados;
    }

    private static Map<String, String> consulta(String bruta) {
        Map<String, String> m = new LinkedHashMap<>();
        if (bruta == null || bruta.isEmpty()) return m;
        for (String par : bruta.split("&")) {
            if (par.isEmpty()) continue;
            int i = par.indexOf('=');
            String chave = decodificar(i < 0 ? par : par.substring(0, i));
            String valor = i < 0 ? "" : decodificar(par.substring(i + 1));
            m.putIfAbsent(chave, valor);
        }
        return m;
    }

    private static String decodificar(String texto) {
        try {
            return URLDecoder.decode(texto.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ErroApi(CodigoErro.REQUISICAO_INVALIDA, "Endereço com escape inválido: " + texto);
        }
    }

    @Override
    public void close() {
        http.stop(0);
        trabalhadores.shutdownNow();
    }
}
