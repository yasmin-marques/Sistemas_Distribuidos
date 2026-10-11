package clinica.modelo;

/** Subclasse de {@link Animal}: gato. */
public class Gato extends Animal {

    private static final long serialVersionUID = 1L;

    private String raca;
    private boolean castrado;

    public Gato() {
    }

    public Gato(int id, String nome, int idade, double peso, String tutor,
                String raca, boolean castrado) {
        super(id, nome, idade, peso, tutor);
        this.raca = raca;
        this.castrado = castrado;
    }

    @Override
    public String getEspecie() { return "Gato"; }

    public String getRaca() { return raca; }
    public void setRaca(String raca) { this.raca = raca; }

    public boolean isCastrado() { return castrado; }
    public void setCastrado(boolean castrado) { this.castrado = castrado; }

    @Override
    public String toString() {
        return super.toString() + " [raça: " + raca + ", castrado: " + (castrado ? "sim" : "não") + "]";
    }
}
