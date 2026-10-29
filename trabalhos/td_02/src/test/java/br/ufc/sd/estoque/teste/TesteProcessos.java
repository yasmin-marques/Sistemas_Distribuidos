package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Servidor e três clientes em <b>processos (JVMs) separados</b>, usando os programas reais
 * {@code ServidorEstoque} e {@code ClienteEstoque} com a entrada do teclado simulada.
 */
final class TesteProcessos {

    private static final String SERVIDOR = "br.ufc.sd.estoque.servidor.ServidorEstoque";
    private static final String CLIENTE = "br.ufc.sd.estoque.cliente.ClienteEstoque";

    private TesteProcessos() { }

    /** Um processo filho com a saída capturada linha a linha. */
    static final class Processo implements AutoCloseable {
        final String nome;
        final Process processo;
        final List<String> linhas = new CopyOnWriteArrayList<>();
        private final PrintWriter entrada;

        Processo(String nome, String classe, String... args) throws IOException {
            this.nome = nome;
            List<String> comando = new ArrayList<>(List.of(java(), "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                    "-cp", System.getProperty("java.class.path"), classe));
            comando.addAll(List.of(args));
            ProcessBuilder pb = new ProcessBuilder(comando).redirectErrorStream(true);
            this.processo = pb.start();
            this.entrada = new PrintWriter(new OutputStreamWriter(processo.getOutputStream(), StandardCharsets.UTF_8), true);
            Thread leitor = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) linhas.add(l);
                } catch (IOException ignorado) {
                    // processo encerrado
                }
            }, "leitor-" + nome);
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

        /** Espera até que {@code trecho} apareça {@code vezes} vezes na saída. */
        void aguardar(String trecho, int vezes, long prazoMs) throws InterruptedException {
            long limite = System.currentTimeMillis() + prazoMs;
            while (contar(trecho) < vezes) {
                if (System.currentTimeMillis() > limite || (!processo.isAlive() && contar(trecho) < vezes)) {
                    throw new AssertionError(nome + ": não apareceu '" + trecho + "' (" + vezes + "x). Saída:\n  "
                            + String.join("\n  ", linhas));
                }
                Thread.sleep(50);
            }
        }

        void aguardar(String trecho) throws InterruptedException {
            aguardar(trecho, 1, 15_000);
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
        t.secao("Processos independentes: 1 servidor e 3 clientes em JVMs separadas");

        t.caso("Clientes em processos diferentes operam o estoque e recebem as notificações uns dos outros", () -> {
            try (Processo servidor = iniciarServidor()) {
                String porta = servidor.nome.substring(servidor.nome.indexOf(':') + 1);
                try (Processo vet = cliente("veterinario", "vet123", porta);
                     Processo rec = cliente("recepcao", "rec123", porta);
                     Processo est = cliente("estoquista", "est123", porta)) {

                    est.enviar("saida 1 2 teste entre processos");
                    est.aguardar("Registrado: Mov. #1");
                    for (Processo p : List.of(vet, rec, est)) {
                        p.aguardar("[ATUALIZACAO] Saída de 2 un. de 'Amoxicilina 250mg' por estoquista. Saldo: 48.");
                    }

                    vet.enviar("publicar ALERTA mensagem-do-veterinario");
                    rec.aguardar("[ALERTA] veterinario: mensagem-do-veterinario");
                    est.aguardar("[ALERTA] veterinario: mensagem-do-veterinario");

                    rec.enviar("deixar");
                    rec.aguardar("Você saiu do grupo");
                    est.enviar("publicar NOTIFICACAO enquanto-recepcao-fora");
                    vet.aguardar("enquanto-recepcao-fora");
                    Thread.sleep(1_500);
                    igual(0, rec.contar("enquanto-recepcao-fora"), "quem saiu do grupo não recebe");

                    rec.enviar("entrar");
                    rec.aguardar("Você entrou no grupo", 2, 15_000);
                    est.enviar("publicar ATUALIZACAO recepcao-de-volta");
                    rec.aguardar("recepcao-de-volta");

                    vet.enviar("membros");
                    vet.aguardar("Membros do grupo: [estoquista, recepcao, veterinario]");

                    for (Processo p : List.of(vet, rec, est)) p.enviar("sair");
                    for (Processo p : List.of(vet, rec, est)) {
                        verdadeiro(p.processo.waitFor(15, TimeUnit.SECONDS), p.nome + " deve terminar");
                        igual(0, p.processo.exitValue(), "código de saída de " + p.nome);
                    }
                }
                verdadeiro(servidor.contar("Notificacoes.sairGrupo") >= 3, "servidor registrou as saídas do grupo");
            }
        });
    }

    private static Processo iniciarServidor() throws Exception {
        for (int porta = 23_100; porta < 23_120; porta++) {
            Processo s = new Processo("servidor:" + porta, SERVIDOR, "--porta", String.valueOf(porta),
                    "--sem-console", "--monitor", "3600");
            long limite = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < limite && s.processo.isAlive() && s.contar("Servidor pronto.") == 0) {
                Thread.sleep(50);
            }
            if (s.contar("Servidor pronto.") > 0) return s;
            s.close(); // porta ocupada: tenta a próxima
        }
        throw new AssertionError("Não foi possível iniciar o servidor em nenhuma porta de teste.");
    }

    private static Processo cliente(String usuario, String senha, String porta) throws Exception {
        Processo c = new Processo(usuario, CLIENTE, "localhost", porta);
        c.enviar(usuario);
        c.enviar(senha);
        c.aguardar("Você entrou no grupo");
        return c;
    }
}
