package clinica.serializacao;

// Operações remotas disponíveis (identificam o método chamado na Requisicao)
public enum Operacao {
    REGISTRAR_ANIMAL,
    BUSCAR_ANIMAL,
    LISTAR_ANIMAIS,
    REALIZAR_CONSULTA,
    HISTORICO,
    CADASTRAR_MEDICAMENTO,
    LISTAR_MEDICAMENTOS
}
