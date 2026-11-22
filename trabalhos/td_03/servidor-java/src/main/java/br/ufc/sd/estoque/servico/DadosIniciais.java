package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.modelo.Fornecedor;
import br.ufc.sd.estoque.modelo.Insumo;
import br.ufc.sd.estoque.modelo.Medicamento;
import br.ufc.sd.estoque.modelo.Vacina;
import java.time.LocalDate;

/**
 * Estoque inicial de demonstração. Os quatro primeiros medicamentos são os do TD01; o
 * anti-inflamatório (estoque baixo), a pomada e a vacina V10 (validade próxima) disparam alertas.
 */
public final class DadosIniciais {

    public static final String RESPONSAVEL = "carga-inicial";

    private DadosIniciais() { }

    public static void semear(EstoqueServico estoque) {
        Fornecedor vetPharma = estoque.cadastrarFornecedor(new Fornecedor(0, "VetPharma", "11.111.111/0001-11", "(85) 3333-1001"));
        Fornecedor petSaude = estoque.cadastrarFornecedor(new Fornecedor(0, "PetSaude", "22.222.222/0001-22", "(85) 3333-1002"));
        Fornecedor dermaVet = estoque.cadastrarFornecedor(new Fornecedor(0, "DermaVet", "33.333.333/0001-33", "(85) 3333-1003"));
        Fornecedor medCirurgica = estoque.cadastrarFornecedor(new Fornecedor(0, "MedCirurgica", "44.444.444/0001-44", "(85) 3333-1004"));

        LocalDate hoje = estoque.hoje();
        // 1..4: medicamentos do TD01. No TD01 a pomada já estava vencida; aqui o cadastro recusa
        // item vencido, então ela entra a 3 dias do vencimento (gera o alerta de validade próxima).
        estoque.cadastrarItemComo(RESPONSAVEL, new Medicamento(0, "Amoxicilina 250mg", vetPharma, 50, 10, 35.90,
                LocalDate.of(2030, 6, 30), "Amoxicilina", "250 mg", false));
        estoque.cadastrarItemComo(RESPONSAVEL, new Medicamento(0, "Vermífugo Oral", petSaude, 30, 10, 22.50,
                LocalDate.of(2030, 1, 15), "Praziquantel", "50 mg", false));
        estoque.cadastrarItemComo(RESPONSAVEL, new Medicamento(0, "Anti-inflamatório 10mg", vetPharma, 5, 10, 48.00,
                LocalDate.of(2030, 3, 20), "Meloxicam", "10 mg", false));
        estoque.cadastrarItemComo(RESPONSAVEL, new Medicamento(0, "Pomada cicatrizante", dermaVet, 8, 5, 27.00,
                hoje.plusDays(3), "Alantoína", "2%", false));
        // 5..6: vacinas (a V10 vence em menos de 60 dias -> aviso de validade próxima)
        estoque.cadastrarItemComo(RESPONSAVEL, new Vacina(0, "Antirrábica", petSaude, 40, 15, 39.90,
                LocalDate.of(2027, 12, 31), "Raiva", 1));
        estoque.cadastrarItemComo(RESPONSAVEL, new Vacina(0, "V10 Polivalente", vetPharma, 20, 8, 85.00,
                hoje.plusDays(45), "Cinomose, parvovirose e outras", 1));
        // 7..8: insumos
        estoque.cadastrarItemComo(RESPONSAVEL, new Insumo(0, "Seringa descartável 3ml", medCirurgica, 200, 50, 0.80,
                LocalDate.of(2029, 12, 31), "unidade", true));
        estoque.cadastrarItemComo(RESPONSAVEL, new Insumo(0, "Luvas de procedimento", medCirurgica, 12, 5, 32.00,
                null, "caixa", false));
        // 9: medicamento controlado (exige justificativa na saída)
        estoque.cadastrarItemComo(RESPONSAVEL, new Medicamento(0, "Tramadol 50mg", vetPharma, 25, 5, 18.70,
                LocalDate.of(2028, 8, 31), "Tramadol", "50 mg", true));
    }
}
