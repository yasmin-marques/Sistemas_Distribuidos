package clinica.multicast;

// Endereço (classe D) e porta de um grupo multicast
public class Grupo {

    private final String endereco;
    private final int porta;

    public Grupo(String endereco, int porta) {
        this.endereco = endereco;
        this.porta = porta;
    }

    public String getEndereco() { return endereco; }
    public int getPorta() { return porta; }

    @Override
    public String toString() {
        return endereco + ":" + porta;
    }
}
