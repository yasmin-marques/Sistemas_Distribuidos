package clinica.multicast;

import clinica.util.Json;
import java.util.LinkedHashMap;
import java.util.Map;

// Mensagem enviada ao grupo multicast: {"tipo": ..., "mensagem": ..., "timestamp": ...}. 
public class Notificacao {

    public static final String NOTIFICACAO = "NOTIFICACAO";
    public static final String ALERTA = "ALERTA";
    public static final String ATUALIZACAO = "ATUALIZACAO";
    public static final String NOTA_INFORMATIVA = "NOTA_INFORMATIVA";

    // Limite de bytes do JSON, para caber em um único datagrama
    public static final int TAMANHO_MAXIMO = 1400;

    private final String tipo;
    private final String mensagem;
    private final long timestamp;

    public Notificacao(String tipo, String mensagem, long timestamp) {
        this.tipo = tipo;
        this.mensagem = mensagem;
        this.timestamp = timestamp;
    }

    public String getTipo() { return tipo; }
    public String getMensagem() { return mensagem; }
    public long getTimestamp() { return timestamp; }

    public String toJson() {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("tipo", tipo);
        campos.put("mensagem", mensagem);
        campos.put("timestamp", timestamp);
        return Json.escrever(campos);
    }

    public static Notificacao fromJson(String json) {
        Map<String, Object> campos = Json.lerObjeto(json);
        String tipo = Json.texto(campos, "tipo");
        String mensagem = Json.texto(campos, "mensagem");
        if (tipo == null || mensagem == null) {
            throw new IllegalArgumentException("Notificação sem tipo ou mensagem.");
        }
        return new Notificacao(tipo, mensagem, Json.inteiro(campos, "timestamp"));
    }

    @Override
    public String toString() {
        return "[" + tipo + "] " + mensagem;
    }
}
