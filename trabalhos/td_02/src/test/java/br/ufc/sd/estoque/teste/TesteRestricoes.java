package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Restrições do enunciado verificadas no código-fonte e no bytecode:
 * nenhum socket criado pela aplicação e nenhuma dependência do TD01.
 */
final class TesteRestricoes {

    // montados em tempo de execução (String.join não é constante de compilação), para que nem
    // este arquivo nem o seu .class contenham os nomes procurados
    private static final String SOCK = String.join("", "Sock", "et");
    private static final String DGRAM = String.join("", "Data", "gram");
    private static final List<String> CLASSES_PROIBIDAS = List.of(
            "java/net/" + SOCK, "java/net/Server" + SOCK, "java/net/" + DGRAM + SOCK, "java/net/Multicast" + SOCK,
            "java/net/" + DGRAM + "Packet", "java/nio/channels/" + SOCK + "Channel",
            "java/nio/channels/Server" + SOCK + "Channel", "java/nio/channels/" + DGRAM + "Channel");
    private static final Pattern FONTE_PROIBIDA = Pattern.compile(
            "\\b(Server|" + DGRAM + "|Multicast)?" + SOCK + "(Channel)?\\b|\\b" + DGRAM + "(Channel|Packet)\\b");
    private static final Pattern PACOTE_TD01 = Pattern.compile("^\\s*(import|package)\\s+clinica\\.", Pattern.MULTILINE);

    private TesteRestricoes() { }

    static void executar(Teste t) {
        Path raiz = Paths.get(System.getProperty("td02.raiz", "."));
        t.secao("Restrições do enunciado");

        t.caso("Nenhuma classe de socket aparece no código-fonte do TD02", () -> {
            List<String> ocorrencias = new ArrayList<>();
            for (Path arquivo : arquivos(raiz.resolve("src"), ".java")) {
                List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
                for (int i = 0; i < linhas.size(); i++) {
                    if (FONTE_PROIBIDA.matcher(linhas.get(i)).find()) {
                        ocorrencias.add(raiz.relativize(arquivo) + ":" + (i + 1) + "  " + linhas.get(i).trim());
                    }
                }
            }
            igual(List.of(), ocorrencias, "ocorrências de sockets");
        });

        t.caso("Nenhum .class compilado referencia classes de socket (o RMI as usa internamente, a aplicação não)", () -> {
            List<Path> classes = new ArrayList<>(arquivos(raiz.resolve("out/classes"), ".class"));
            classes.addAll(arquivos(raiz.resolve("out/test-classes"), ".class"));
            verdadeiro(classes.size() > 30, "esperava encontrar as classes compiladas em out/ (achou " + classes.size() + ")");
            List<Pattern> padroes = CLASSES_PROIBIDAS.stream()
                    .map(c -> Pattern.compile(Pattern.quote(c) + "(?![A-Za-z0-9$_])")).toList();
            List<String> ocorrencias = new ArrayList<>();
            for (Path arquivo : classes) {
                // o pool de constantes guarda como texto o nome das classes usadas (ex.: java/net/Xyz)
                String conteudo = new String(Files.readAllBytes(arquivo), StandardCharsets.ISO_8859_1);
                for (Pattern padrao : padroes) {
                    if (padrao.matcher(conteudo).find()) ocorrencias.add(raiz.relativize(arquivo) + " -> " + padrao);
                }
            }
            igual(List.of(), ocorrencias, "referências a sockets no bytecode");
        });

        t.caso("Independência do TD01: nenhum import do pacote 'clinica' e TD01 fora do classpath", () -> {
            List<String> ocorrencias = new ArrayList<>();
            for (Path arquivo : arquivos(raiz.resolve("src"), ".java")) {
                if (PACOTE_TD01.matcher(Files.readString(arquivo, StandardCharsets.UTF_8)).find()) {
                    ocorrencias.add(raiz.relativize(arquivo).toString());
                }
            }
            igual(List.of(), ocorrencias, "arquivos que usam o TD01");
            for (String classeTd01 : List.of("clinica.multicast.ServidorMulticast", "clinica.modelo.Medicamento",
                    "clinica.util.Json")) {
                try {
                    Class.forName(classeTd01);
                    throw new AssertionError("A classe do TD01 " + classeTd01 + " está no classpath do TD02.");
                } catch (ClassNotFoundException esperado) {
                    // ok: o TD02 roda sem o TD01
                }
            }
            String classpath = System.getProperty("java.class.path").replace('\\', '/');
            verdadeiro(!classpath.contains("td_01"), "classpath não deve incluir o TD01: " + classpath);
        });
    }

    private static List<Path> arquivos(Path pasta, String extensao) throws IOException {
        if (!Files.isDirectory(pasta)) return List.of();
        try (Stream<Path> s = Files.walk(pasta)) {
            return s.filter(p -> p.toString().endsWith(extensao)).toList();
        }
    }
}
