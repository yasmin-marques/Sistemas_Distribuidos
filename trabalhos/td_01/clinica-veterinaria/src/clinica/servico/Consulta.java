package clinica.servico;

import clinica.modelo.Animal;
import clinica.modelo.RegistroConsulta;
import java.util.List;

// Interface do serviço remoto: atendimento da clínica veterinária
public interface Consulta {

    // Cadastra um animal e devolve-o com o id gerado pela clínica
    Animal registrarAnimal(Animal animal);

    /** @throws IllegalArgumentException se o animal não existir. */
    Animal buscarAnimal(int id);

    List<Animal> listarAnimais();

    /** Realiza uma consulta; se idMedicamento > 0, dá baixa de {@code quantidade} unidades no estoque. */
    RegistroConsulta realizarConsulta(int idAnimal, String sintomas, String diagnostico,
                                      int idMedicamento, int quantidade);

    // Histórico de consultas de um animal
    List<RegistroConsulta> historico(int idAnimal);
}
