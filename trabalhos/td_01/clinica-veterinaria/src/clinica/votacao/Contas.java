package clinica.votacao;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Eleitores e administradores cadastrados no servidor
public class Contas {

    public enum Papel { ELEITOR, ADMIN }

    private final Map<String, String> eleitores = new ConcurrentHashMap<>();
    private final Map<String, String> administradores = new ConcurrentHashMap<>();

    /** Contas usadas na demonstração: tutores com senha 1234 e o administrador "admin". */
    public static Contas padrao() {
        Contas contas = new Contas();
        for (String tutor : new String[] {"carlos", "ana", "beatriz", "paula", "rui"}) {
            contas.adicionarEleitor(tutor, "1234");
        }
        contas.adicionarAdministrador("admin", "admin123");
        return contas;
    }

    public Contas adicionarEleitor(String usuario, String senha) {
        eleitores.put(usuario, senha);
        return this;
    }

    public Contas adicionarAdministrador(String usuario, String senha) {
        administradores.put(usuario, senha);
        return this;
    }

    /** @return o papel da conta, ou {@code null} se usuário ou senha estiverem errados. */
    public Papel autenticar(String usuario, String senha) {
        if (usuario == null || senha == null) return null;
        if (senha.equals(administradores.get(usuario))) return Papel.ADMIN;
        if (senha.equals(eleitores.get(usuario))) return Papel.ELEITOR;
        return null;
    }
}
