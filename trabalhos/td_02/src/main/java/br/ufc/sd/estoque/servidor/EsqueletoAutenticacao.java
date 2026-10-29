package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AutenticacaoRemota;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;

/** Esqueleto de {@link AutenticacaoRemota}. */
public class EsqueletoAutenticacao extends EsqueletoBase<AutenticacaoRemota> {

    public EsqueletoAutenticacao(AutenticacaoRemota servo, TabelaObjetosRemotos tabela) {
        super(AutenticacaoRemota.class, servo, tabela);
    }

    @Override
    public Object invocar(String metodo, ObjetoJson a) {
        return switch (metodo) {
            case "autenticar" -> CodecEntidades.sessaoParaJson(servo.autenticar(a.texto("usuario"), a.texto("senha")));
            case "encerrarSessao" -> {
                servo.encerrarSessao(a.texto("token"));
                yield null;
            }
            default -> throw metodoInexistente(metodo);
        };
    }
}
