package clinica.serializacao;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;

// Empacotamento e desempacotamento das mensagens.
// O desempacotamento só aceita classes da aplicação e tipos básicos do JDK
public final class Empacotador {

    private static final int TAMANHO_MAXIMO_MENSAGEM = 16 * 1024 * 1024;

    private static final ObjectInputFilter FILTRO = ObjectInputFilter.Config.createFilter(
            "clinica.**;java.io.Serializable;java.lang.*;java.util.*;java.time.*;!*");

    private Empacotador() { }

    public static byte[] empacotar(Serializable objeto) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(objeto);
        }
        return bytes.toByteArray();
    }

    public static <T> T desempacotar(byte[] dados, Class<T> tipo) throws IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(dados))) {
            ois.setObjectInputFilter(FILTRO);
            Object objeto = ois.readObject();
            if (!tipo.isInstance(objeto)) {
                throw new StreamCorruptedException("Esperava " + tipo.getSimpleName()
                        + " mas chegou " + (objeto == null ? "null" : objeto.getClass().getName()));
            }
            return tipo.cast(objeto);
        } catch (ClassNotFoundException e) {
            throw new InvalidClassException("Classe desconhecida na mensagem: " + e.getMessage());
        }
    }

    // Envia uma mensagem já empacotada: [tamanho (int)] [bytes]
    public static void enviar(DataOutputStream out, byte[] dados) throws IOException {
        out.writeInt(dados.length);
        out.write(dados);
        out.flush();
    }

    // Recebe uma mensagem empacotada. Lança EOFException se a conexão foi encerrada
    public static byte[] receber(DataInputStream in) throws IOException {
        int tamanho = in.readInt();
        if (tamanho < 0 || tamanho > TAMANHO_MAXIMO_MENSAGEM) {
            throw new StreamCorruptedException("Tamanho de mensagem inválido: " + tamanho);
        }
        byte[] dados = new byte[tamanho];
        in.readFully(dados);
        return dados;
    }
}
