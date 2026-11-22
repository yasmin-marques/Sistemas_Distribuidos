package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.teste.Apoio.Api;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O programa real do servidor ({@code ServidorEstoque.main}) rodando <b>num processo (JVM) separado</b>,
 * atendido por clientes HTTP e controlado pelo console. Os clientes Python e JavaScript são testados
 * contra um servidor assim pelo script {@code testes} da raiz do projeto.
 */
final class TesteProcessos {

    private static final String SERVIDOR = "br.ufc.sd.estoque.servidor.ServidorEstoque";
    private static final Pattern PORTA = Pattern.compile("porta (\\d+)");

    private TesteProcessos() { }

    /** Um processo filho com a saída capturada linha a linha. */
    static final class Processo implements AutoCloseable {
        final Process processo;
        final List<String> linhas = new CopyOnWriteArrayList<>();
        private final PrintWriter entrada;

        Processo(String classe, String... args) throws IOException {
            List<String> comando = new ArrayList<>(List.of(java(), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-cp", System.getProperty("java.class.path"), classe));
            comando.addAll(List.of(args));
            this.processo = new ProcessBuilder(comando).redirectErrorStream(true).start();
            this.entrada = new PrintWriter(new OutputStreamWriter(processo.getOutputStream(), StandardCharsets.UTF_8), true);
            Thread leitor = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) linhas.add(l);
                } catch (IOException ignorado) {
                    // processo encerrado
                }
            }, "leitor-servidor");
            leitor.setDaemon(true);
            leitor.start();
        }

        private static String java() {
            return ProcessHandle.current().info().command().orElse("java");
        }

        void enviar(String linha) {
            entrada.println(linha);
        }

        int contar(String trecho) {
            int n = 0;
            for (String l : linhas) if (l.contains(trecho)) n++;
            return n;
        }

        void aguardar(String trecho) throws InterruptedException {
            long limite = System.currentTimeMillis() + 15_000;
            while (contar(trecho) < 1) {
                if (System.currentTimeMillis() > limite || !processo.isAlive()) {
                    throw new AssertionError("não apareceu '" + trecho + "'. Saída:\n  " + String.join("\n  ", linhas));
                }
                Thread.sleep(50);
            }
        }

        @Override
        public void close() {
            entrada.close();
            processo.destroy();
            try {
                if (!processo.waitFor(5, TimeUnit.SECONDS)) processo.destroyForcibly();
            } catch (InterruptedException e) {
                processo.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    static void executar(Teste t) {
        t.secao("Processo independente: o servidor real em outra JVM");

        t.caso("Clientes HTTP falam com o servidor de outro processo e o console do servidor avisa o grupo", () -> {
            try (Processo servidor = new Processo(SERVIDOR, "--porta", "0", "--monitor", "3600")) {
                servidor.aguardar("Servidor pronto.");
                String porta = null;
                for (String l : servidor.linhas) {
                    Matcher m = PORTA.matcher(l);
                    if (m.find()) porta = m.group(1);
                }
                verdadeiro(porta != null, "porta informada na saída do servidor: " + servidor.linhas);
                Api a = new Api("http://localhost:" + porta).entrar("estoquista");
                Api b = new Api("http://localhost:" + porta).entrar("veterinario");

                ObjetoJson ref = b.exigir(201, b.post("/api/notificacoes/assinaturas", null)).objeto();
                Apoio.Caixa caixa = new Apoio.Caixa(b, ref.texto("href"));

                a.exigir(201, a.post("/api/estoque/itens/1/saidas", Map.of("quantidade", 2, "motivo", "teste entre processos")));
                caixa.esperar(n -> n.texto("mensagem").contains("Saída de 2 un. de 'Amoxicilina 250mg' por estoquista. Saldo: 48."));

                servidor.enviar("baixar 1 3 baixa pelo console");        // o operador age no próprio servidor
                caixa.esperar(n -> n.texto("tipo").equals("ATUALIZACAO") && n.texto("mensagem").contains("Saldo: 45."));
                igual(45, a.get("/api/estoque/itens/1").objeto().inteiro("quantidade"), "o console alterou o estoque");

                servidor.enviar("ALERTA manutenção às 18h");
                caixa.esperar(n -> n.texto("tipo").equals("ALERTA") && n.texto("mensagem").equals("manutenção às 18h"));

                servidor.enviar("sair");
                verdadeiro(servidor.processo.waitFor(15, TimeUnit.SECONDS), "o servidor deve encerrar com 'sair'");
                igual(0, servidor.processo.exitValue(), "código de saída do servidor");
            }
        });
    }
}
