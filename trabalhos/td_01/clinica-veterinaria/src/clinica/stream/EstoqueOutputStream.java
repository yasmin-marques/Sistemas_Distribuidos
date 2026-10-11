package clinica.stream;

import clinica.modelo.Medicamento;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

// Questão 1 - OutputStream do POJO Estoque

public class EstoqueOutputStream extends OutputStream {

    // Primeira linha de toda mensagem; também serve para validar a origem
    static final String CABECALHO = "ESTOQUE-V1";

    private final OutputStream destino;
    private final Medicamento[] medicamentos;
    private final int quantidade;

    /**
    @param destino      stream para onde as informações serão enviadas
    @param medicamentos array com os objetos a transmitir
    @param quantidade   número de objetos (a partir do índice 0) que serão enviados
     */
    public EstoqueOutputStream(OutputStream destino, Medicamento[] medicamentos, int quantidade) {
        if (destino == null) throw new IllegalArgumentException("Destino não pode ser nulo.");
        if (medicamentos == null) throw new IllegalArgumentException("Array não pode ser nulo.");
        if (quantidade < 0 || quantidade > medicamentos.length) {
            throw new IllegalArgumentException("Quantidade (" + quantidade
                    + ") deve estar entre 0 e " + medicamentos.length + ".");
        }
        for (int i = 0; i < quantidade; i++) {
            if (medicamentos[i] == null) {
                throw new IllegalArgumentException("Elemento " + i + " do array é nulo.");
            }
        }
        this.destino = destino;
        this.medicamentos = medicamentos;
        this.quantidade = quantidade;
    }

    /** Codifica os {@code quantidade} primeiros medicamentos e envia ao destino. */
    public void enviar() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(CABECALHO).append('\n');
        sb.append(quantidade).append('\n');
        for (int i = 0; i < quantidade; i++) {
            sb.append(CodecMedicamento.codificar(medicamentos[i])).append('\n');
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        write(bytes, 0, bytes.length);
        flush();
    }

    @Override
    public void write(int b) throws IOException {
        destino.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        destino.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        destino.flush();
    }

    @Override
    public void close() throws IOException {
        destino.close();
    }
}
