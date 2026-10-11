package clinica.stream.teste;

import clinica.modelo.Medicamento;

// Utilitário de impressão usado pelos testes de leitura
final class ImprimirMedicamentos {

    private ImprimirMedicamentos() { }

    static void imprimir(Medicamento[] lidos) {
        System.out.println("Recebidos " + lidos.length + " medicamento(s):");
        for (Medicamento m : lidos) {
            System.out.println("  " + m);
        }
    }
}
