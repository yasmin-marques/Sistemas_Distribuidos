/*
 Biblioteca de acesso à API do estoque veterinário (TD03, JavaScript).

 Fala com o servidor Java só pelo contrato HTTP/JSON. Roda no navegador (como <script> comum,
 expõe `EstoqueApi`) e no Node 18+ (require), sem nenhuma dependência.

 Camadas: ClienteHttp (requisição-resposta, o "doOperation"), modelo (entidades locais, com
 herança e agregação), proxies (um por objeto distribuído) e OuvinteNotificacoes (long polling).
 */
(function (raiz, fabrica) {
  if (typeof module === 'object' && module.exports) module.exports = fabrica();
  else raiz.EstoqueApi = fabrica();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  // erros

  /* O servidor respondeu com erro: status HTTP + código ('OPERACAO_NEGADA'...) + mensagem. /
  class ErroApi extends Error {
    constructor(status, codigo, mensagem) {
      super(`${codigo}: ${mensagem}`);
      this.name = 'ErroApi';
      this.status = status;
      this.codigo = codigo;
      this.mensagem = mensagem;
    }
  }

  /*  Não foi possível falar com o servidor (fora do ar, endereço errado, tempo esgotado...). */
  class FalhaComunicacao extends Error {
    constructor(mensagem) {
      super(mensagem);
      this.name = 'FalhaComunicacao';
    }
  }

  // requisição-resposta

  class ClienteHttp {
    /* @param {string} baseUrl ex.: http://localhost:8080  @param {Function} [fetchImpl] */
    constructor(baseUrl, fetchImpl) {
      this.baseUrl = baseUrl.replace(/\/+$/, '');
      this.fetch = fetchImpl || ((...a) => fetch(...a));
      this.tempoLimiteMs = 40000;
      this.proximoId = 1;
      /* função chamada com cada mensagem trocada ({sentido, id, ...}) quando o rastro está ligado */
      this.rastro = null;
    }

    url(caminho) {
      return /^https?:/.test(caminho) ? caminho : this.baseUrl + caminho;
    }

    /*
     Envia a requisição e devolve o corpo JSON da resposta (null se não houver corpo).
     @throws {ErroApi} se o servidor respondeu com erro
     @throws {FalhaComunicacao} se não houve resposta
    */
    async doOperation(metodo, caminho, corpo, token, opcoes = {}) {
      const url = this.url(caminho);
      const id = this.proximoId++;
      const cabecalhos = { Accept: 'application/json', 'X-Request-Id': String(id) };
      let dados;
      if (corpo !== undefined && corpo !== null) {
        dados = JSON.stringify(corpo);
        cabecalhos['Content-Type'] = 'application/json';
      }
      if (token) cabecalhos.Authorization = 'Bearer ' + token;
      this._registrar({ sentido: '>>>', id, metodo, url, corpo: dados });

      const controle = new AbortController();
      const limite = opcoes.tempoLimiteMs || this.tempoLimiteMs;
      const relogio = setTimeout(() => controle.abort(), limite);
      if (opcoes.sinal) opcoes.sinal.addEventListener('abort', () => controle.abort());
      let resposta, texto;
      try {
        resposta = await this.fetch(url, { method: metodo, headers: cabecalhos, body: dados, signal: controle.signal });
        texto = await resposta.text();
      } catch (e) {
        if (opcoes.sinal && opcoes.sinal.aborted) throw e; // cancelado de propósito por quem chamou
        const motivo = e.name === 'AbortError' ? 'tempo esgotado' : (e.cause && e.cause.code) || e.message;
        throw new FalhaComunicacao(`${url}: ${motivo}`);
      } finally {
        clearTimeout(relogio);
      }
      this._registrar({ sentido: '<<<', id, status: resposta.status, corpo: texto });

      let resultado = null;
      if (texto) {
        try {
          resultado = JSON.parse(texto);
        } catch (e) {
          throw new FalhaComunicacao(`Resposta malformada de ${url}: ${e.message}`);
        }
      }
      if (resposta.status >= 400) {
        const erro = (resultado && resultado.erro) || {};
        throw new ErroApi(resposta.status, erro.codigo || 'ERRO_INTERNO', erro.mensagem || 'HTTP ' + resposta.status);
      }
      return resultado;
    }

    _registrar(mensagem) {
      if (this.rastro) this.rastro(mensagem);
    }
  }

  // modelo (cópias locais)

  class Fornecedor {
    constructor({ id = 0, nome = null, cnpj = null, telefone = null } = {}) {
      Object.assign(this, { id, nome, cnpj, telefone });
    }
    paraJson() { return { id: this.id, nome: this.nome, cnpj: this.cnpj, telefone: this.telefone }; }
  }

  /* Superclasse de tudo o que é guardado no estoque. */
  class ItemEstoque {
    constructor(d = {}) {
      this.id = d.id || 0;
      this.nome = d.nome || '';
      this.fornecedor = d.fornecedor ? new Fornecedor(d.fornecedor) : null; // agregação ("tem-um")
      this.quantidade = d.quantidade || 0;
      this.estoqueMinimo = d.estoqueMinimo || 0;
      this.precoUnitario = d.precoUnitario || 0;
      this.validade = d.validade || null; // AAAA-MM-DD
    }
    get classe() { return 'ITEM'; }
    get categoria() { return 'Item'; }
    get diasAvisoValidade() { return 30; }
    camposProprios() { return {}; }
    detalhes() { return ''; }

    vencido(hoje = new Date()) { return !!this.validade && this.validade < ItemEstoque.iso(hoje); }
    venceEmBreve(hoje = new Date()) {
      if (!this.validade || this.vencido(hoje)) return false;
      const limite = new Date(hoje.getTime() + this.diasAvisoValidade * 86400000);
      return this.validade <= ItemEstoque.iso(limite);
    }
    abaixoDoMinimo() { return this.quantidade <= this.estoqueMinimo; }

    paraJson() {
      return Object.assign({
        classe: this.classe, id: this.id, nome: this.nome,
        fornecedor: this.fornecedor ? this.fornecedor.paraJson() : null,
        quantidade: this.quantidade, estoqueMinimo: this.estoqueMinimo,
        precoUnitario: this.precoUnitario, validade: this.validade,
      }, this.camposProprios());
    }
    static iso(data) {
      const p = (n) => String(n).padStart(2, '0');
      return `${data.getFullYear()}-${p(data.getMonth() + 1)}-${p(data.getDate())}`;
    }
  }

  class Medicamento extends ItemEstoque {
    constructor(d = {}) {
      super(d);
      this.principioAtivo = d.principioAtivo || '';
      this.concentracao = d.concentracao || '';
      this.controlado = !!d.controlado;
    }
    get classe() { return 'MEDICAMENTO'; }
    get categoria() { return 'Medicamento'; }
    camposProprios() { return { principioAtivo: this.principioAtivo, concentracao: this.concentracao, controlado: this.controlado }; }
    detalhes() { return `${this.principioAtivo}, ${this.concentracao}${this.controlado ? ', CONTROLADO' : ''}`; }
  }

  class Vacina extends ItemEstoque {
    constructor(d = {}) {
      super(d);
      this.doencaAlvo = d.doencaAlvo || '';
      this.dosesPorFrasco = d.dosesPorFrasco || 1;
    }
    get classe() { return 'VACINA'; }
    get categoria() { return 'Vacina'; }
    get diasAvisoValidade() { return 60; }
    camposProprios() { return { doencaAlvo: this.doencaAlvo, dosesPorFrasco: this.dosesPorFrasco }; }
    detalhes() { return `${this.doencaAlvo}, ${this.dosesPorFrasco} dose(s) por frasco`; }
  }

  class Insumo extends ItemEstoque {
    constructor(d = {}) {
      super(d);
      this.unidadeMedida = d.unidadeMedida || '';
      this.esteril = !!d.esteril;
    }
    get classe() { return 'INSUMO'; }
    get categoria() { return 'Insumo'; }
    get diasAvisoValidade() { return 15; }
    camposProprios() { return { unidadeMedida: this.unidadeMedida, esteril: this.esteril }; }
    detalhes() { return `${this.unidadeMedida}${this.esteril ? ', estéril' : ''}`; }
  }

  /* Reconstrói o item na subclasse indicada pelo campo discriminador 'classe'. */
  function itemDeJson(d) {
    switch (d.classe) {
      case 'MEDICAMENTO': return new Medicamento(d);
      case 'VACINA': return new Vacina(d);
      case 'INSUMO': return new Insumo(d);
      default: throw new Error('Classe de item desconhecida: ' + d.classe);
    }
  }

  // proxies

  class ProxyBase {
    constructor(http, href) {
      this.http = http;
      this.href = href;
    }
  }

  /* Proxy de objeto criado em tempo de execução, usado POR REFERÊNCIA (guarda só o endereço). */
  class ProxyPorReferencia extends ProxyBase {
    constructor(http, referencia) {
      super(http, referencia.href);
      this.referencia = referencia;
    }
    get objeto() { return this.referencia.objeto; }
    toString() { return `${this.referencia.objeto} (${this.referencia.interface}) @ ${this.href}`; }
  }

  class AutenticacaoProxy extends ProxyBase {
    autenticar(usuario, senha) {
      return this.http.doOperation('POST', this.href + '/sessoes', { usuario, senha });
    }
    encerrarSessao(token) {
      return this.http.doOperation('DELETE', this.href + '/sessoes/atual', null, token);
    }
  }

  class PedidoReposicaoProxy extends ProxyPorReferencia {
    consultar() { return this.http.doOperation('GET', this.href); }
    adicionarItem(token, idItem, quantidade) {
      return this.http.doOperation('POST', this.href + '/itens', { idItem, quantidade }, token);
    }
    confirmarRecebimento(token) { return this.http.doOperation('POST', this.href + '/recebimento', null, token); }
    cancelar(token) { return this.http.doOperation('POST', this.href + '/cancelamento', null, token); }
  }

  class AssinaturaProxy extends ProxyPorReferencia {
    /* Long polling: a resposta só volta quando há mensagem ou a espera termina. */
    aguardar(esperaMs, sinal) {
      return this.http.doOperation('GET', `${this.href}/mensagens?espera=${Math.floor(esperaMs)}`, null, null,
        { tempoLimiteMs: esperaMs + 15000, sinal });
    }
    usuario() { return this.http.doOperation('GET', this.href).then((r) => r.usuario); }
  }

  class EstoqueProxy extends ProxyBase {
    async listarItens() { return (await this.http.doOperation('GET', this.href + '/itens')).map(itemDeJson); }
    async buscarItem(id) { return itemDeJson(await this.http.doOperation('GET', `${this.href}/itens/${id}`)); }
    listarFornecedores() {
      return this.http.doOperation('GET', this.href + '/fornecedores').then((l) => l.map((f) => new Fornecedor(f)));
    }
    /* Passagem por VALOR: o item local vai como JSON; volta outra cópia, com o id gerado. */
    async cadastrarItem(token, item) {
      return itemDeJson(await this.http.doOperation('POST', this.href + '/itens', item.paraJson(), token));
    }
    registrarEntrada(token, id, quantidade) {
      return this.http.doOperation('POST', `${this.href}/itens/${id}/entradas`, { quantidade }, token);
    }
    registrarSaida(token, id, quantidade, motivo) {
      const corpo = { quantidade };
      if (motivo) corpo.motivo = motivo;
      return this.http.doOperation('POST', `${this.href}/itens/${id}/saidas`, corpo, token);
    }
    listarMovimentacoes(id = 0) {
      return this.http.doOperation('GET', this.href + '/movimentacoes' + (id ? `?item=${id}` : ''));
    }
    /* Passagem por REFERÊNCIA: o pedido fica no servidor; recebemos o endereço dele. */
    async abrirPedidoReposicao(token, idFornecedor) {
      const ref = await this.http.doOperation('POST', this.href + '/pedidos', { idFornecedor }, token);
      return new PedidoReposicaoProxy(this.http, ref);
    }
  }

  class NotificacoesProxy extends ProxyBase {
    /* joinGroup: devolve a referência da assinatura criada no servidor. */
    async entrarGrupo(token) {
      const ref = await this.http.doOperation('POST', this.href + '/assinaturas', null, token);
      return new AssinaturaProxy(this.http, ref);
    }
    /* leaveGroup: a referência vai como ARGUMENTO e o servidor a converte no objeto real. */
    sairGrupo(assinatura) {
      return this.http.doOperation('POST', this.href + '/saida', { assinatura: assinatura.referencia });
    }
    publicar(token, tipo, mensagem) {
      return this.http.doOperation('POST', this.href + '/mensagens', { tipo, mensagem }, token);
    }
    membros() { return this.http.doOperation('GET', this.href + '/membros'); }
  }

  class ConexaoClinica {
    constructor(http, enderecos) {
      this.http = http;
      this.autenticacao = new AutenticacaoProxy(http, enderecos.Autenticacao);
      this.estoque = new EstoqueProxy(http, enderecos.Estoque);
      this.notificacoes = new NotificacoesProxy(http, enderecos.Notificacoes);
    }
    /* Descobre os objetos distribuídos do servidor (GET /api), como o lookup do registro do TD02. */
    static async conectar(baseUrl, fetchImpl) {
      const http = new ClienteHttp(baseUrl, fetchImpl);
      const indice = await http.doOperation('GET', '/api');
      const enderecos = {};
      for (const o of indice.objetos) enderecos[o.objeto] = o.href;
      for (const nome of ['Autenticacao', 'Estoque', 'Notificacoes']) {
        if (!enderecos[nome]) throw new FalhaComunicacao(`O servidor não oferece o objeto ${nome}.`);
      }
      return new ConexaoClinica(http, enderecos);
    }
  }

  // notificações em tempo real

  /* Fica pedindo mensagens à assinatura (long polling) e entrega cada uma assim que chega. */
  class OuvinteNotificacoes {
    constructor(assinatura, aoReceber, aoAvisar = () => {}) {
      this.assinatura = assinatura;
      this.aoReceber = aoReceber;
      this.aoAvisar = aoAvisar;
      this.ativo = false;
      this.controle = null;
      this.esperaMs = 10000;
    }
    iniciar() {
      this.ativo = true;
      this.laco = this._laco();
      return this;
    }
    parar() {
      this.ativo = false;
      if (this.controle) this.controle.abort();
    }
    async _laco() {
      let falhas = 0;
      while (this.ativo) {
        this.controle = new AbortController();
        try {
          const lista = await this.assinatura.aguardar(this.esperaMs, this.controle.signal);
          falhas = 0;
          for (const n of lista) if (this.ativo) this.aoReceber(n);
        } catch (e) {
          if (!this.ativo) return;
          if (e instanceof ErroApi) {
            this.ativo = false;
            this.aoAvisar(`Assinatura encerrada pelo servidor (${e.mensagem}).`, true);
          } else {
            falhas++;
            if (falhas === 1) this.aoAvisar('Sem contato com o servidor. Tentando de novo...', false);
            await new Promise((r) => setTimeout(r, Math.min(5, falhas) * 1000));
          }
        }
      }
    }
  }

  return {
    ErroApi, FalhaComunicacao, ClienteHttp, ConexaoClinica, OuvinteNotificacoes,
    Fornecedor, ItemEstoque, Medicamento, Vacina, Insumo, itemDeJson,
    AutenticacaoProxy, EstoqueProxy, PedidoReposicaoProxy, NotificacoesProxy, AssinaturaProxy,
  };
});
