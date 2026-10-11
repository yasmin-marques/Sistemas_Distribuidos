package clinica.servico;

import clinica.modelo.Animal;
import clinica.modelo.Medicamento;
import clinica.modelo.RegistroConsulta;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Implementação de {@link Consulta}; usa o {@link EstoqueServico} para dispensar medicamentos. */
public class ConsultaServico implements Consulta {

    private final EstoqueServico estoque;
    private final Map<Integer, Animal> animais = new LinkedHashMap<>();
    private final List<RegistroConsulta> consultas = new ArrayList<>();
    private int proximoAnimal = 1;
    private int proximaConsulta = 1;

    public ConsultaServico(EstoqueServico estoque) {
        this.estoque = estoque;
    }

    @Override
    public synchronized Animal registrarAnimal(Animal animal) {
        if (animal == null || animal.getNome() == null || animal.getNome().isBlank()) {
            throw new IllegalArgumentException("Animal deve ter nome.");
        }
        animal.setId(proximoAnimal++);
        animais.put(animal.getId(), animal);
        return animal;
    }

    @Override
    public synchronized Animal buscarAnimal(int id) {
        Animal a = animais.get(id);
        if (a == null) throw new IllegalArgumentException("Animal " + id + " não encontrado.");
        return a;
    }

    @Override
    public synchronized List<Animal> listarAnimais() {
        return new ArrayList<>(animais.values());
    }

    @Override
    public synchronized RegistroConsulta realizarConsulta(int idAnimal, String sintomas,
            String diagnostico, int idMedicamento, int quantidade) {
        Animal animal = buscarAnimal(idAnimal);
        String nomeMedicamento = null;
        int dispensado = 0;
        if (idMedicamento > 0) {
            Medicamento atualizado = estoque.baixar(idMedicamento, quantidade);
            nomeMedicamento = atualizado.getNome();
            dispensado = quantidade;
        }
        RegistroConsulta registro = new RegistroConsulta(proximaConsulta++, animal, sintomas,
                diagnostico, nomeMedicamento, dispensado, LocalDateTime.now());
        consultas.add(registro);
        return registro;
    }

    @Override
    public synchronized List<RegistroConsulta> historico(int idAnimal) {
        buscarAnimal(idAnimal); // valida existência
        List<RegistroConsulta> resultado = new ArrayList<>();
        for (RegistroConsulta c : consultas) {
            if (c.getAnimal().getId() == idAnimal) resultado.add(c);
        }
        return resultado;
    }
}
