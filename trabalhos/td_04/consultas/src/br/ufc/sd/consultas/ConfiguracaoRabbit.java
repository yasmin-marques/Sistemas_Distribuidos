package br.ufc.sd.consultas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Único endereço de rede que Consultas conhece: o do broker. Não existe IP, porta ou banco do
 * Estoque aqui (desacoplamento espacial). Lê o .env; variável de ambiente tem prioridade.
 */
public record ConfiguracaoRabbit(String host, int porta, String usuario, String senha,
                                 String vhost, String fila) {

    public static ConfiguracaoRabbit carregar(Path arquivoEnv) throws IOException {
        Map<String, String> arquivo = new HashMap<>();
        if (arquivoEnv != null && Files.isRegularFile(arquivoEnv)) {
            for (String linha : Files.readAllLines(arquivoEnv, StandardCharsets.UTF_8)) {
                linha = linha.strip();
                int igual = linha.indexOf('=');
                if (linha.isEmpty() || linha.startsWith("#") || igual <= 0) continue;
                arquivo.put(linha.substring(0, igual).strip(), linha.substring(igual + 1).strip());
            }
        }
        String porta = valor(arquivo, "RABBITMQ_PORT", "5672");
        int numeroPorta;
        try {
            numeroPorta = Integer.parseInt(porta);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("RABBITMQ_PORT inválida: '" + porta + "'.");
        }
        return new ConfiguracaoRabbit(
                valor(arquivo, "RABBITMQ_HOST", "localhost"),
                numeroPorta,
                valor(arquivo, "RABBITMQ_USER", "clinica"),
                valor(arquivo, "RABBITMQ_PASSWORD", "clinica123"),
                valor(arquivo, "RABBITMQ_VHOST", "/"),
                valor(arquivo, "RABBITMQ_QUEUE", "estoque.baixas"));
    }

    private static String valor(Map<String, String> arquivo, String chave, String padrao) {
        String ambiente = System.getenv(chave);
        if (ambiente != null && !ambiente.isBlank()) return ambiente.strip();
        String doArquivo = arquivo.get(chave);
        return doArquivo == null || doArquivo.isEmpty() ? padrao : doArquivo;
    }

    /** Texto para log, sem a senha. */
    public String descricao() {
        return host + ":" + porta + " (vhost '" + vhost + "', fila '" + fila + "')";
    }
}
