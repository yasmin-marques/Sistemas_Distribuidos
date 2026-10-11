package clinica.serializacao;

import java.io.Serializable;

// Mensagem de request: qual operação chamar e com quais argumentos
public class Requisicao implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Operacao operacao;
    private final Serializable[] argumentos;

    public Requisicao(Operacao operacao, Serializable... argumentos) {
        this.operacao = operacao;
        this.argumentos = argumentos;
    }

    public Operacao getOperacao() { return operacao; }
    public Serializable[] getArgumentos() { return argumentos; }

    @Override
    public String toString() {
        return "Requisicao[" + operacao + ", " + argumentos.length + " argumento(s)]";
    }
}
