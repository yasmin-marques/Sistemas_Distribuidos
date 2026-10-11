package clinica.stream.teste;

import clinica.modelo.Medicamento;
import java.time.LocalDate;

// Dados usados pelos programas de teste das Questões 1 e 2
final class DadosExemplo {

    private DadosExemplo() { }

    static Medicamento[] medicamentos() {
        return new Medicamento[] {
            new Medicamento(1, "Amoxicilina 250mg", "VetPharma", 50, 35.90, LocalDate.of(2028, 6, 30)),
            new Medicamento(2, "Vermífugo Oral", "PetSaude", 30, 22.50, LocalDate.of(2028, 1, 15)),
            // nome com ';' e '\' para exercitar o escape do protocolo
            new Medicamento(3, "Dipirona; gotas 500mg \\ 20ml", "Vet;Lab", 12, 18.00, LocalDate.of(2027, 12, 1)),
            new Medicamento(4, "Anti-inflamatório 10mg", "VetPharma", 5, 48.00, LocalDate.of(2028, 3, 20)),
        };
    }

    // Lê opcionalmente o número de objetos a enviar (args[indice]); padrão: todos
    static int quantidade(String[] args, int indice, int total) {
        if (args.length > indice) {
            int n = Integer.parseInt(args[indice]);
            if (n < 0 || n > total) {
                throw new IllegalArgumentException("Quantidade deve estar entre 0 e " + total);
            }
            return n;
        }
        return total;
    }
}
