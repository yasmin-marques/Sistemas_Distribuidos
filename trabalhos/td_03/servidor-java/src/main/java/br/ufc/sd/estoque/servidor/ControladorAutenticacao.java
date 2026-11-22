package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.AutenticacaoRemota;
import br.ufc.sd.estoque.api.NaoAutenticadoException;
import br.ufc.sd.estoque.json.CodecEntidades;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.web.Requisicao;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import br.ufc.sd.estoque.web.TabelaObjetosDistribuidos;

/**
 * Objeto distribuído {@code Autenticacao}.
 * <pre>
 *   GET    /api/autenticacao   descrição do objeto (nome, interface e operações)
 *   POST   /api/autenticacao/sessoes         autenticar(usuario, senha)  -> 201 Sessao
 *   DELETE /api/autenticacao/sessoes/atual   encerrarSessao(token)       -> 204
 * </pre>
 */
final class ControladorAutenticacao extends ControladorBase {

    static final String CAMINHO = "/api/autenticacao";

    private final AutenticacaoRemota servo;

    ControladorAutenticacao(AutenticacaoRemota servo, TabelaObjetosDistribuidos tabela) {
        super(tabela);
        this.servo = servo;
    }

    @Override
    void registrar(Roteador r) {
        descrever(r, AutenticacaoRemota.NOME, AutenticacaoRemota.class, CAMINHO);
        r.post(CAMINHO + "/sessoes", this::autenticar);
        r.delete(CAMINHO + "/sessoes/atual", this::encerrar);
    }

    private Resposta autenticar(Requisicao q) {
        ObjetoJson a = q.corpo();
        var sessao = servo.autenticar(a.textoOpcional("usuario"), a.textoOpcional("senha"));
        return Resposta.criado(null, CodecEntidades.sessaoParaJson(sessao));
    }

    private Resposta encerrar(Requisicao q) {
        String token = q.token();
        if (token == null) throw new NaoAutenticadoException("Informe o token no cabeçalho Authorization: Bearer.");
        servo.encerrarSessao(token);
        return Resposta.semConteudo();
    }
}
