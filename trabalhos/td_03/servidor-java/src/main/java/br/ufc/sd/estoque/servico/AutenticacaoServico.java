package br.ufc.sd.estoque.servico;

import br.ufc.sd.estoque.api.AutenticacaoRemota;
import br.ufc.sd.estoque.api.NaoAutenticadoException;
import br.ufc.sd.estoque.modelo.Sessao;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Autentica os usuários e mantém as sessões abertas (thread-safe). */
public class AutenticacaoServico implements AutenticacaoRemota {

    /** Mesmos usuários da questão 4 do TD01. */
    public static final Map<String, String> USUARIOS_PADRAO = Map.of(
            "veterinario", "vet123",
            "recepcao", "rec123",
            "estoquista", "est123");

    private final Map<String, String> usuarios;
    private final ConcurrentMap<String, Sessao> sessoes = new ConcurrentHashMap<>();

    public AutenticacaoServico(Map<String, String> usuarios) {
        this.usuarios = Map.copyOf(usuarios);
    }

    @Override
    public Sessao autenticar(String usuario, String senha) {
        if (usuario == null || senha == null) throw new NaoAutenticadoException("Informe usuário e senha.");
        String esperada = usuarios.get(usuario);
        // comparação em tempo constante, para não revelar a senha pelo tempo de resposta
        boolean confere = esperada != null && MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.UTF_8), senha.getBytes(StandardCharsets.UTF_8));
        if (!confere) throw new NaoAutenticadoException("Usuário ou senha inválidos.");
        Sessao sessao = new Sessao(UUID.randomUUID().toString(), usuario, System.currentTimeMillis());
        sessoes.put(sessao.getToken(), sessao);
        return sessao;
    }

    @Override
    public void encerrarSessao(String token) {
        if (token == null) throw new IllegalArgumentException("Token ausente.");
        sessoes.remove(token);
    }

    /** @return a sessão do token; lança {@link NaoAutenticadoException} se ele não for válido. */
    public Sessao validar(String token) {
        Sessao sessao = token == null ? null : sessoes.get(token);
        if (sessao == null) {
            throw new NaoAutenticadoException("Sessão inválida ou encerrada; autentique-se novamente.");
        }
        return sessao;
    }

    public List<String> usuariosConectados() {
        List<String> nomes = new ArrayList<>();
        for (Sessao s : sessoes.values()) nomes.add(s.getUsuario());
        Collections.sort(nomes);
        return nomes;
    }
}
