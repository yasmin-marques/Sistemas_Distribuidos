package clinica.stream.teste;

import clinica.modelo.Medicamento;
import clinica.stream.EstoqueOutputStream;
import java.io.FileOutputStream;

// Questão 1c — destino: arquivo (FileOutputStream)
// Uso: java clinica.stream.teste.Q1cEnviarParaArquivo [arquivo] [quantidade]

public class Q1cEnviarParaArquivo {
    public static void main(String[] args) throws Exception {
        String arquivo = args.length > 0 ? args[0] : "estoque.txt";
        Medicamento[] dados = DadosExemplo.medicamentos();
        int n = DadosExemplo.quantidade(args, 1, dados.length);

        try (EstoqueOutputStream saida = new EstoqueOutputStream(new FileOutputStream(arquivo), dados, n)) {
            saida.enviar();
        }
        System.out.println(n + " medicamento(s) gravado(s) em '" + arquivo + "'.");
    }
}
