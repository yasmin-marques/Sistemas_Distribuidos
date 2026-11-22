package br.ufc.sd.estoque.web;

/**
 * Códigos de erro da API. Cada um corresponde a um status HTTP; o código textual vai no
 * corpo da resposta ({@code {"erro":{"codigo":"OPERACAO_NEGADA",...}}}), igual ao do TD02.
 */
public enum CodigoErro {
    /** Requisição malformada (JSON inválido, corpo grande demais...). */
    REQUISICAO_INVALIDA(400),
    /** Argumento ausente, de tipo errado ou com valor inválido. */
    ARGUMENTO_INVALIDO(400),
    /** Token ausente/inválido ou credenciais incorretas. */
    NAO_AUTENTICADO(401),
    /** O objeto remoto ou o recurso não existe (ou já foi encerrado). */
    OBJETO_INEXISTENTE(404),
    /** O objeto existe mas não aceita esse método HTTP. */
    METODO_INEXISTENTE(405),
    /** A operação violaria uma regra de negócio (ex.: estoque insuficiente). */
    OPERACAO_NEGADA(409),
    /** Falha inesperada no servidor. */
    ERRO_INTERNO(500);

    private final int status;

    CodigoErro(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
