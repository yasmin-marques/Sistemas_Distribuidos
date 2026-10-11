package clinica.modelo;

/** Subclasse de {@link Animal}: coelho. */
public class Coelho extends Animal {

    private static final long serialVersionUID = 1L;

    private String raca;
    private String pelagem;

    public Coelho() {
    }

    public Coelho(int id, String nome, int idade, double peso, String tutor,
                  String raca, String pelagem) {
        super(id, nome, idade, peso, tutor);
        this.raca = raca;
        this.pelagem = pelagem;
    }

    @Override
    public String getEspecie() { return "Coelho"; }

    public String getRaca() { return raca; }
    public void setRaca(String raca) { this.raca = raca; }

    public String getPelagem() { return pelagem; }
    public void setPelagem(String pelagem) { this.pelagem = pelagem; }

    @Override
    public String toString() {
        return super.toString() + " [raça: " + raca + ", pelagem: " + pelagem + "]";
    }
}
