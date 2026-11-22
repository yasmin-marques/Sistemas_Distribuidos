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
 * Restrições do enunciado verificadas no código-fonte e no bytecode do servidor: nenhum socket
 * criado pela aplicação, nenhum RMI, e o servidor é um projeto separado dos clientes.
 */
final class TesteRestricoes {

    // montados em tempo de execução (String.join não é constante de compilação), para que nem
    // este arquivo nem o seu .class contenham os nomes procurados
    private static final String SOCK = String.join("", "Sock", "et");
    private static final String DGRAM = String.join("", "Data", "gram");
    private static final String RMI = String.join("", "r", "mi");
    private static final List<String> CLASSES_PROIBIDAS = List.of(
            "java/net/" + SOCK, "java/net/Server" + SOCK, "java/net/" + DGRAM + SOCK, "java/net/Multicast" + SOCK,
            "java/net/" + DGRAM + "Packet", "java/nio/channels/" + SOCK + "Channel",
            "java/nio/channels/Server" + SOCK + "Channel", "java/nio/channels/" + DGRAM + "Channel",
            "java/" + RMI + "/");
    private static final Pattern FONTE_PROIBIDA = Pattern.compile(
            "\\b(Server|" + DGRAM + "|Multicast)?" + SOCK + "(Channel)?\\b|\\b" + DGRAM + "(Channel|Packet)\\b|java\\." + RMI + "\\b");

    private TesteRestricoes() { }

    static void executar(Teste t) {
        Path raiz = Paths.get(System.getProperty("td03.raiz", "."));
        t.secao("Restrições do enunciado");

        t.caso("Nenhuma classe de socket nem de RMI aparece no código-fonte do servidor", () -> {
            List<String> ocorrencias = new ArrayList<>();
            for (Path arquivo : arquivos(raiz.resolve("src"), ".java")) {
                List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
                for (int i = 0; i < linhas.size(); i++) {
                    if (FONTE_PROIBIDA.matcher(linhas.get(i)).find()) {
                        ocorrencias.add(raiz.relativize(arquivo) + ":" + (i + 1) + "  " + linhas.get(i).trim());
                    }
                }
            }
            igual(List.of(), ocorrencias, "ocorrências de sockets/RMI");
        });

        t.caso("Nenhum .class do servidor referencia sockets ou RMI (o servidor HTTP do JDK os usa internamente)", () -> {
            List<Path> classes = new ArrayList<>(arquivos(raiz.resolve("out/classes"), ".class"));
            verdadeiro(classes.size() > 30, "esperava encontrar as classes compiladas em out/ (achou " + classes.size() + ")");
            List<Pattern> padroes = CLASSES_PROIBIDAS.stream()
                    .map(c -> Pattern.compile(Pattern.quote(c) + (c.endsWith("/") ? "" : "(?![A-Za-z0-9$_])"))).toList();
            List<String> ocorrencias = new ArrayList<>();
            for (Path arquivo : classes) {
                // o pool de constantes guarda como texto o nome das classes usadas (ex.: java/net/Xyz)
                String conteudo = new String(Files.readAllBytes(arquivo), StandardCharsets.ISO_8859_1);
                for (Pattern padrao : padroes) {
                    if (padrao.matcher(conteudo).find()) ocorrencias.add(raiz.relativize(arquivo) + " -> " + padrao);
                }
            }
            igual(List.of(), ocorrencias, "referências a sockets/RMI no bytecode");
        });

        t.caso("Servidor e clientes são projetos separados: o servidor não depende de nenhum cliente", () -> {
            Path projetos = raiz.toAbsolutePath().normalize().getParent();
            verdadeiro(projetos != null, "pasta que contém os projetos");
            for (String cliente : List.of("cliente-python", "cliente-javascript")) {
                Path pasta = projetos.resolve(cliente);
                verdadeiro(Files.isDirectory(pasta), "projeto " + cliente + " ao lado do servidor (" + pasta + ")");
                verdadeiro(!pasta.startsWith(raiz.toAbsolutePath().normalize()), cliente + " não fica dentro do servidor");
            }
            String classpath = System.getProperty("java.class.path").replace('\\', '/');
            verdadeiro(!classpath.contains("cliente-"), "classpath do servidor não inclui clientes: " + classpath);
        });
    }

    private static List<Path> arquivos(Path pasta, String extensao) throws IOException {
        if (!Files.isDirectory(pasta)) return List.of();
        try (Stream<Path> s = Files.walk(pasta)) {
            return s.filter(p -> p.toString().endsWith(extensao)).toList();
        }
    }
}
