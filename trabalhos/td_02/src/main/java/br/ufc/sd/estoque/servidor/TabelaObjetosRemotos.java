package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.ObjetoRemoto;
import br.ufc.sd.estoque.protocolo.CodigoErro;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Tabela de objetos remotos do servidor (o "módulo de referência remota" do livro).
 *
 * <p>Associa o nome de cada objeto ({@code objectReference.objeto}) ao seu servo e
 * esqueleto. Objetos de serviço têm nome fixo; objetos criados durante a execução
 * (assinaturas, pedidos) recebem um nome aleatório quando são devolvidos por referência
 * pela primeira vez, e saem da tabela quando seu ciclo de vida termina.
 */
public class TabelaObjetosRemotos {

    private record Entrada(Object servo, Esqueleto esqueleto) { }

    private final String host;
    private final int porta;
    private final String servico;
    private final Map<Class<?>, Function<Object, Esqueleto>> fabricas = new LinkedHashMap<>();
    private final Map<String, Entrada> porNome = new LinkedHashMap<>();
    private final Map<Object, String> nomePorServo = new IdentityHashMap<>();

    public TabelaObjetosRemotos(String host, int porta, String servico) {
        this.host = host;
        this.porta = porta;
        this.servico = servico;
    }

    /** Ensina a tabela a criar o esqueleto dos servos que implementam a interface remota. */
    @SuppressWarnings("unchecked")
    public synchronized <T extends ObjetoRemoto> void registrarTipo(Class<T> interfaceRemota,
                                                                    Function<T, Esqueleto> fabrica) {
        fabricas.put(interfaceRemota, servo -> fabrica.apply((T) servo));
    }

    /** Registra um objeto de serviço com nome fixo. */
    public synchronized RemoteObjectRef registrar(String nome, ObjetoRemoto servo) {
        if (porNome.containsKey(nome)) throw new IllegalStateException("Já existe objeto chamado '" + nome + "'.");
        Class<?> tipo = interfaceDe(servo);
        Esqueleto esqueleto = fabricas.get(tipo).apply(servo);
        porNome.put(nome, new Entrada(servo, esqueleto));
        nomePorServo.put(servo, nome);
        return referencia(nome, esqueleto.interfaceRemota());
    }

    /**
     * Devolve a referência do servo, registrando-o se for a primeira vez que ele sai do
     * servidor. É o que acontece quando um método devolve um objeto remoto.
     */
    public synchronized RemoteObjectRef exportar(ObjetoRemoto servo) {
        String nome = nomePorServo.get(servo);
        if (nome != null) return referencia(nome, porNome.get(nome).esqueleto().interfaceRemota());
        Class<?> tipo = interfaceDe(servo);
        // nome aleatório: quem não recebeu a referência não consegue adivinhá-la
        String prefixo = tipo.getSimpleName().replace("Remota", "").replace("Remoto", "");
        String candidato;
        do {
            candidato = prefixo + "-" + UUID.randomUUID().toString().substring(0, 8);
        } while (porNome.containsKey(candidato));
        return registrar(candidato, servo);
    }

    /** Seleciona o objeto indicado pela referência. */
    public synchronized Esqueleto localizar(RemoteObjectRef ref) {
        Entrada e = porNome.get(ref.objeto());
        if (e == null) {
            throw new ExcecaoRemota(CodigoErro.OBJETO_INEXISTENTE,
                    "Objeto remoto '" + ref.objeto() + "' não existe (ou já foi encerrado).");
        }
        if (!e.esqueleto().interfaceRemota().equals(ref.interfaceRemota())) {
            throw new ExcecaoRemota(CodigoErro.REQUISICAO_INVALIDA, "O objeto '" + ref.objeto() + "' implementa "
                    + e.esqueleto().interfaceRemota() + ", não " + ref.interfaceRemota() + ".");
        }
        return e.esqueleto();
    }

    /**
     * Converte uma referência recebida como argumento no servo local correspondente.
     *
     * @throws IllegalArgumentException se a referência não for de um objeto deste servidor com o tipo esperado
     */
    public synchronized <T> T resolver(RemoteObjectRef ref, Class<T> tipo) {
        if (!servico.equals(ref.servico()) || ref.porta() != porta) {
            throw new IllegalArgumentException("A referência " + ref + " não pertence a este servidor.");
        }
        Entrada e = porNome.get(ref.objeto());
        if (e == null || !tipo.isInstance(e.servo())) {
            throw new IllegalArgumentException("A referência não aponta para um " + tipo.getSimpleName() + " ativo.");
        }
        return tipo.cast(e.servo());
    }

    /** Retira o servo da tabela; referências a ele passam a dar {@code OBJETO_INEXISTENTE}. */
    public synchronized void remover(Object servo) {
        String nome = nomePorServo.remove(servo);
        if (nome != null) porNome.remove(nome);
    }

    public RemoteObjectRef referencia(String nome, String interfaceRemota) {
        return new RemoteObjectRef(host, porta, servico, nome, interfaceRemota);
    }

    public synchronized List<String> nomes() {
        return Collections.unmodifiableList(new ArrayList<>(porNome.keySet()));
    }

    public synchronized int tamanho() {
        return porNome.size();
    }

    private Class<?> interfaceDe(Object servo) {
        for (Class<?> tipo : fabricas.keySet()) {
            if (tipo.isInstance(servo)) return tipo;
        }
        throw new IllegalArgumentException("Nenhum esqueleto registrado para " + servo.getClass().getName() + ".");
    }
}
