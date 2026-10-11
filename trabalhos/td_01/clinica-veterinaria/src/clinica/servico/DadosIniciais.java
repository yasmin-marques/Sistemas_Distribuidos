package clinica.servico;

import clinica.modelo.Medicamento;
import java.time.LocalDate;

// Estoque inicial usado pelos servidores de demonstração
public final class DadosIniciais {

    private DadosIniciais() { }

    public static void semearEstoque(EstoqueServico estoque) {
        estoque.cadastrar(new Medicamento(0, "Amoxicilina 250mg", "VetPharma", 50, 35.90, LocalDate.of(2030, 6, 30)));
        estoque.cadastrar(new Medicamento(0, "Vermífugo Oral", "PetSaude", 30, 22.50, LocalDate.of(2030, 1, 15)));
        estoque.cadastrar(new Medicamento(0, "Anti-inflamatório 10mg", "VetPharma", 5, 48.00, LocalDate.of(2030, 3, 20)));
        estoque.cadastrar(new Medicamento(0, "Pomada cicatrizante", "DermaVet", 8, 27.00, LocalDate.of(2025, 1, 1))); // vencida
    }
}
