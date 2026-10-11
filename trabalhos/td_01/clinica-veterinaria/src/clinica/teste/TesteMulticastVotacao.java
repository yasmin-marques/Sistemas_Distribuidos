package clinica.teste;

import clinica.modelo.Medicamento;
import clinica.multicast.ClienteMulticast;
import clinica.multicast.Grupo;
import clinica.multicast.MonitorEstoque;
import clinica.multicast.Notificacao;
import clinica.multicast.OuvinteMulticast;
import clinica.multicast.RedeMulticast;
import clinica.multicast.ServidorMulticast;
import clinica.servico.DadosIniciais;
import clinica.servico.EstoqueServico;
import clinica.util.Json;
import clinica.votacao.ClienteVotacao;
import clinica.votacao.Contas;
import clinica.votacao.RespostaVotacao;
import clinica.votacao.Resultado;
import clinica.votacao.ServidorVotacao;
import clinica.votacao.Urna;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// Testes automatizados da Questão 4 (multicast) e da questão extra (votação)
public class TesteMulticastVotacao {

    private static int passou = 0;
    private static int falhou = 0;
    private static int pulou = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("JSON e formato da mensagem");
        executar("JSON: ida e volta com caracteres especiais", TesteMulticastVotacao::testeJson);
        executar("JSON: texto inválido é rejeitado", TesteMulticastVotacao::testeJsonInvalido);
        executar("Notificação no formato do enunciado", TesteMulticastVotacao::testeNotificacao);

        System.out.println("\nQUESTÃO 4: Multicast");
        executar("Autenticação TCP aceita e recusa", TesteMulticastVotacao::testeAutenticacao);
        executar("Servidor valida tipo e tamanho da mensagem", TesteMulticastVotacao::testeValidacaoEnvio);
        executarMulticast("Vários clientes recebem NOTIFICACAO, ALERTA e ATUALIZACAO", TesteMulticastVotacao::testeVariosClientes);
        executarMulticast("joinGroup / leaveGroup", TesteMulticastVotacao::testeEntrarESair);
        executarMulticast("Envio concorrente de várias threads", TesteMulticastVotacao::testeEnvioConcorrente);
        executarMulticast("Monitor de estoque envia cada alerta uma vez", TesteMulticastVotacao::testeMonitor);

        System.out.println("\n===== QUESTÃO EXTRA: Votação =====");
        executar("Urna: voto único, remoção e apuração", TesteMulticastVotacao::testeUrna);
        executar("Urna: empate e votação sem votos", TesteMulticastVotacao::testeUrnaEmpate);
        executar("TCP: login, lista de candidatos e permissões", TesteMulticastVotacao::testeFluxoTcp);
        executar("Prazo: votos recusados e resultado após o fim", TesteMulticastVotacao::testePrazo);
        executar("Vários eleitores votando ao mesmo tempo", TesteMulticastVotacao::testeConcorrenciaVotacao);
        executarMulticast("Nota do administrador chega ao eleitor por multicast", TesteMulticastVotacao::testeNota);

        System.out.println("\nResultado: " + passou + " passou, " + falhou + " falhou"
                + (pulou > 0 ? ", " + pulou + " pulado(s) (sem interface multicast)" : "") + ".");
        System.exit(falhou == 0 ? 0 : 1);
    }

    // JSON/mensagem

    private static void testeJson() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("texto", "aspas \" barra \\ linha1\nlinha2\ttab ação ✓");
        original.put("inteiro", 123456789012L);
        original.put("decimal", 2.5);
        original.put("ligado", true);
        original.put("nada", null);
        original.put("lista", Arrays.asList(1L, "dois", Arrays.asList(true, false)));
        Map<String, Object> interno = new LinkedHashMap<>();
        interno.put("a", "b");
        original.put("objeto", interno);

        Map<String, Object> lido = Json.lerObjeto(Json.escrever(original));
        verificar(original.equals(lido), "objeto lido difere do original: " + lido);
        verificar(!Json.escrever(original).contains("\n"), "JSON não pode conter quebra de linha crua");
        verificar("A".equals(Json.texto(Json.lerObjeto("{\"x\":\"\\u0041\"}"), "x")), "escape \\u");
    }

    private static void testeJsonInvalido() {
        for (String ruim : new String[] {"", "{", "{\"a\":}", "{\"a\":1", "[1,]", "\"aberto", "{a:1}", "{} lixo", "nulo"}) {
            esperarExcecao(IllegalArgumentException.class, () -> Json.ler(ruim));
        }
        esperarExcecao(IllegalArgumentException.class, () -> Json.lerObjeto("[1,2]"));
    }

    private static void testeNotificacao() {
        Notificacao n = new Notificacao("NOTIFICACAO", "Novo dado disponível", 123456789L);
        verificar(n.toJson().equals("{\"tipo\":\"NOTIFICACAO\",\"mensagem\":\"Novo dado disponível\",\"timestamp\":123456789}"),
                "formato diferente do sugerido: " + n.toJson());

        Notificacao especial = new Notificacao("ALERTA", "Dose \"alta\"\nrever \\ agora", 42L);
        Notificacao lida = Notificacao.fromJson(especial.toJson());
        verificar(lida.getTipo().equals("ALERTA") && lida.getMensagem().equals(especial.getMensagem())
                && lida.getTimestamp() == 42L, "ida e volta falhou");
        esperarExcecao(IllegalArgumentException.class, () -> Notificacao.fromJson("{\"mensagem\":\"x\",\"timestamp\":1}"));
    }

    // Q4

    private static void testeAutenticacao() throws Exception {
        try (ServidorMulticast servidor = new ServidorMulticast(0, "230.0.2.1", livre())) {
            servidor.iniciar();
            int porta = servidor.getPortaAutenticacao();

            Grupo grupo = ClienteMulticast.autenticar("localhost", porta, "veterinario", "vet123");
            verificar(grupo.getEndereco().equals("230.0.2.1"), "deveria receber o endereço do grupo");
            verificar(servidor.getAutenticados().contains("veterinario"), "usuário deveria constar como autenticado");

            esperarExcecao(IOException.class, () -> ClienteMulticast.autenticar("localhost", porta, "veterinario", "errada"));
            esperarExcecao(IOException.class, () -> ClienteMulticast.autenticar("localhost", porta, "ninguem", "x"));
        }
    }

    private static void testeValidacaoEnvio() throws Exception {
        try (ServidorMulticast servidor = new ServidorMulticast(0, "230.0.2.2", livre())) {
            esperarExcecao(IllegalArgumentException.class, () -> servidor.enviar("QUALQUER", "x"));
            esperarExcecao(IllegalArgumentException.class, () -> servidor.enviar(Notificacao.ALERTA, "x".repeat(2000)));
        }
    }

    private static void testeVariosClientes() throws Exception {
        int porta = livre();
        try (ServidorMulticast servidor = new ServidorMulticast(0, "230.0.2.3", porta)) {
            servidor.iniciar();
            BlockingQueue<Notificacao> filaA = new LinkedBlockingQueue<>();
            BlockingQueue<Notificacao> filaB = new LinkedBlockingQueue<>();
            BlockingQueue<Notificacao> filaC = new LinkedBlockingQueue<>();
            try (OuvinteMulticast a = new OuvinteMulticast(new Grupo("230.0.2.3", porta), filaA::add);
                 OuvinteMulticast b = new OuvinteMulticast(new Grupo("230.0.2.3", porta), filaB::add);
                 OuvinteMulticast c = new OuvinteMulticast(new Grupo("230.0.2.3", porta), filaC::add)) {
                a.entrar();
                b.entrar();
                c.entrar();

                servidor.enviar(Notificacao.NOTIFICACAO, "Novo dado disponível");
                servidor.enviar(Notificacao.ALERTA, "Estoque baixo");
                servidor.enviar(Notificacao.ATUALIZACAO, "Agenda atualizada");

                Set<String> esperados = Set.of("NOTIFICACAO", "ALERTA", "ATUALIZACAO");
                for (BlockingQueue<Notificacao> fila : List.of(filaA, filaB, filaC)) {
                    verificar(tipos(fila, 3).equals(esperados), "cada cliente deveria receber os 3 tipos");
                }
            }
        }
    }

    private static void testeEntrarESair() throws Exception {
        int porta = livre();
        try (ServidorMulticast servidor = new ServidorMulticast(0, "230.0.2.4", porta)) {
            servidor.iniciar();
            BlockingQueue<Notificacao> filaA = new LinkedBlockingQueue<>();
            BlockingQueue<Notificacao> filaB = new LinkedBlockingQueue<>();
            try (OuvinteMulticast a = new OuvinteMulticast(new Grupo("230.0.2.4", porta), filaA::add);
                 OuvinteMulticast b = new OuvinteMulticast(new Grupo("230.0.2.4", porta), filaB::add)) {
                verificar(!a.estaNoGrupo(), "não deveria estar no grupo antes do joinGroup");
                a.entrar();
                b.entrar();
                verificar(a.estaNoGrupo(), "deveria estar no grupo");

                servidor.enviar(Notificacao.NOTIFICACAO, "um");
                verificar(filaA.poll(3, TimeUnit.SECONDS) != null, "A deveria receber 'um'");
                verificar(filaB.poll(3, TimeUnit.SECONDS) != null, "B deveria receber 'um'");

                a.sair();   // leaveGroup
                servidor.enviar(Notificacao.NOTIFICACAO, "dois");
                verificar(filaB.poll(3, TimeUnit.SECONDS) != null, "B deveria receber 'dois'");
                verificar(filaA.poll(700, TimeUnit.MILLISECONDS) == null, "A saiu do grupo e não deveria receber");

                a.entrar();
                servidor.enviar(Notificacao.NOTIFICACAO, "tres");
                Notificacao recebida = filaA.poll(3, TimeUnit.SECONDS);
                verificar(recebida != null && recebida.getMensagem().equals("tres"), "A voltou ao grupo e deveria receber 'tres'");
            }
        }
    }

    private static void testeEnvioConcorrente() throws Exception {
        int porta = livre();
        final int threads = 4, porThread = 5;
        try (ServidorMulticast servidor = new ServidorMulticast(0, "230.0.2.5", porta)) {
            BlockingQueue<Notificacao> fila = new LinkedBlockingQueue<>();
            try (OuvinteMulticast ouvinte = new OuvinteMulticast(new Grupo("230.0.2.5", porta), fila::add)) {
                ouvinte.entrar();

                ExecutorService produtores = Executors.newFixedThreadPool(threads);
                try {
                    List<Future<?>> envios = new ArrayList<>();
                    for (int t = 0; t < threads; t++) {
                        final int n = t;
                        envios.add(produtores.submit(() -> {
                            for (int i = 0; i < porThread; i++) {
                                servidor.enviar(Notificacao.ATUALIZACAO, "t" + n + "-m" + i);
                            }
                        }));
                    }
                    for (Future<?> f : envios) f.get(5, TimeUnit.SECONDS);
                } finally {
                    produtores.shutdownNow();
                }

                Set<String> recebidas = new HashSet<>();
                for (int i = 0; i < threads * porThread; i++) {
                    Notificacao n = fila.poll(3, TimeUnit.SECONDS);
                    verificar(n != null, "faltaram mensagens: chegaram " + recebidas.size() + " de " + threads * porThread);
                    recebidas.add(n.getMensagem());
                }
                verificar(recebidas.size() == threads * porThread, "mensagens repetidas ou perdidas");
            }
        }
    }

    private static void testeMonitor() throws Exception {
        int porta = livre();
        EstoqueServico estoque = new EstoqueServico();
        DadosIniciais.semearEstoque(estoque);   // 2 medicamentos com estoque <= 10; 1 deles vencido

        try (ServidorMulticast servidor = new ServidorMulticast(0, "230.0.2.6", porta)) {
            BlockingQueue<Notificacao> fila = new LinkedBlockingQueue<>();
            MonitorEstoque monitor = new MonitorEstoque(estoque, servidor, 10, 1);
            try (OuvinteMulticast ouvinte = new OuvinteMulticast(new Grupo("230.0.2.6", porta), fila::add)) {
                ouvinte.entrar();
                monitor.iniciar();

                List<String> alertas = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    Notificacao n = fila.poll(4, TimeUnit.SECONDS);
                    verificar(n != null && n.getTipo().equals(Notificacao.ALERTA), "deveria chegar um ALERTA");
                    alertas.add(n.getMensagem());
                }
                verificar(alertas.stream().anyMatch(m -> m.contains("vencido") && m.contains("Pomada")), "alerta de vencido");
                verificar(alertas.stream().anyMatch(m -> m.contains("baixo") && m.contains("Anti-inflamatório")), "alerta de estoque baixo");
                verificar(fila.poll(2500, TimeUnit.MILLISECONDS) == null, "o mesmo alerta não deveria ser repetido");

                // depois de repor acima do limite e baixar de novo, o alerta volta
                Medicamento m = estoque.repor(3, 20);
                verificar(m.getQuantidade() == 25, "reposição");
                verificar(fila.poll(2, TimeUnit.SECONDS) == null, "estoque normal não gera alerta");
                estoque.baixar(3, 20);
                Notificacao novo = fila.poll(4, TimeUnit.SECONDS);
                verificar(novo != null && novo.getMensagem().contains("Anti-inflamatório"), "alerta deveria voltar");
            } finally {
                monitor.parar();
            }
        }
    }

    // votação

    private static void testeUrna() {
        Urna urna = new Urna();
        int rex = urna.adicionar("Rex", "Cachorro").getId();
        int mimi = urna.adicionar("Mimi", "Gato").getId();
        int thor = urna.adicionar("Thor", "Coelho").getId();

        esperarExcecao(IllegalArgumentException.class, () -> urna.adicionar("  ", ""));
        esperarExcecao(IllegalArgumentException.class, () -> urna.adicionar("rex", ""));

        urna.votar("ana", rex);
        urna.votar("carlos", rex);
        urna.votar("beatriz", mimi);
        urna.votar("paula", thor);
        esperarExcecao(IllegalStateException.class, () -> urna.votar("ana", mimi));       // já votou
        esperarExcecao(IllegalArgumentException.class, () -> urna.votar("rui", 999));     // candidato inexistente
        verificar(!urna.jaVotou("rui"), "voto inválido não deve contar");

        verificar(urna.remover(thor) == 1, "remover Thor deveria liberar 1 eleitor");
        urna.votar("paula", mimi);                                                        // paula pode votar de novo
        esperarExcecao(IllegalArgumentException.class, () -> urna.remover(thor));

        Resultado r = urna.encerrar();
        verificar(r.getTotal() == 4, "total deveria ser 4, foi " + r.getTotal());
        verificar(r.getLinhas().get(0).getVotos() == 2 && r.getLinhas().get(1).getVotos() == 2, "contagem");
        verificar(Math.abs(r.getLinhas().get(0).getPercentual() - 50.0) < 1e-9, "percentual");
        verificar(r.getVencedores().equals(Arrays.asList("Rex", "Mimi")), "empate Rex/Mimi: " + r.getVencedores());

        verificar(urna.encerrar() == r, "encerrar duas vezes devolve o mesmo resultado");
        esperarExcecao(IllegalStateException.class, () -> urna.votar("rui", rex));
        esperarExcecao(IllegalStateException.class, () -> urna.adicionar("Novo", ""));
        esperarExcecao(IllegalStateException.class, () -> urna.remover(rex));
    }

    private static void testeUrnaEmpate() {
        Urna vazia = new Urna();
        vazia.adicionar("Rex", "");
        Resultado r = vazia.encerrar();
        verificar(r.getTotal() == 0 && r.getVencedores().isEmpty(), "sem votos não há vencedor");
        verificar(r.getLinhas().get(0).getPercentual() == 0.0, "percentual sem votos deve ser 0");

        Urna urna = new Urna();
        int a = urna.adicionar("A", "").getId();
        urna.adicionar("B", "");
        urna.votar("x", a);
        Resultado unico = urna.encerrar();
        verificar(unico.getVencedores().equals(List.of("A")), "vencedor único");
        verificar(Math.abs(unico.getLinhas().get(0).getPercentual() - 100.0) < 1e-9, "100%");

        Resultado copia = Resultado.fromMapa(Json.lerObjeto(Json.escrever(unico.toMapa())));
        verificar(copia.getTotal() == 1 && copia.getVencedores().equals(List.of("A")), "resultado sobrevive ao JSON");
    }

    private static void testeFluxoTcp() throws Exception {
        try (ServidorVotacao servidor = new ServidorVotacao(0, new Grupo("230.0.2.7", livre()), 60, Contas.padrao())) {
            servidor.getUrna().adicionar("Rex", "Cachorro");
            servidor.getUrna().adicionar("Mimi", "Gato");
            servidor.iniciar();
            int porta = servidor.getPorta();

            try (ClienteVotacao eleitor = new ClienteVotacao("localhost", porta)) {
                verificar(!eleitor.listar().isSucesso(), "sem login não pode listar");
                verificar(!eleitor.votar(1).isSucesso(), "sem login não pode votar");
                verificar(!eleitor.login("carlos", "errada").isSucesso(), "senha errada deveria falhar");

                RespostaVotacao login = eleitor.login("carlos", "1234");
                verificar(login.isSucesso() && "ELEITOR".equals(login.getPapel()), "login do eleitor");
                verificar(login.getCandidatos().size() == 2, "o login deveria devolver a lista de candidatos");
                verificar(login.getGrupo() != null && login.getGrupo().getEndereco().equals("230.0.2.7"), "grupo no login");
                verificar(login.getRestante() > 0, "deveria informar o tempo restante");

                verificar(!eleitor.votar(99).isSucesso(), "candidato inexistente");
                verificar(eleitor.votar(1).isSucesso(), "primeiro voto");
                RespostaVotacao repetido = eleitor.votar(2);
                verificar(!repetido.isSucesso() && repetido.getMensagem().contains("já votou"), "segundo voto");

                verificar(!eleitor.adicionarCandidato("Luna", "Gato").isSucesso(), "eleitor não adiciona candidato");
                verificar(!eleitor.removerCandidato(1).isSucesso(), "eleitor não remove candidato");
                verificar(!eleitor.enviarNota("oi").isSucesso(), "eleitor não envia nota");
                verificar(eleitor.resultado().getResultado() == null, "sem resultado antes do prazo");
            }

            // o voto vale para o usuário, não para a conexão
            try (ClienteVotacao outraConexao = new ClienteVotacao("localhost", porta)) {
                outraConexao.login("carlos", "1234");
                verificar(!outraConexao.votar(2).isSucesso(), "carlos não pode votar de novo em outra conexão");
            }

            try (ClienteVotacao admin = new ClienteVotacao("localhost", porta)) {
                verificar(!admin.login("admin", "errada").isSucesso(), "senha de admin errada");
                RespostaVotacao login = admin.login("admin", "admin123");
                verificar(login.isSucesso() && "ADMIN".equals(login.getPapel()), "login do admin");

                verificar(admin.adicionarCandidato("Luna", "Gato, Persa").isSucesso(), "adicionar");
                verificar(admin.listar().getCandidatos().size() == 3, "lista deveria ter 3");
                verificar(admin.removerCandidato(3).isSucesso(), "remover");
                verificar(!admin.removerCandidato(3).isSucesso(), "remover de novo deveria falhar");
                verificar(admin.listar().getCandidatos().size() == 2, "lista deveria voltar a 2");
                verificar(!admin.votar(1).isSucesso(), "administrador não vota");
            }
        }
    }

    private static void testePrazo() throws Exception {
        try (ServidorVotacao servidor = new ServidorVotacao(0, new Grupo("230.0.2.9", livre()), 3, Contas.padrao())) {
            servidor.getUrna().adicionar("Rex", "Cachorro");
            servidor.getUrna().adicionar("Mimi", "Gato");
            servidor.iniciar();
            int porta = servidor.getPorta();

            try (ClienteVotacao ana = new ClienteVotacao("localhost", porta);
                 ClienteVotacao carlos = new ClienteVotacao("localhost", porta);
                 ClienteVotacao beatriz = new ClienteVotacao("localhost", porta)) {
                ana.login("ana", "1234");
                carlos.login("carlos", "1234");
                beatriz.login("beatriz", "1234");

                verificar(ana.votar(1).isSucesso() && carlos.votar(1).isSucesso(), "votos dentro do prazo");
                RespostaVotacao parcial = ana.resultado();
                verificar(parcial.getResultado() == null && parcial.getRestante() > 0, "ainda não há resultado");

                verificar(servidor.aguardarEncerramento(10), "o prazo deveria acabar");

                RespostaVotacao tarde = beatriz.votar(2);
                verificar(!tarde.isSucesso() && tarde.getMensagem().contains("encerrada"), "voto depois do prazo");

                RespostaVotacao finalResp = beatriz.resultado();
                Resultado r = finalResp.getResultado();
                verificar(finalResp.getRestante() == 0 && r != null, "resultado final deveria estar disponível");
                verificar(r.getTotal() == 2, "total de votos");
                verificar(r.getVencedores().equals(List.of("Rex")), "vencedor");
                verificar(Math.abs(r.getLinhas().get(0).getPercentual() - 100.0) < 1e-9
                        && r.getLinhas().get(1).getPercentual() == 0.0, "percentuais");
            }
        }
    }

    private static void testeConcorrenciaVotacao() throws Exception {
        final int eleitores = 40;
        Contas contas = new Contas();
        for (int i = 0; i < eleitores; i++) contas.adicionarEleitor("e" + i, "s");

        try (ServidorVotacao servidor = new ServidorVotacao(0, new Grupo("230.0.2.10", livre()), 4, contas)) {
            servidor.getUrna().adicionar("A", "");
            servidor.getUrna().adicionar("B", "");
            servidor.getUrna().adicionar("C", "");
            servidor.iniciar();
            int porta = servidor.getPorta();

            ExecutorService pool = Executors.newFixedThreadPool(eleitores);
            try {
                List<Future<Boolean>> tarefas = new ArrayList<>();
                for (int i = 0; i < eleitores; i++) {
                    final int n = i;
                    Callable<Boolean> votar = () -> {
                        try (ClienteVotacao c = new ClienteVotacao("localhost", porta)) {
                            c.login("e" + n, "s");
                            boolean primeiro = c.votar(1 + n % 3).isSucesso();
                            boolean segundo = c.votar(1).isSucesso();
                            return primeiro && !segundo;
                        }
                    };
                    tarefas.add(pool.submit(votar));
                }
                for (Future<Boolean> t : tarefas) verificar(t.get(15, TimeUnit.SECONDS), "voto único por eleitor");
            } finally {
                pool.shutdownNow();
            }

            verificar(servidor.aguardarEncerramento(10), "o prazo deveria acabar");
            Resultado r = servidor.getUrna().getResultadoFinal();
            verificar(r.getTotal() == eleitores, "total deveria ser " + eleitores + ", foi " + r.getTotal());
            verificar(r.getLinhas().get(0).getVotos() == 14 && r.getLinhas().get(1).getVotos() == 13
                    && r.getLinhas().get(2).getVotos() == 13, "contagem por candidato");
            verificar(r.getVencedores().equals(List.of("A")), "vencedor");
        }
    }

    private static void testeNota() throws Exception {
        int porta = livre();
        try (ServidorVotacao servidor = new ServidorVotacao(0, new Grupo("230.0.2.8", porta), 60, Contas.padrao())) {
            servidor.iniciar();
            BlockingQueue<Notificacao> fila = new LinkedBlockingQueue<>();

            try (ClienteVotacao eleitor = new ClienteVotacao("localhost", servidor.getPorta());
                 ClienteVotacao admin = new ClienteVotacao("localhost", servidor.getPorta())) {
                Grupo grupo = eleitor.login("ana", "1234").getGrupo();
                try (OuvinteMulticast ouvinte = new OuvinteMulticast(grupo, fila::add)) {
                    ouvinte.entrar();
                    admin.login("admin", "admin123");

                    verificar(admin.enviarNota("Faltam 5 minutos para o fim da votação").isSucesso(), "envio da nota");
                    Notificacao nota = fila.poll(3, TimeUnit.SECONDS);
                    verificar(nota != null, "o eleitor deveria receber a nota");
                    verificar(nota.getTipo().equals(Notificacao.NOTA_INFORMATIVA)
                            && nota.getMensagem().equals("Faltam 5 minutos para o fim da votação"), "conteúdo da nota");

                    verificar(!admin.enviarNota("   ").isSucesso(), "nota vazia deveria falhar");
                    verificar(!eleitor.enviarNota("tentativa").isSucesso(), "eleitor não envia nota");
                    verificar(fila.poll(700, TimeUnit.MILLISECONDS) == null, "nada mais deveria chegar");
                }
            }
        }
    }

    // utilitários

    private static int livre() throws IOException {
        try (DatagramSocket s = new DatagramSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static Set<String> tipos(BlockingQueue<Notificacao> fila, int quantidade) throws InterruptedException {
        Set<String> tipos = new HashSet<>();
        for (int i = 0; i < quantidade; i++) {
            Notificacao n = fila.poll(3, TimeUnit.SECONDS);
            verificar(n != null, "faltaram mensagens: chegaram " + i + " de " + quantidade);
            tipos.add(n.getTipo());
        }
        return tipos;
    }

    private interface Teste { void rodar() throws Exception; }

    private static void executar(String nome, Teste teste) {
        try {
            teste.rodar();
            passou++;
            System.out.println("  [OK]    " + nome);
        } catch (Throwable e) {
            falhou++;
            System.out.println("  [FALHA] " + nome + " -> " + e);
        }
    }

    private static void executarMulticast(String nome, Teste teste) {
        if (!RedeMulticast.disponivel()) {
            pulou++;
            System.out.println("  [PULOU] " + nome);
            return;
        }
        executar(nome, teste);
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }

    private static void esperarExcecao(Class<? extends Throwable> tipo, Teste codigo) {
        try {
            codigo.rodar();
        } catch (Throwable e) {
            if (tipo.isInstance(e)) return;
            throw new AssertionError("esperava " + tipo.getSimpleName() + " mas veio " + e);
        }
        throw new AssertionError("esperava " + tipo.getSimpleName() + " mas nada foi lançado");
    }
}
