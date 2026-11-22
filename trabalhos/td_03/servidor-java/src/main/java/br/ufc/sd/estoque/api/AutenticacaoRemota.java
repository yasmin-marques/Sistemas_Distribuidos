package br.ufc.sd.estoque.api;

import br.ufc.sd.estoque.modelo.Sessao;

/**
 * Autenticação dos usuários da clínica (no TD01 era feita por TCP unicast).
 * Objeto remoto de nome fixo {@value #NOME}.
 */
public interface AutenticacaoRemota extends ObjetoRemoto {

    String NOME = "Autenticacao";

    /** @throws NaoAutenticadoException se usuário ou senha estiverem incorretos */
    Sessao autenticar(String usuario, String senha);

    void encerrarSessao(String token);
}
