package clinica.modelo;

import java.io.Serializable;
import java.util.Locale;

// Superclasse dos animais atendidos pela clínica (Cachorro, Gato e Coelho)
public abstract class Animal implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String nome;
    private int idade;       
    private double peso;    
    private String tutor;

    public Animal() {
    }

    public Animal(int id, String nome, int idade, double peso, String tutor) {
        this.id = id;
        this.nome = nome;
        this.idade = idade;
        this.peso = peso;
        this.tutor = tutor;
    }

    public abstract String getEspecie();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public int getIdade() { return idade; }
    public void setIdade(int idade) { this.idade = idade; }

    public double getPeso() { return peso; }
    public void setPeso(double peso) { this.peso = peso; }

    public String getTutor() { return tutor; }
    public void setTutor(String tutor) { this.tutor = tutor; }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s #%d '%s' (%d ano(s), %.1f kg, tutor: %s)",
                getEspecie(), id, nome, idade, peso, tutor);
    }
}
