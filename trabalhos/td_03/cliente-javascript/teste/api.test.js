// Testes da biblioteca JavaScript contra um servidor em execução.
// API_URL=http://localhost:8080 node --test teste/
// (o script testes.sh/testes.ps1 da raiz do projeto sobe o servidor Java e roda isto sozinho.)
const test = require('node:test');
const assert = require('node:assert/strict');
const api = require('../js/api.js');

const URL = process.env.API_URL || 'http://localhost:8080';
const sufixo = () => Math.random().toString(16).slice(2, 8);

async function esperaErro(promessa, status, codigo) {
  await assert.rejects(promessa, (e) => {
    assert.ok(e instanceof api.ErroApi, 'esperava ErroApi mas foi ' + e);
    assert.equal(e.status, status);
    if (codigo) assert.equal(e.codigo, codigo);
    return true;
  });
}

let c;
async function entrar(usuario = 'estoquista', senha = 'est123') {
  return (await c.autenticacao.autenticar(usuario, senha)).token;
}

test.before(async () => { c = await api.ConexaoClinica.conectar(URL); });

test('descobre os três objetos distribuídos', () => {
  for (const p of [c.autenticacao, c.estoque, c.notificacoes]) assert.ok(p.href.startsWith(URL + '/api/'), p.href);
});

test('autenticação: aceita, recusa e exige token', async () => {
  const sessao = await c.autenticacao.autenticar('veterinario', 'vet123');
  assert.equal(sessao.usuario, 'veterinario');
  await esperaErro(c.autenticacao.autenticar('veterinario', 'errada'), 401, 'NAO_AUTENTICADO');
  await esperaErro(c.autenticacao.autenticar('intruso', 'x'), 401, 'NAO_AUTENTICADO');
  await esperaErro(c.estoque.registrarEntrada('token-falso', 1, 1), 401, 'NAO_AUTENTICADO');
  await c.autenticacao.encerrarSessao(sessao.token);
  await esperaErro(c.estoque.registrarEntrada(sessao.token, 1, 1), 401);
});

test('servidor inexistente vira FalhaComunicacao', async () => {
  await assert.rejects(api.ConexaoClinica.conectar('http://localhost:1'), api.FalhaComunicacao);
});

test('consultas devolvem as subclasses e a agregação', async () => {
  const itens = await c.estoque.listarItens();
  assert.ok(itens.length >= 9);
  assert.ok(itens.every((i) => i instanceof api.ItemEstoque));
  assert.ok(itens.some((i) => i instanceof api.Medicamento));
  assert.ok(itens.some((i) => i instanceof api.Vacina));
  assert.ok(itens.some((i) => i instanceof api.Insumo));
  const item = await c.estoque.buscarItem(1);
  assert.ok(item instanceof api.Medicamento);
  assert.equal(item.fornecedor.nome, 'VetPharma');
  assert.ok((await c.estoque.listarFornecedores()).length >= 4);
  await esperaErro(c.estoque.buscarItem(99999), 404, 'OBJETO_INEXISTENTE');
  assert.throws(() => api.itemDeJson({ classe: 'FERRAMENTA' }));
});

test('por valor: cadastrar envia uma cópia e o objeto local não muda', async () => {
  const token = await entrar();
  const f = new api.Fornecedor({ id: 2 });
  const locais = [
    new api.Medicamento({ nome: 'Dipirona ' + sufixo(), fornecedor: f, quantidade: 30, estoqueMinimo: 5, precoUnitario: 9.9,
      validade: '2031-01-01', principioAtivo: 'Dipirona', concentracao: '500 mg' }),
    new api.Vacina({ nome: 'Giárdia ' + sufixo(), fornecedor: f, quantidade: 10, estoqueMinimo: 2, precoUnitario: 60,
      validade: '2031-01-01', doencaAlvo: 'Giardíase', dosesPorFrasco: 1 }),
    new api.Insumo({ nome: 'Gaze ' + sufixo(), fornecedor: f, quantidade: 100, estoqueMinimo: 20, precoUnitario: 0.5,
      unidadeMedida: 'pacote', esteril: true }),
  ];
  for (const local of locais) {
    const criado = await c.estoque.cadastrarItem(token, local);
    assert.ok(criado.id > 0);
    assert.equal(local.id, 0, 'o objeto local continua com id 0');
    assert.equal(criado.constructor, local.constructor, 'a subclasse volta preservada');
    assert.equal(criado.fornecedor.nome, 'PetSaude');
    local.quantidade = 777;
    assert.notEqual((await c.estoque.buscarItem(criado.id)).quantidade, 777);
  }
});

test('cadastrar valida as regras de cada subclasse', async () => {
  const token = await entrar();
  const f = new api.Fornecedor({ id: 2 });
  await esperaErro(c.estoque.cadastrarItem(token, new api.Insumo({ nome: 'X', fornecedor: new api.Fornecedor({ id: 77 }), quantidade: 1, unidadeMedida: 'un' })), 400);
  await esperaErro(c.estoque.cadastrarItem(token, new api.Medicamento({ nome: 'Sem validade', fornecedor: f, quantidade: 1, principioAtivo: 'A', concentracao: '1' })), 400);
  await esperaErro(c.estoque.cadastrarItem(token, new api.Vacina({ nome: 'Vencida', fornecedor: f, quantidade: 1, validade: '2020-01-01', doencaAlvo: 'Raiva' })), 400);
  const nome = 'Unico ' + sufixo();
  await c.estoque.cadastrarItem(token, new api.Insumo({ nome, fornecedor: f, quantidade: 1, unidadeMedida: 'un' }));
  await esperaErro(c.estoque.cadastrarItem(token, new api.Insumo({ nome: nome.toUpperCase(), fornecedor: f, quantidade: 1, unidadeMedida: 'un' })), 409, 'OPERACAO_NEGADA');
});

test('entradas, saídas e regras de negócio', async () => {
  const token = await entrar('veterinario', 'vet123');
  const item = await c.estoque.cadastrarItem(token, new api.Insumo({ nome: 'Luva ' + sufixo(),
    fornecedor: new api.Fornecedor({ id: 4 }), quantidade: 10, estoqueMinimo: 1, unidadeMedida: 'caixa' }));
  const saida = await c.estoque.registrarSaida(token, item.id, 4, 'procedimento');
  assert.deepEqual([saida.tipo, saida.saldoApos, saida.responsavel], ['SAIDA', 6, 'veterinario']);
  assert.equal((await c.estoque.registrarEntrada(token, item.id, 3)).saldoApos, 9);
  assert.equal((await c.estoque.listarMovimentacoes(item.id)).length, 2);
  await esperaErro(c.estoque.registrarSaida(token, item.id, 50, 'x'), 409, 'OPERACAO_NEGADA');
  await esperaErro(c.estoque.registrarSaida(token, item.id, -2), 400, 'ARGUMENTO_INVALIDO');
  await esperaErro(c.estoque.registrarEntrada(token, 99999, 1), 404, 'OBJETO_INEXISTENTE');
  const controlado = (await c.estoque.listarItens()).find((i) => i instanceof api.Medicamento && i.controlado);
  await esperaErro(c.estoque.registrarSaida(token, controlado.id, 1), 409, 'OPERACAO_NEGADA');
  assert.equal((await c.estoque.registrarSaida(token, controlado.id, 1, 'analgesia')).tipo, 'SAIDA');
});

test('por referência: dois proxies com a mesma referência atuam no mesmo pedido', async () => {
  const token = await entrar();
  const p1 = await c.estoque.abrirPedidoReposicao(token, 4);
  const p2 = new api.PedidoReposicaoProxy(c.http, p1.referencia);
  assert.equal(p1.href, p2.href);
  await p1.adicionarItem(token, 7, 5);
  assert.equal((await p2.consultar()).itens.length, 1, 'p2 enxerga a alteração de p1');
  await esperaErro(p1.adicionarItem(token, 1, 5), 409, 'OPERACAO_NEGADA');
  assert.equal((await p2.cancelar(token)).status, 'CANCELADO');
  assert.equal((await p1.consultar()).status, 'CANCELADO', 'p1 enxerga o cancelamento de p2');
});

test('pedido confirmado dá entrada em lote', async () => {
  const token = await entrar();
  const antes = (await c.estoque.buscarItem(7)).quantidade;
  const pedido = await c.estoque.abrirPedidoReposicao(token, 4);
  await pedido.adicionarItem(token, 7, 100);
  const visto = await pedido.adicionarItem(token, 7, 50);
  assert.equal(visto.itens[0].quantidade, 150);
  assert.equal((await pedido.confirmarRecebimento(token)).length, 1);
  assert.equal((await c.estoque.buscarItem(7)).quantidade, antes + 150);
  await esperaErro(pedido.confirmarRecebimento(token), 409, 'OPERACAO_NEGADA');
});

test('grupo: mensagens chegam a todos os membros e sair invalida a referência', async () => {
  const t1 = await entrar('veterinario', 'vet123');
  const t2 = await entrar('recepcao', 'rec123');
  const a1 = await c.notificacoes.entrarGrupo(t1);
  const a2 = await c.notificacoes.entrarGrupo(t2);
  try {
    assert.equal(await a1.usuario(), 'veterinario');
    const marca = sufixo();
    for (const tipo of ['NOTIFICACAO', 'ALERTA', 'ATUALIZACAO']) await c.notificacoes.publicar(t1, tipo, `${tipo} ${marca}`);
    for (const a of [a1, a2]) {
      const recebidas = new Set();
      const limite = Date.now() + 5000;
      while (recebidas.size < 3 && Date.now() < limite) {
        for (const n of await a.aguardar(1000)) if (n.mensagem.includes(marca)) recebidas.add(n.tipo);
      }
      assert.deepEqual([...recebidas].sort(), ['ALERTA', 'ATUALIZACAO', 'NOTIFICACAO']);
    }
    assert.ok((await c.notificacoes.membros()).includes('veterinario'));
    await c.notificacoes.sairGrupo(a2); // a referência vai como argumento
    await esperaErro(a2.aguardar(0), 404, 'OBJETO_INEXISTENTE');
    await esperaErro(c.notificacoes.sairGrupo(a2), 400);
  } finally {
    for (const a of [a1, a2]) await c.notificacoes.sairGrupo(a).catch(() => {});
  }
});

test('OuvinteNotificacoes entrega em tempo real (long polling)', async () => {
  const token = await entrar();
  const assinatura = await c.notificacoes.entrarGrupo(token);
  const marca = sufixo();
  let recebida;
  const chegou = new Promise((resolve) => { recebida = resolve; });
  const ouvinte = new api.OuvinteNotificacoes(assinatura, (n) => { if (n.mensagem.includes(marca)) recebida(n); }).iniciar();
  try {
    const inicio = Date.now();
    setTimeout(() => c.notificacoes.publicar(token, 'ALERTA', 'urgente ' + marca), 400);
    const n = await Promise.race([chegou, new Promise((_, rej) => setTimeout(() => rej(new Error('não chegou')), 8000))]);
    assert.equal(n.tipo, 'ALERTA');
    assert.ok(Date.now() - inicio < 4000, 'chegou bem antes do fim da espera máxima');
  } finally {
    ouvinte.parar();
    await c.notificacoes.sairGrupo(assinatura).catch(() => {});
  }
  await esperaErro(assinatura.aguardar(30001), 404); // a assinatura já saiu; antes disso seria 400
});

test('publicar valida a mensagem', async () => {
  const token = await entrar();
  await esperaErro(c.notificacoes.publicar(token, 'ALERTA', '  '), 400);
  await esperaErro(c.notificacoes.publicar(token, 'SPAM', 'x'), 400);
});

test('rastro mostra requisição e resposta', async () => {
  const mensagens = [];
  c.http.rastro = (m) => mensagens.push(m);
  await c.estoque.buscarItem(1);
  c.http.rastro = null;
  assert.deepEqual(mensagens.map((m) => m.sentido), ['>>>', '<<<']);
  assert.equal(mensagens[0].metodo, 'GET');
  assert.equal(mensagens[1].status, 200);
  assert.equal(mensagens[0].id, mensagens[1].id);
});
