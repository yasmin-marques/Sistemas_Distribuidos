package br.ufc.sd.estoque.protocolo;

import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.json.ObjetoJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Empacota e desempacota {@link Mensagem}s na representação externa (JSON em UTF-8).
 *
 * <p>Os bytes de {@code arguments} são, por convenção da aplicação, um documento JSON;
 * por isso o campo é embutido como JSON na mensagem (e não como base64), o que deixa o
 * tráfego legível. Exemplo de requisição:
 * <pre>
 * {"messageType":0,"requestId":7,
 *  "objectReference":{"host":"localhost","porta":1099,"servico":"ClinicaVeterinaria",
 *                     "objeto":"Estoque","interface":"EstoqueRemoto"},
 *  "methodId":"registrarSaida",
 *  "arguments":{"token":"...","idItem":3,"quantidade":2,"motivo":"consulta"}}
 * </pre>
 */
public final class CodecMensagem {

    /** Tamanho máximo de uma mensagem empacotada (1 MiB). */
    public static final int TAMANHO_MAXIMO = 1024 * 1024;

    private CodecMensagem() { }

    public static byte[] empacotar(Mensagem m) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("messageType", m.getMessageType());
        json.put("requestId", m.getRequestId());
        json.put("objectReference", m.getObjectReference().paraJson());
        json.put("methodId", m.getMethodId());
        json.put("arguments", Json.lerBytes(m.getArguments()));
        byte[] dados = Json.escreverBytes(json);
        if (dados.length > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Mensagem excede " + TAMANHO_MAXIMO + " bytes.");
        }
        return dados;
    }

    /** @throws IllegalArgumentException se os bytes não formarem uma mensagem válida. */
    public static Mensagem desempacotar(byte[] dados) {
        if (dados == null || dados.length == 0) throw new IllegalArgumentException("Mensagem vazia.");
        if (dados.length > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("Mensagem excede " + TAMANHO_MAXIMO + " bytes.");
        }
        ObjetoJson json = ObjetoJson.de(Json.lerBytes(dados));
        if (!json.mapa().containsKey("arguments")) {
            throw new IllegalArgumentException("Mensagem sem o campo 'arguments'.");
        }
        return new Mensagem(
                json.inteiro("messageType"),
                json.inteiro("requestId"),
                RemoteObjectRef.deJson(json.valor("objectReference")),
                json.texto("methodId"),
                Json.escreverBytes(json.valor("arguments")));
    }
}
