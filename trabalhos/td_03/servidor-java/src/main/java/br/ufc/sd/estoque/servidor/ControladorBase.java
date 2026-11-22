package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.ObjetoRemoto;
import br.ufc.sd.estoque.web.ReferenciaRemota;
import br.ufc.sd.estoque.web.Requisicao;
import br.ufc.sd.estoque.web.Resposta;
import br.ufc.sd.estoque.web.Roteador;
import br.ufc.sd.estoque.web.TabelaObjetosDistribuidos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Comportamento comum dos controladores. Cada controlador é a "porta HTTP" de um objeto
 * distribuído: traduz requisição HTTP em chamada de método do objeto e o resultado em resposta
 * (papel dos esqueletos do TD02).
 */
abstract class ControladorBase {

    protected final TabelaObjetosDistribuidos tabela;

    protected ControladorBase(TabelaObjetosDistribuidos tabela) {
        this.tabela = Objects.requireNonNull(tabela);
    }

    /** Declara as rotas (método HTTP + caminho) do objeto no roteador. */
    abstract void registrar(Roteador roteador);

    /**
     * Faz o endereço do objeto responder a {@code GET} com a sua descrição (nome, interface e
     * operações), para que quem tem só o endereço descubra o que pode chamar.
     */
    protected void descrever(Roteador roteador, String nome, Class<? extends ObjetoRemoto> interfaceRemota,
                             String caminho) {
        roteador.get(caminho, q -> {
            Map<String, Object> m = new LinkedHashMap<>(
                    tabela.referencia(nome, q.baseUrl()).paraJson());
            m.put("operacoes", roteador.operacoes(caminho));
            return Resposta.ok(m);
        });
    }

    /**
     * Passagem por <b>referência</b>: o objeto fica no servidor e o cliente recebe só a
     * referência (201 Created + {@code Location} com o endereço do objeto).
     */
    protected Resposta porReferencia(Requisicao requisicao, ObjetoRemoto objeto) {
        ReferenciaRemota ref = tabela.exportar(objeto, requisicao.baseUrl());
        return Resposta.criado(ref.href(), ref.paraJson());
    }
}
