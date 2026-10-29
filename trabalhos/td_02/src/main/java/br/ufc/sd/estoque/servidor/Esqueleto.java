package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.json.ObjetoJson;

/**
 * Esqueleto (skeleton) de um objeto remoto: desempacota os argumentos, chama o método do
 * objeto (servo) e empacota o resultado.
 */
public interface Esqueleto {

    /** Nome da interface remota implementada pelo servo (ex.: {@code EstoqueRemoto}). */
    String interfaceRemota();

    /**
     * Executa o método.
     *
     * @return o resultado já na representação externa (valor JSON: Map, List, String, número, booleano ou null)
     * @throws br.ufc.sd.estoque.protocolo.ExcecaoRemota com {@code METODO_INEXISTENTE} se o método não existir
     */
    Object invocar(String metodo, ObjetoJson argumentos);
}
