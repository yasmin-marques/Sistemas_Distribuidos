package clinica.stream.teste;

import clinica.modelo.Medicamento;
import clinica.stream.EstoqueOutputStream;

// Questão 1b — destino: saída padrão (System.out)
// Uso: java clinica.stream.teste.Q1bEnviarParaSaidaPadrao [quantidade]
public class Q1bEnviarParaSaidaPadrao {
    public static void main(String[] args) throws Exception {
        Medicamento[] dados = DadosExemplo.medicamentos();
        int n = DadosExemplo.quantidade(args, 0, dados.length);

        EstoqueOutputStream saida = new EstoqueOutputStream(System.out, dados, n);
        saida.enviar();   // não fechamos System.out
    }
}
