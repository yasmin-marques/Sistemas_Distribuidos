package br.ufc.sd.estoque.web;

import br.ufc.sd.estoque.api.ObjetoRemoto;
import br.ufc.sd.estoque.api.RecursoNaoEncontradoException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tabela de objetos distribuídos do servidor (o "módulo de referência remota" do livro).
 *
 * <p>Objetos de serviço têm nome e endereço fixos ({@code Estoque} em {@code /api/estoque}).
 * Objetos criados durante a execução (pedidos de reposição, assinaturas) recebem um nome
 * aleatório quando são devolvidos por referência pela primeira vez, ficam em
 * {@code <prefixo>/<nome>} e saem da tabela quando o ciclo de vida termina — a partir daí a
 * referência dá 404.
 */
public class TabelaObjetosDistribuidos {

    private record Entrada(Object servo, Class<? extends ObjetoRemoto> tipo, String caminho) { }

    private final Map<String, Entrada> porNome = new LinkedHashMap<>();
    private final Map<Object, String> nomePorServo = new IdentityHashMap<>();
    private final Map<Class<? extends ObjetoRemoto>, String> prefixos = new LinkedHashMap<>();

    /** Diz onde vivem os objetos criados dinamicamente de uma interface (ex.: {@code /api/pedidos}). */
    public synchronized void registrarTipoDinamico(Class<? extends ObjetoRemoto> interfaceRemota, String prefixoCaminho) {
        prefixos.put(interfaceRemota, prefixoCaminho);
    }

    /** Registra um objeto de serviço com nome e caminho fixos. */
    public synchronized void registrarFixo(String nome, ObjetoRemoto servo, Class<? extends ObjetoRemoto> tipo,
                                           String caminho) {
        if (porNome.containsKey(nome)) throw new IllegalStateException("Já existe objeto chamado '" + nome + "'.");
        porNome.put(nome, new Entrada(servo, tipo, caminho));
        nomePorServo.put(servo, nome);
    }

    /**
     * Devolve a referência do objeto, registrando-o se for a primeira vez que ele sai do
     * servidor. É o que acontece quando uma operação devolve um objeto remoto.
     */
    public synchronized ReferenciaRemota exportar(ObjetoRemoto servo, String baseUrl) {
        String nome = nomePorServo.get(servo);
        if (nome == null) {
            Class<? extends ObjetoRemoto> tipo = tipoDe(servo);
            String prefixoNome = tipo.getSimpleName().replace("Remota", "").replace("Remoto", "");
            do {
                // nome aleatório: quem não recebeu a referência não consegue adivinhá-la
                nome = prefixoNome + "-" + UUID.randomUUID().toString().substring(0, 8);
            } while (porNome.containsKey(nome));
            porNome.put(nome, new Entrada(servo, tipo, prefixos.get(tipo) + "/" + nome));
            nomePorServo.put(servo, nome);
        }
        return referencia(nome, baseUrl);
    }

    public synchronized ReferenciaRemota referencia(String nome, String baseUrl) {
        Entrada e = porNome.get(nome);
        if (e == null) throw new RecursoNaoEncontradoException("Objeto remoto '" + nome + "' não existe.");
        return new ReferenciaRemota(nome, e.tipo().getSimpleName(), baseUrl + e.caminho());
    }

    /** Seleciona o objeto pelo nome (usado quando o nome vem no caminho da URL). */
    public synchronized <T> T localizar(String nome, Class<T> tipo) {
        Entrada e = porNome.get(nome);
        if (e == null || !tipo.isInstance(e.servo())) {
            throw new RecursoNaoEncontradoException("Objeto remoto '" + nome + "' não existe (ou já foi encerrado).");
        }
        return tipo.cast(e.servo());
    }

    /**
     * Converte uma referência recebida como <b>argumento</b> no objeto real que ela indica.
     *
     * @throws IllegalArgumentException se a referência não apontar para um objeto ativo do tipo esperado
     */
    public synchronized <T> T resolver(ReferenciaRemota ref, Class<T> tipo) {
        Entrada e = porNome.get(ref.objeto());
        if (e == null || !tipo.isInstance(e.servo()) || !e.tipo().getSimpleName().equals(ref.interfaceRemota())) {
            throw new IllegalArgumentException("A referência '" + ref.objeto() + "' não aponta para um "
                    + tipo.getSimpleName() + " ativo.");
        }
        return tipo.cast(e.servo());
    }

    /** Retira o objeto da tabela; referências a ele passam a dar 404. */
    public synchronized void remover(Object servo) {
        String nome = nomePorServo.remove(servo);
        if (nome != null) porNome.remove(nome);
    }

    /** Descrição dos objetos de serviço (fixos), sem expor os nomes dos objetos dinâmicos. */
    public synchronized List<Map<String, Object>> descreverFixos(String baseUrl) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Map.Entry<String, Entrada> e : porNome.entrySet()) {
            if (prefixos.containsKey(e.getValue().tipo())
                    && e.getValue().caminho().startsWith(prefixos.get(e.getValue().tipo()) + "/")) continue;
            lista.add(new ReferenciaRemota(e.getKey(), e.getValue().tipo().getSimpleName(),
                    baseUrl + e.getValue().caminho()).paraJson());
        }
        return lista;
    }

    public synchronized List<String> nomes() {
        return Collections.unmodifiableList(new ArrayList<>(porNome.keySet()));
    }

    public synchronized int tamanho() {
        return porNome.size();
    }

    private Class<? extends ObjetoRemoto> tipoDe(Object servo) {
        for (Class<? extends ObjetoRemoto> tipo : prefixos.keySet()) {
            if (tipo.isInstance(servo)) return tipo;
        }
        throw new IllegalArgumentException("Tipo de objeto remoto sem endereço registrado: " + servo.getClass().getName());
    }
}
