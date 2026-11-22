/*
 Interface web do estoque veterinário (TD03). Usa a biblioteca js/api.js para falar com o
 servidor Java; aqui fica só a tela. Dados vindos do servidor entram na página sempre como
 texto (nunca como HTML).
*/
(function () {
  'use strict';
  const { ConexaoClinica, OuvinteNotificacoes, ErroApi, FalhaComunicacao, PedidoReposicaoProxy,
    Medicamento, Vacina, Insumo, Fornecedor } = window.EstoqueApi;

  const $ = (id) => document.getElementById(id);
  const estado = {
    conexao: null, sessao: null, ouvinte: null, assinatura: null,
    itens: [], fornecedores: [], pedido: null, naoLidas: 0, atualizacaoAgendada: null,
  };

  // utilitários de tela

  /* Cria um elemento: h('td', {className:'num'}, 'texto', outroElemento). Texto vai como textContent. */
  function h(tag, props, ...filhos) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(props || {})) {
      if (v === undefined || v === null || v === false) continue;
      if (k === 'className') el.className = v;
      else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
      else el.setAttribute(k, v === true ? '' : v);
    }
    for (const f of filhos.flat()) if (f !== null && f !== undefined && f !== false) el.append(f);
    return el;
  }

  const dinheiro = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const hora = (ms) => new Date(ms).toLocaleTimeString('pt-BR');
  const dataHora = (ms) => new Date(ms).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'medium' });
  const dataBr = (iso) => (iso ? iso.split('-').reverse().join('/') : '—');

  function toast(texto, tipo = '') {
    const t = h('div', { className: 'toast ' + tipo, role: 'status' }, texto);
    $('toasts').append(t);
    setTimeout(() => t.remove(), tipo === 'erro' ? 7000 : 3800);
  }

  function mensagemDe(e) {
    if (e instanceof ErroApi) return e.mensagem;
    if (e instanceof FalhaComunicacao) return 'Sem comunicação com o servidor: ' + e.message;
    return e.message || String(e);
  }

  /* Executa uma ação assíncrona mostrando erros como aviso; devolve undefined se falhar. */
  async function tentar(acao, botao) {
    if (botao) botao.disabled = true;
    try {
      return await acao();
    } catch (e) {
      toast(mensagemDe(e), 'erro');
      if (e instanceof ErroApi && e.status === 401) voltarAoLogin('Sessão encerrada. Entre novamente.');
    } finally {
      if (botao) botao.disabled = false;
    }
  }

  function guardar(chave, valor) { try { localStorage.setItem(chave, valor); } catch (e) { /* sem armazenamento */ } }
  function lembrar(chave) { try { return localStorage.getItem(chave); } catch (e) { return null; } }

  // login

  $('login-url').value = lembrar('estoquevet.url') || $('login-url').value;
  document.querySelectorAll('.chip').forEach((c) => c.addEventListener('click', () => {
    $('login-usuario').value = c.dataset.u;
    $('login-senha').value = c.dataset.s;
  }));

  $('form-login').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const erro = $('login-erro');
    erro.hidden = true;
    const botao = $('login-botao');
    botao.disabled = true;
    botao.textContent = 'Conectando…';
    try {
      const url = $('login-url').value.trim();
      estado.conexao = await ConexaoClinica.conectar(url);
      estado.sessao = await estado.conexao.autenticacao.autenticar($('login-usuario').value.trim(), $('login-senha').value);
      guardar('estoquevet.url', url);
      iniciarApp();
    } catch (e) {
      estado.conexao = null;
      erro.textContent = e instanceof ErroApi && e.status === 401 ? 'Usuário ou senha inválidos.'
        : e instanceof FalhaComunicacao ? 'Não foi possível conectar ao servidor. Ele está rodando em ' + $('login-url').value + '?'
        : mensagemDe(e);
      erro.hidden = false;
    } finally {
      botao.disabled = false;
      botao.textContent = 'Entrar';
    }
  });

  async function iniciarApp() {
    $('tela-login').hidden = true;
    $('app').hidden = false;
    $('nome-usuario').textContent = estado.sessao.usuario;
    estado.conexao.http.rastro = registrarRastro;
    $('feed').replaceChildren();
    $('feed-vazio').hidden = false;
    trocarAba('estoque');
    await tentar(async () => {
      estado.fornecedores = await estado.conexao.estoque.listarFornecedores();
      await carregarItens();
      await entrarNoGrupo(true);
    });
  }

  async function voltarAoLogin(aviso) {
    if (estado.ouvinte) estado.ouvinte.parar();
    estado.ouvinte = null;
    estado.assinatura = null;
    $('app').hidden = true;
    $('tela-login').hidden = false;
    if (aviso) { $('login-erro').textContent = aviso; $('login-erro').hidden = false; }
  }

  $('botao-sair').addEventListener('click', async () => {
    const { conexao, sessao } = estado;
    if (estado.assinatura) await sairDoGrupo(true);
    await conexao.autenticacao.encerrarSessao(sessao.token).catch(() => {});
    estado.sessao = null;
    voltarAoLogin();
  });

  // abas

  function trocarAba(nome) {
    document.querySelectorAll('.abas button').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.aba === nome)));
    document.querySelectorAll('[role=tabpanel]').forEach((s) => { s.hidden = s.id !== 'aba-' + nome; });
    if (nome === 'notif') { estado.naoLidas = 0; atualizarContador(); atualizarMembros(); }
    if (nome === 'movs') carregarMovs();
    if (nome === 'pedidos') preencherSelectsPedido();
    if (nome === 'demo') preencherDemo();
  }
  document.querySelectorAll('.abas button').forEach((b) => b.addEventListener('click', () => trocarAba(b.dataset.aba)));

  const abaAtual = () => (document.querySelector('.abas [aria-selected=true]') || {}).dataset?.aba;

  // estoque

  async function carregarItens() {
    estado.itens = await estado.conexao.estoque.listarItens();
    desenharItens();
  }

  function desenharItens() {
    const filtro = $('busca').value.trim().toLowerCase();
    const hoje = new Date();
    const corpo = $('tabela-itens').tBodies[0];
    corpo.replaceChildren();
    let total = 0;
    const visiveis = estado.itens.filter((i) => !filtro ||
      [i.nome, i.categoria, i.fornecedor && i.fornecedor.nome].join(' ').toLowerCase().includes(filtro));
    for (const i of estado.itens) total += i.quantidade * i.precoUnitario;
    for (const i of visiveis) {
      const selos = [];
      if (i.vencido(hoje)) selos.push(h('span', { className: 'selo selo--perigo' }, 'vencido'));
      else if (i.venceEmBreve(hoje)) selos.push(h('span', { className: 'selo selo--aviso' }, 'vence em breve'));
      if (i.abaixoDoMinimo()) selos.push(h('span', { className: 'selo selo--aviso' }, 'estoque baixo'));
      corpo.append(h('tr', {},
        h('td', {}, String(i.id)),
        h('td', {}, h('strong', {}, i.nome), h('span', { className: 'selo' }, i.categoria), h('span', { className: 'quase' }, i.detalhes())),
        h('td', {}, i.fornecedor ? i.fornecedor.nome || '#' + i.fornecedor.id : '—'),
        h('td', { className: 'num' }, String(i.quantidade), ...selos.map((s) => h('div', {}, s))),
        h('td', { className: 'num' }, String(i.estoqueMinimo)),
        h('td', { className: 'num' }, dinheiro(i.precoUnitario)),
        h('td', {}, dataBr(i.validade)),
        h('td', { className: 'acoes' },
          h('button', { className: 'botao botao--mini', onclick: () => abrirMov(i, 'entrada') }, '+ Entrada'), ' ',
          h('button', { className: 'botao botao--mini', onclick: () => abrirMov(i, 'saida') }, '− Saída'), ' ',
          h('button', { className: 'botao botao--leve botao--mini', onclick: () => mostrarJson('Item #' + i.id + ' (JSON enviado pelo servidor)', i.paraJson()) }, 'JSON'))));
    }
    if (!visiveis.length) corpo.append(h('tr', {}, h('td', { colspan: 8, className: 'vazio' }, 'Nenhum item encontrado.')));
    $('total-estoque').textContent = `${estado.itens.length} itens · valor total em estoque ${dinheiro(total)}`;
  }
  $('busca').addEventListener('input', desenharItens);

  function mostrarJson(titulo, objeto) {
    $('json-titulo').textContent = titulo;
    $('json-corpo').textContent = JSON.stringify(objeto, null, 2);
    $('dialogo-json').showModal();
  }

  // entrada/saída
  let movAtual = null;
  function abrirMov(item, tipo) {
    movAtual = { item, tipo };
    $('mov-titulo').textContent = (tipo === 'entrada' ? 'Entrada de ' : 'Saída de ') + item.nome;
    $('mov-qtd').value = 1;
    $('mov-motivo').value = '';
    $('mov-motivo-rotulo').hidden = tipo === 'entrada';
    $('mov-motivo').placeholder = item.controlado ? 'Obrigatório: medicamento controlado' : 'Opcional';
    $('dialogo-mov').showModal();
    $('mov-qtd').focus();
  }
  $('form-mov').addEventListener('submit', async (ev) => {
    const confirmou = ev.submitter && ev.submitter.value === 'ok';
    if (!confirmou || !movAtual) return;
    const { item, tipo } = movAtual;
    const qtd = parseInt($('mov-qtd').value, 10);
    const motivo = $('mov-motivo').value.trim();
    await tentar(async () => {
      const mov = tipo === 'entrada'
        ? await estado.conexao.estoque.registrarEntrada(estado.sessao.token, item.id, qtd)
        : await estado.conexao.estoque.registrarSaida(estado.sessao.token, item.id, qtd, motivo);
      toast(`${mov.tipo === 'ENTRADA' ? 'Entrada' : 'Saída'} registrada. Saldo de ${mov.nomeItem}: ${mov.saldoApos}.`);
      await carregarItens();
    });
  });
  document.querySelectorAll('[data-fechar]').forEach((b) => b.addEventListener('click', () => b.closest('dialog').close()));

  // novo item (passagem por valor)
  function preencherFornecedores(select) {
    select.replaceChildren(...estado.fornecedores.map((f) => h('option', { value: f.id }, f.nome)));
  }
  $('botao-novo').addEventListener('click', () => {
    preencherFornecedores($('item-fornecedor'));
    $('item-erro').hidden = true;
    mostrarCamposDoTipo();
    $('dialogo-item').showModal();
  });
  function mostrarCamposDoTipo() {
    document.querySelectorAll('.campos-tipo').forEach((c) => { c.hidden = c.dataset.tipo !== $('item-tipo').value; });
  }
  $('item-tipo').addEventListener('change', mostrarCamposDoTipo);

  $('form-item').addEventListener('submit', async (ev) => {
    if (!ev.submitter || ev.submitter.value !== 'ok') return;
    ev.preventDefault(); // só fecha se o servidor aceitar
    const base = {
      nome: $('item-nome').value.trim(), fornecedor: new Fornecedor({ id: Number($('item-fornecedor').value) }),
      quantidade: parseInt($('item-qtd').value, 10), estoqueMinimo: parseInt($('item-min').value, 10),
      precoUnitario: parseFloat($('item-preco').value), validade: $('item-validade').value || null,
    };
    let local;
    switch ($('item-tipo').value) {
      case 'MEDICAMENTO': local = new Medicamento({ ...base, principioAtivo: $('item-principio').value.trim(),
        concentracao: $('item-concentracao').value.trim(), controlado: $('item-controlado').checked }); break;
      case 'VACINA': local = new Vacina({ ...base, doencaAlvo: $('item-doenca').value.trim(),
        dosesPorFrasco: parseInt($('item-doses').value, 10) }); break;
      default: local = new Insumo({ ...base, unidadeMedida: $('item-unidade').value.trim(), esteril: $('item-esteril').checked });
    }
    try {
      const criado = await estado.conexao.estoque.cadastrarItem(estado.sessao.token, local);
      $('dialogo-item').close();
      toast(`"${criado.nome}" cadastrado com id ${criado.id} (o objeto local continua com id ${local.id}: o servidor usou uma cópia).`);
      $('form-item').reset();
      await carregarItens();
    } catch (e) {
      $('item-erro').textContent = mensagemDe(e);
      $('item-erro').hidden = false;
    }
  });

  // movimentações

  async function carregarMovs() {
    const sel = $('filtro-mov');
    if (sel.options.length - 1 !== estado.itens.length) {
      const atual = sel.value;
      sel.replaceChildren(h('option', { value: 0 }, 'Todos os itens'),
        ...estado.itens.map((i) => h('option', { value: i.id }, `#${i.id} ${i.nome}`)));
      sel.value = atual || '0';
    }
    await tentar(async () => {
      const movs = (await estado.conexao.estoque.listarMovimentacoes(Number(sel.value))).slice().reverse();
      const corpo = $('tabela-movs').tBodies[0];
      corpo.replaceChildren(...movs.map((m) => h('tr', {},
        h('td', {}, String(m.id)), h('td', {}, dataHora(m.instante)),
        h('td', {}, h('span', { className: 'selo ' + (m.tipo === 'ENTRADA' ? 'selo--ok' : 'selo--aviso') }, m.tipo)),
        h('td', {}, m.nomeItem), h('td', { className: 'num' }, String(m.quantidade)),
        h('td', { className: 'num' }, String(m.saldoApos)), h('td', {}, m.responsavel), h('td', {}, m.motivo || '—'))));
      if (!movs.length) corpo.append(h('tr', {}, h('td', { colspan: 8, className: 'vazio' }, 'Nenhuma movimentação ainda.')));
    });
  }
  $('filtro-mov').addEventListener('change', carregarMovs);

  // pedidos (passagem por referência)

  function preencherSelectsPedido() {
    const atual = $('pedido-fornecedor').value;
    preencherFornecedores($('pedido-fornecedor'));
    if (atual) $('pedido-fornecedor').value = atual;
    desenharPedido();
  }

  $('form-pedido-abrir').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    await tentar(async () => {
      estado.pedido = await estado.conexao.estoque.abrirPedidoReposicao(estado.sessao.token, Number($('pedido-fornecedor').value));
      toast('Pedido aberto no servidor. Você recebeu uma referência.');
      await atualizarPedido();
    });
  });

  $('botao-pedido-href').addEventListener('click', async () => {
    const href = $('pedido-href').value.trim();
    if (!href) return;
    await tentar(async () => {
      // monta um proxy só com o endereço, como se outro cliente tivesse passado a referência
      const nome = href.split('/').pop();
      estado.pedido = new PedidoReposicaoProxy(estado.conexao.http, { objeto: nome, interface: 'PedidoReposicaoRemoto', href });
      await atualizarPedido();
      toast('Conectado ao pedido pela referência.');
    });
  });

  async function atualizarPedido() {
    estado.pedidoDados = await estado.pedido.consultar();
    desenharPedido();
  }

  function desenharPedido() {
    const cartao = $('cartao-pedido');
    cartao.hidden = !estado.pedido || !estado.pedidoDados;
    if (cartao.hidden) return;
    const p = estado.pedidoDados;
    const aberto = p.status === 'ABERTO';
    $('pedido-titulo').replaceChildren(`Pedido #${p.id} · ${p.fornecedor.nome} `,
      h('span', { className: 'selo ' + (aberto ? '' : p.status === 'RECEBIDO' ? 'selo--ok' : 'selo--perigo') }, p.status));
    $('pedido-ref').textContent = estado.pedido.href;
    const corpo = $('tabela-pedido').tBodies[0];
    corpo.replaceChildren(...p.itens.map((i) => h('tr', {}, h('td', {}, `#${i.idItem} ${i.nomeItem}`), h('td', { className: 'num' }, String(i.quantidade)))));
    if (!p.itens.length) corpo.append(h('tr', {}, h('td', { colspan: 2, className: 'vazio' }, 'Sem itens.')));
    const doFornecedor = estado.itens.filter((i) => i.fornecedor && i.fornecedor.id === p.fornecedor.id);
    $('pedido-item').replaceChildren(...doFornecedor.map((i) => h('option', { value: i.id }, `#${i.id} ${i.nome}`)));
    $('form-pedido-item').querySelectorAll('select,input,button').forEach((el) => { el.disabled = !aberto; });
    $('botao-pedido-confirmar').disabled = !aberto;
    $('botao-pedido-cancelar').disabled = !aberto;
  }

  $('form-pedido-item').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    await tentar(async () => {
      estado.pedidoDados = await estado.pedido.adicionarItem(estado.sessao.token, Number($('pedido-item').value), parseInt($('pedido-qtd').value, 10));
      desenharPedido();
    });
  });
  $('botao-pedido-atualizar').addEventListener('click', () => tentar(atualizarPedido));
  $('botao-pedido-confirmar').addEventListener('click', () => tentar(async () => {
    const movs = await estado.pedido.confirmarRecebimento(estado.sessao.token);
    toast(`Recebimento confirmado: ${movs.length} item(ns) deram entrada no estoque.`);
    await atualizarPedido();
    await carregarItens();
  }));
  $('botao-pedido-cancelar').addEventListener('click', () => tentar(async () => {
    estado.pedidoDados = await estado.pedido.cancelar(estado.sessao.token);
    desenharPedido();
    toast('Pedido cancelado.', 'aviso');
  }));

  // notificações

  function atualizarContador() {
    const c = $('contador-notif');
    c.hidden = estado.naoLidas === 0;
    c.textContent = String(estado.naoLidas);
  }

  function atualizarEstadoGrupo() {
    const dentro = !!estado.assinatura;
    $('pilula-grupo').textContent = dentro ? 'no grupo' : 'fora do grupo';
    $('pilula-grupo').classList.toggle('pilula--ativa', dentro);
    $('botao-grupo').textContent = dentro ? 'Sair do grupo' : 'Entrar no grupo';
  }

  async function entrarNoGrupo(silencioso) {
    if (estado.assinatura) return;
    estado.assinatura = await estado.conexao.notificacoes.entrarGrupo(estado.sessao.token);
    estado.ouvinte = new OuvinteNotificacoes(estado.assinatura, receber, (aviso, encerrada) => {
      toast(aviso, 'aviso');
      if (encerrada) { estado.assinatura = null; estado.ouvinte = null; atualizarEstadoGrupo(); }
    }).iniciar();
    atualizarEstadoGrupo();
    if (!silencioso) toast('Você entrou no grupo.');
  }

  async function sairDoGrupo(silencioso) {
    if (!estado.assinatura) return;
    const assinatura = estado.assinatura;
    estado.ouvinte.parar();
    estado.ouvinte = null;
    estado.assinatura = null;
    atualizarEstadoGrupo();
    await estado.conexao.notificacoes.sairGrupo(assinatura).catch(() => {});
    if (!silencioso) toast('Você saiu do grupo.', 'aviso');
  }

  $('botao-grupo').addEventListener('click', () => tentar(() => (estado.assinatura ? sairDoGrupo() : entrarNoGrupo()), $('botao-grupo')));

  function receber(n) {
    $('feed-vazio').hidden = true;
    $('feed').prepend(h('li', { className: n.tipo }, h('strong', {}, n.tipo), h('span', {}, n.mensagem), h('time', {}, hora(n.timestamp))));
    if (abaAtual() !== 'notif') { estado.naoLidas++; atualizarContador(); }
    if (n.tipo !== 'NOTIFICACAO') agendarAtualizacao(); // alterações de estoque: recarrega a tela
  }

  /* Junta várias notificações seguidas numa só atualização das listas. */
  function agendarAtualizacao() {
    clearTimeout(estado.atualizacaoAgendada);
    estado.atualizacaoAgendada = setTimeout(async () => {
      if (!estado.sessao) return;
      try {
        await carregarItens();
        if (abaAtual() === 'movs') await carregarMovs();
      } catch (e) { /* a próxima ação mostra o erro */ }
    }, 300);
  }

  async function atualizarMembros() {
    await tentar(async () => {
      const membros = await estado.conexao.notificacoes.membros();
      $('lista-membros').replaceChildren(...(membros.length ? membros.map((m) => h('li', {}, m)) : [h('li', { className: 'suave' }, 'Ninguém no grupo.')]));
    });
  }
  $('botao-membros').addEventListener('click', atualizarMembros);
  $('botao-limpar-notif').addEventListener('click', () => { $('feed').replaceChildren(); $('feed-vazio').hidden = false; });

  $('form-publicar').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    await tentar(async () => {
      await estado.conexao.notificacoes.publicar(estado.sessao.token, $('pub-tipo').value, $('pub-mensagem').value);
      $('pub-mensagem').value = '';
    });
  });

  // demonstração valor × referência

  function preencherDemo() {
    const atual = $('demo-item').value;
    $('demo-item').replaceChildren(...estado.itens.map((i) => h('option', { value: i.id }, `#${i.id} ${i.nome}`)));
    if (atual) $('demo-item').value = atual;
  }

  function linhasDemo(linhas) {
    $('saida-demo').textContent = linhas.join('\n');
  }

  $('botao-demo-valor').addEventListener('click', () => tentar(async () => {
    const id = Number($('demo-item').value) || 1;
    const out = ['== Passagem por VALOR =='];
    const copia = await estado.conexao.estoque.buscarItem(id);
    out.push(`1) GET do item ${id} devolveu uma cópia: quantidade=${copia.quantidade}, nome='${copia.nome}'`);
    copia.quantidade = 99999;
    copia.nome = 'ALTERADO SÓ NO CLIENTE';
    out.push(`2) Alterei a cópia local: quantidade=${copia.quantidade}, nome='${copia.nome}'`);
    const doServidor = await estado.conexao.estoque.buscarItem(id);
    out.push(`3) GET de novo: quantidade=${doServidor.quantidade}, nome='${doServidor.nome}'`);
    out.push('   O objeto do servidor não mudou: o cliente recebeu uma cópia serializada em JSON.');
    linhasDemo(out);
  }, $('botao-demo-valor')));

  $('botao-demo-ref').addEventListener('click', () => tentar(async () => {
    const out = ['== Passagem por REFERÊNCIA =='];
    const { estoque } = estado.conexao;
    const token = estado.sessao.token;
    const item = estado.itens[0] || (await estoque.listarItens())[0];
    const p1 = await estoque.abrirPedidoReposicao(token, item.fornecedor.id);
    out.push('1) abrir pedido devolveu uma REFERÊNCIA (o pedido ficou no servidor):', '   ' + p1);
    await p1.adicionarItem(token, item.id, 5);
    out.push('2) POST pela referência alterou o objeto no servidor: ' + JSON.stringify((await p1.consultar()).itens));
    const p2 = new PedidoReposicaoProxy(estado.conexao.http, p1.referencia);
    out.push('3) Um segundo proxy criado só com a referência vê o mesmo pedido: ' + JSON.stringify((await p2.consultar()).itens));
    const cancelado = await p2.cancelar(token);
    out.push(`4) Cancelei pelo segundo proxy; o primeiro enxerga: status=${(await p1.consultar()).status} (pedido #${cancelado.id})`);
    out.push('   Os dois proxies apontam para o MESMO objeto remoto.');
    linhasDemo(out);
  }, $('botao-demo-ref')));

  // protocolo

  const esperasEmCurso = new Set(); // ids das chamadas de long polling (só aparecem se a opção estiver marcada)

  function registrarRastro(m) {
    if (!$('rastro-ligado').checked) return;
    if (m.sentido === '>>>' && /\/mensagens\?espera=/.test(m.url)) esperasEmCurso.add(m.id);
    if (esperasEmCurso.has(m.id)) {
      if (m.sentido === '<<<') esperasEmCurso.delete(m.id);
      if (!$('rastro-polling').checked) return;
    }
    const cab = m.sentido === '>>>' ? `>>> #${m.id} ${m.metodo} ${m.url}` : `<<< #${m.id} ${m.status}`;
    const classe = m.sentido === '>>>' ? 'req' : m.status >= 400 ? 'err' : 'res';
    const pre = $('rastro');
    pre.append(h('span', { className: classe }, cab + '\n'));
    if (m.corpo) pre.append(h('span', { className: 'dim' }, '    ' + (m.corpo.length > 600 ? m.corpo.slice(0, 600) + '…' : m.corpo) + '\n'));
    while (pre.childNodes.length > 400) pre.removeChild(pre.firstChild);
    pre.scrollTop = pre.scrollHeight;
  }
  $('botao-limpar-rastro').addEventListener('click', () => $('rastro').replaceChildren());

  atualizarEstadoGrupo();
})();
