package clinica.stream;

import clinica.modelo.Medicamento;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;

/** QUESTÃO 2 - Lê os dados gerados por {@link EstoqueOutputStream} e reconstrói o array de Medicamento. */
public class EstoqueInputStream extends InputStream {

    private static final int TAMANHO_MAXIMO_LINHA = 64 * 1024;
    private static final int MAXIMO_OBJETOS = 1_000_000;

    private final InputStream origem;

    /** @param origem stream de onde as sequências de bytes serão lidas */
    public EstoqueInputStream(InputStream origem) {
        if (origem == null) throw new IllegalArgumentException("Origem não pode ser nula.");
        this.origem = (origem instanceof BufferedInputStream) ? origem : new BufferedInputStream(origem);
    }

    // Lê uma mensagem completa e devolve os medicamentos nela contidos
    public Medicamento[] lerMedicamentos() throws IOException {
        String cabecalho = lerLinha();
        if (cabecalho == null) throw new EOFException("Stream vazio: cabeçalho não recebido.");
        if (!EstoqueOutputStream.CABECALHO.equals(cabecalho)) {
            throw new StreamCorruptedException("Cabeçalho inválido: '" + cabecalho + "'");
        }

        String linhaN = lerLinha();
        if (linhaN == null) throw new EOFException("Quantidade de objetos não recebida.");
        int n;
        try {
            n = Integer.parseInt(linhaN.trim());
        } catch (NumberFormatException e) {
            throw new StreamCorruptedException("Quantidade inválida: '" + linhaN + "'");
        }
        if (n < 0 || n > MAXIMO_OBJETOS) {
            throw new StreamCorruptedException("Quantidade fora do limite: " + n);
        }

        Medicamento[] resultado = new Medicamento[n];
        for (int i = 0; i < n; i++) {
            String linha = lerLinha();
            if (linha == null) {
                throw new EOFException("Stream terminou após " + i + " de " + n + " objetos.");
            }
            resultado[i] = CodecMedicamento.decodificar(linha);
        }
        return resultado;
    }

    // Lê bytes até '\n' (ignorando '\r' final) e decodifica como UTF-8.
    private String lerLinha() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = read()) != -1) {
            if (b == '\n') {
                return decodificar(buffer);
            }
            buffer.write(b);
            if (buffer.size() > TAMANHO_MAXIMO_LINHA) {
                throw new StreamCorruptedException("Linha maior que o limite permitido.");
            }
        }
        if (buffer.size() == 0) return null;     // fim do stream sem dados
        throw new EOFException("Stream terminou no meio de uma linha.");
    }

    private static String decodificar(ByteArrayOutputStream buffer) {
        String s = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
    }

    @Override
    public int read() throws IOException {
        return origem.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return origem.read(b, off, len);
    }

    @Override
    public int available() throws IOException {
        return origem.available();
    }

    @Override
    public void close() throws IOException {
        origem.close();
    }
}
