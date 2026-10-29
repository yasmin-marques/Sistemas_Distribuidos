package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;

/** Esqueleto de {@link AssinaturaRemota}. */
public class EsqueletoAssinatura extends EsqueletoBase<AssinaturaRemota> {

    public EsqueletoAssinatura(AssinaturaRemota servo, TabelaObjetosRemotos tabela) {
        super(AssinaturaRemota.class, servo, tabela);
    }

    @Override
    public Object invocar(String metodo, ObjetoJson a) {
        return switch (metodo) {
            case "aguardar" -> CodecEntidades.listaParaJson(servo.aguardar(a.longo("esperaMaximaMs")),
                    CodecEntidades::notificacaoParaJson);
            case "usuario" -> servo.usuario();
            default -> throw metodoInexistente(metodo);
        };
    }
}
