package clinica.stream.teste;

import clinica.modelo.Medicamento;
import clinica.stream.EstoqueInputStream;

// Questão 2b — origem: entrada padrão (System.in)
// Uso: java clinica.stream.teste.Q2bLerDaEntradaPadrao < estoque.txt
//  ou: java ...Q1bEnviarParaSaidaPadrao | java ...Q2bLerDaEntradaPadrao
//  ou digitando o conteúdo no teclado (cabeçalho, N e N linhas)

public class Q2bLerDaEntradaPadrao {
    public static void main(String[] args) throws Exception {
        EstoqueInputStream entrada = new EstoqueInputStream(System.in);
        Medicamento[] lidos = entrada.lerMedicamentos();
        ImprimirMedicamentos.imprimir(lidos);
    }
}
