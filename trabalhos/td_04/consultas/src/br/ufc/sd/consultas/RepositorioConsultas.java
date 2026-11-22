package br.ufc.sd.consultas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Guarda as consultas em um CSV local. O banco do Estoque é outro e Consultas não o acessa. */
public final class RepositorioConsultas {

    private static final String CABECALHO =
            "id;data_hora;animal;especie;sintomas;diagnostico;id_produto;quantidade;id_mensagem";

    /** idProduto = 0 quando a consulta não usou medicamento/vacina. */
    public record RegistroConsulta(int id, LocalDateTime dataHora, String animal, String especie,
                                   String sintomas, String diagnostico, int idProduto,
                                   int quantidade, String idMensagem) {

        @Override
        public String toString() {
            String uso = idProduto == 0 ? "nenhum produto"
                    : quantidade + "x produto #" + idProduto + " (mensagem " + idMensagem + ")";
            return "Consulta #" + id + " [" + dataHora.withNano(0) + "] " + especie + " '" + animal
                    + "' | sintomas: " + sintomas + " | diagnóstico: " + diagnostico + " | uso: " + uso;
        }
    }

    private final Path arquivo;

    public RepositorioConsultas(Path arquivo) {
        this.arquivo = arquivo;
    }

    public int proximoId() throws IOException {
        int maior = 0;
        for (RegistroConsulta c : listar()) maior = Math.max(maior, c.id());
        return maior + 1;
    }

    public void salvar(RegistroConsulta c) throws IOException {
        Files.createDirectories(arquivo.toAbsolutePath().getParent());
        List<String> linhas = new ArrayList<>();
        if (!Files.exists(arquivo)) linhas.add(CABECALHO);
        linhas.add(String.join(";", String.valueOf(c.id()), c.dataHora().withNano(0).toString(),
                limpar(c.animal()), limpar(c.especie()), limpar(c.sintomas()), limpar(c.diagnostico()),
                String.valueOf(c.idProduto()), String.valueOf(c.quantidade()), c.idMensagem()));
        Files.write(arquivo, linhas, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public List<RegistroConsulta> listar() throws IOException {
        List<RegistroConsulta> consultas = new ArrayList<>();
        if (!Files.exists(arquivo)) return consultas;
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            if (linha.isBlank() || linha.startsWith("id;")) continue;
            String[] p = linha.split(";", -1);
            consultas.add(new RegistroConsulta(Integer.parseInt(p[0]), LocalDateTime.parse(p[1]),
                    p[2], p[3], p[4], p[5], Integer.parseInt(p[6]), Integer.parseInt(p[7]), p[8]));
        }
        return consultas;
    }

    private static String limpar(String texto) {
        return texto == null ? "" : texto.replace(';', ',').replace('\r', ' ').replace('\n', ' ').strip();
    }
}
