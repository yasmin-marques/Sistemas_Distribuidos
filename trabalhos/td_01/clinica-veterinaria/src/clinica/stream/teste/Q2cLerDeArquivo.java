package clinica.stream.teste;

import clinica.stream.EstoqueInputStream;
import java.io.FileInputStream;

// Questão 2c — origem: arquivo (FileInputStream)
// Uso: java clinica.stream.teste.Q2cLerDeArquivo [arquivo]
// (gere o arquivo antes com Q1cEnviarParaArquivo)

public class Q2cLerDeArquivo {
    public static void main(String[] args) throws Exception {
        String arquivo = args.length > 0 ? args[0] : "estoque.txt";
        try (EstoqueInputStream entrada = new EstoqueInputStream(new FileInputStream(arquivo))) {
            ImprimirMedicamentos.imprimir(entrada.lerMedicamentos());
        }
    }
}
