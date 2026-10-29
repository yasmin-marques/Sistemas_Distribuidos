package br.ufc.sd.estoque.protocolo;

/** Códigos de erro que podem voltar numa mensagem de resposta. */
public enum CodigoErro {
    /** A mensagem não pôde ser desempacotada ou não é uma requisição. */
    REQUISICAO_INVALIDA,
    /** A referência não corresponde a nenhum objeto da tabela do servidor. */
    OBJETO_INEXISTENTE,
    /** O objeto existe mas não oferece o método pedido. */
    METODO_INEXISTENTE,
    /** Argumento ausente, de tipo errado ou com valor inválido. */
    ARGUMENTO_INVALIDO,
    /** A operação violaria uma regra de negócio (ex.: estoque insuficiente). */
    OPERACAO_NEGADA,
    /** Token ausente, inválido ou credenciais incorretas. */
    NAO_AUTENTICADO,
    /** Falha inesperada no servidor. */
    ERRO_INTERNO
}
