package br.ufc.sd.estoque.protocolo;

import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.json.ObjetoJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Conteúdo do campo {@code arguments} de uma mensagem de resposta:
 * <pre>
 * {"status":"OK","resultado": ...}
 * {"status":"ERRO","erro":{"codigo":"OPERACAO_NEGADA","mensagem":"..."}}
 * </pre>
 */
public final class CargaResposta {

    private CargaResposta() { }

    public static byte[] sucesso(Object resultado) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "OK");
        m.put("resultado", resultado);
        return Json.escreverBytes(m);
    }

    public static byte[] erro(CodigoErro codigo, String mensagem) {
        Map<String, Object> erro = new LinkedHashMap<>();
        erro.put("codigo", codigo.name());
        erro.put("mensagem", mensagem == null ? codigo.name() : mensagem);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "ERRO");
        m.put("erro", erro);
        return Json.escreverBytes(m);
    }

    /**
     * Interpreta a carga de uma resposta.
     *
     * @return o resultado (valor JSON) em caso de sucesso
     * @throws ExcecaoRemota se o servidor respondeu com erro
     * @throws FalhaComunicacaoException se a carga estiver malformada
     */
    public static Object ler(byte[] carga) {
        try {
            ObjetoJson json = ObjetoJson.de(Json.lerBytes(carga));
            String status = json.texto("status");
            if ("OK".equals(status)) return json.valor("resultado");
            if ("ERRO".equals(status)) {
                ObjetoJson erro = json.objeto("erro");
                CodigoErro codigo;
                try {
                    codigo = CodigoErro.valueOf(erro.texto("codigo"));
                } catch (IllegalArgumentException desconhecido) {
                    codigo = CodigoErro.ERRO_INTERNO;
                }
                throw new ExcecaoRemota(codigo, erro.texto("mensagem"));
            }
            throw new IllegalArgumentException("status desconhecido '" + status + "'");
        } catch (ExcecaoRemota e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new FalhaComunicacaoException("Resposta malformada: " + e.getMessage(), e);
        }
    }
}
