package br.ufc.quixada.sd.multicast;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Mensagem enviada pelo grupo multicast
 
public class NotificacaoMulticast implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TIPO_NOTIFICACAO = "NOTIFICACAO";
    public static final String TIPO_ALERTA = "ALERTA";
    public static final String TIPO_ATUALIZACAO = "ATUALIZACAO";

    private String tipo;
    private String mensagem;
    private long timestamp;

    public NotificacaoMulticast() {
    }

    public NotificacaoMulticast(String tipo, String mensagem, long timestamp) {
        this.tipo = tipo;
        this.mensagem = mensagem;
        this.timestamp = timestamp;
    }

    public String getTipo() {
        return tipo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String toJson() {
        return "{\"tipo\":\"" + escapar(tipo) + "\",\"mensagem\":\"" + escapar(mensagem)
                + "\",\"timestamp\":" + timestamp + "}";
    }

    public static NotificacaoMulticast fromJson(String json) {
        String tipo = extrairString(json, "tipo");
        String mensagem = extrairString(json, "mensagem");
        long timestamp = Long.parseLong(extrairNumero(json, "timestamp"));
        return new NotificacaoMulticast(tipo, mensagem, timestamp);
    }

    private static String escapar(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extrairString(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("Campo '" + campo + "' não encontrado no JSON: " + json);
        }
        return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String extrairNumero(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!m.find()) {
            throw new IllegalArgumentException("Campo '" + campo + "' não encontrado no JSON: " + json);
        }
        return m.group(1);
    }

    @Override
    public String toString() {
        return "[" + tipo + "] " + mensagem;
    }
}
