package clinica.modelo;

import java.io.Serializable;
import java.time.LocalDateTime;

// POJO que representa uma consulta já realizada.
public class RegistroConsulta implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private Animal animal;
    private String sintomas;
    private String diagnostico;
    private String medicamento;   // nome do medicamento dispensado
    private int quantidade;       // unidades dispensadas
    private LocalDateTime dataHora;

    public RegistroConsulta() {
    }

    public RegistroConsulta(int id, Animal animal, String sintomas, String diagnostico,
                            String medicamento, int quantidade, LocalDateTime dataHora) {
        this.id = id;
        this.animal = animal;
        this.sintomas = sintomas;
        this.diagnostico = diagnostico;
        this.medicamento = medicamento;
        this.quantidade = quantidade;
        this.dataHora = dataHora;
    }

    public int getId() { return id; }
    public Animal getAnimal() { return animal; }
    public String getSintomas() { return sintomas; }
    public String getDiagnostico() { return diagnostico; }
    public String getMedicamento() { return medicamento; }
    public int getQuantidade() { return quantidade; }
    public LocalDateTime getDataHora() { return dataHora; }

    @Override
    public String toString() {
        String med = medicamento == null ? "nenhum" : quantidade + "x " + medicamento;
        return "Consulta #" + id + " [" + dataHora.withNano(0) + "] " + animal.getEspecie()
                + " '" + animal.getNome() + "' | sintomas: " + sintomas
                + " | diagnóstico: " + diagnostico + " | medicamento: " + med;
    }
}
