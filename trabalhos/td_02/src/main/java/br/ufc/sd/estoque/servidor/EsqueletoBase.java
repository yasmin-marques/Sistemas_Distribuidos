package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.ObjetoRemoto;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.protocolo.CodigoErro;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import java.util.Objects;

/** Comportamento comum dos esqueletos: passagem de objetos remotos por referência. */
abstract class EsqueletoBase<T extends ObjetoRemoto> implements Esqueleto {

    protected final T servo;
    protected final TabelaObjetosRemotos tabela;
    private final String interfaceRemota;

    protected EsqueletoBase(Class<T> interfaceRemota, T servo, TabelaObjetosRemotos tabela) {
        this.interfaceRemota = interfaceRemota.getSimpleName();
        this.servo = Objects.requireNonNull(servo);
        this.tabela = Objects.requireNonNull(tabela);
    }

    @Override
    public String interfaceRemota() {
        return interfaceRemota;
    }

    /** Resultado que é objeto remoto: viaja a referência, o objeto fica no servidor. */
    protected Object porReferencia(ObjetoRemoto objeto) {
        return tabela.exportar(objeto).paraJson();
    }

    /** Argumento que é referência: converte de volta no objeto local que ela indica. */
    protected <R> R resolver(ObjetoJson argumentos, String campo, Class<R> tipo) {
        return tabela.resolver(RemoteObjectRef.deJson(argumentos.objeto(campo).mapa()), tipo);
    }

    protected ExcecaoRemota metodoInexistente(String metodo) {
        return new ExcecaoRemota(CodigoErro.METODO_INEXISTENTE,
                "A interface " + interfaceRemota + " não tem o método '" + metodo + "'.");
    }
}
