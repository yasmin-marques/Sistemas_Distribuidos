package clinica.modelo;

/** Subclasse de {@link Animal}: cachorro. */
public class Cachorro extends Animal {

    private static final long serialVersionUID = 1L;

    public enum Porte { PEQUENO, MEDIO, GRANDE }

    private String raca;
    private Porte porte;

    public Cachorro() {
    }

    public Cachorro(int id, String nome, int idade, double peso, String tutor,
                    String raca, Porte porte) {
        super(id, nome, idade, peso, tutor);
        this.raca = raca;
        this.porte = porte;
    }

    @Override
    public String getEspecie() { return "Cachorro"; }

    public String getRaca() { return raca; }
    public void setRaca(String raca) { this.raca = raca; }

    public Porte getPorte() { return porte; }
    public void setPorte(Porte porte) { this.porte = porte; }

    @Override
    public String toString() {
        return super.toString() + " [raça: " + raca + ", porte: " + porte + "]";
    }
}
