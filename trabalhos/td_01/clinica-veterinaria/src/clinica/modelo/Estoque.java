package clinica.modelo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Agregação: o estoque guarda medicamentos, que existem independentemente dele. 
public class Estoque implements Serializable {

    private static final long serialVersionUID = 1L;

    private final ArrayList<Medicamento> medicamentos = new ArrayList<>();

    public void adicionar(Medicamento medicamento) {
        medicamentos.add(medicamento);
    }

    public boolean remover(int id) {
        return medicamentos.removeIf(m -> m.getId() == id);
    }

    /** @return o medicamento com o id informado ou {@code null}. */
    public Medicamento buscarPorId(int id) {
        for (Medicamento m : medicamentos) {
            if (m.getId() == id) return m;
        }
        return null;
    }

    public List<Medicamento> getMedicamentos() {
        return Collections.unmodifiableList(medicamentos);
    }

    public int tamanho() {
        return medicamentos.size();
    }
}
