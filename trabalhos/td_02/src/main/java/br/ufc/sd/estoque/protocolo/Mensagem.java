package br.ufc.sd.estoque.protocolo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Mensagem do protocolo requisição-resposta, com os cinco campos da figura do enunciado:
 * <pre>
 * messageType      int  (0 = Request, 1 = Reply)
 * requestId        int
 * objectReference  RemoteObjectRef
 * methodId         String (nome do método)
 * arguments        array de bytes
 * </pre>
 * Numa resposta, {@code arguments} carrega o resultado (ou o erro) do método.
 */
public final class Mensagem {

    private final int messageType;
    private final int requestId;
    private final RemoteObjectRef objectReference;
    private final String methodId;
    private final byte[] arguments;

    public Mensagem(int messageType, int requestId, RemoteObjectRef objectReference, String methodId,
                    byte[] arguments) {
        if (messageType != TipoMensagem.REQUEST && messageType != TipoMensagem.REPLY) {
            throw new IllegalArgumentException("messageType inválido: " + messageType);
        }
        if (methodId == null || methodId.isBlank()) {
            throw new IllegalArgumentException("methodId não pode ser vazio.");
        }
        this.messageType = messageType;
        this.requestId = requestId;
        this.objectReference = Objects.requireNonNull(objectReference, "objectReference");
        this.methodId = methodId;
        this.arguments = Objects.requireNonNull(arguments, "arguments").clone();
    }

    public int getMessageType() { return messageType; }
    public int getRequestId() { return requestId; }
    public RemoteObjectRef getObjectReference() { return objectReference; }
    public String getMethodId() { return methodId; }
    public byte[] getArguments() { return arguments.clone(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mensagem m)) return false;
        return messageType == m.messageType && requestId == m.requestId
                && objectReference.equals(m.objectReference) && methodId.equals(m.methodId)
                && Arrays.equals(arguments, m.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageType, requestId, objectReference, methodId, Arrays.hashCode(arguments));
    }

    @Override
    public String toString() {
        return TipoMensagem.nome(messageType) + " #" + requestId + " " + objectReference.objeto() + "."
                + methodId + " " + new String(arguments, StandardCharsets.UTF_8);
    }
}
