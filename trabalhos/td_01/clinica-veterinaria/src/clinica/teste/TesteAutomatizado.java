package clinica.teste;

import clinica.modelo.Animal;
import clinica.modelo.Cachorro;
import clinica.modelo.Coelho;
import clinica.modelo.Gato;
import clinica.modelo.Medicamento;
import clinica.modelo.RegistroConsulta;
import clinica.serializacao.ClienteVeterinaria;
import clinica.serializacao.ClienteVeterinaria.ErroRemotoException;
import clinica.serializacao.ServidorVeterinaria;
import clinica.stream.EstoqueInputStream;
import clinica.stream.EstoqueOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StreamCorruptedException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// Testes automatizados (sem dependências externas) das Questões 1, 2 e 3
public class TesteAutomatizado {

    private static int passou = 0;
    private static int falhou = 0;

    private static Medicamento[] dados() {
        return new Medicamento[] {
            new Medicamento(1, "Amoxicilina 250mg", "VetPharma", 50, 35.90, LocalDate.of(2028, 6, 30)),
            new Medicamento(2, "Vermífugo Oral", "PetSaude", 30, 22.50, LocalDate.of(2028, 1, 15)),
            new Medicamento(3, "Dipirona; gotas 500mg \\ 20ml", "Vet;Lab", 12, 18.00, LocalDate.of(2027, 12, 1)),
            new Medicamento(4, "Linha1\nLinha2", "Sem validade", 1, 0.0, null),
        };
    }

    public static void main(String[] args) throws Exception {
        System.out.println("QUESTÃO 1 e 2: Streams");
        executar("Q1b/Q2b  System.out  -> System.in (capturados)", TesteAutomatizado::testeSaidaEEntradaPadrao);
        executar("Q1c/Q2c  FileOutputStream -> FileInputStream", TesteAutomatizado::testeArquivo);
        executar("Q1d/Q2d  Socket TCP (servidor lê do cliente)", TesteAutomatizado::testeTcp);
        executar("Subconjunto: enviar apenas N < array.length", TesteAutomatizado::testeSubconjunto);
        executar("Array vazio (N = 0)", TesteAutomatizado::testeVazio);
        executar("Construtor valida argumentos", TesteAutomatizado::testeValidacaoConstrutor);
        executar("Leitor detecta stream truncado/corrompido", TesteAutomatizado::testeCorrompido);
        executar("Duas mensagens seguidas no mesmo stream", TesteAutomatizado::testeDuasMensagens);

        System.out.println("\nQUESTÃO 3: Serialização cliente-servidor");
        try (ServidorVeterinaria servidor = new ServidorVeterinaria(0)) {
            Thread t = new Thread(servidor::iniciar, "servidor");
            t.setDaemon(true);
            t.start();
            int porta = servidor.getPorta();

            executar("Registrar/listar animais preserva subclasses", () -> testeAnimais(porta));
            executar("Consulta com baixa de estoque + histórico", () -> testeConsulta(porta));
            executar("Erros do servidor chegam ao cliente", () -> testeErros(porta));
            executar("Cadastro de medicamento remoto", () -> testeCadastroMedicamento(porta));
            executar("Vários clientes simultâneos (ids únicos)", () -> testeConcorrencia(porta));
            executar("Servidor rejeita classe não permitida", () -> testeFiltro(porta));
        }

        System.out.println("\nResultado: " + passou + " passou, " + falhou + " falhou.");
        System.exit(falhou == 0 ? 0 : 1);
    }

    // Q1/Q2

    private static void testeSaidaEEntradaPadrao() throws Exception {
        PrintStream outOriginal = System.out;
        InputStream inOriginal = System.in;
        ByteArrayOutputStream capturado = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(capturado, true, StandardCharsets.UTF_8));
            Medicamento[] original = dados();
            new EstoqueOutputStream(System.out, original, original.length).enviar();
            System.setOut(outOriginal);

            System.setIn(new ByteArrayInputStream(capturado.toByteArray()));
            Medicamento[] lido = new EstoqueInputStream(System.in).lerMedicamentos();
            verificar(Arrays.equals(original, lido), "array lido difere do enviado");
        } finally {
            System.setOut(outOriginal);
            System.setIn(inOriginal);
        }
    }

    private static void testeArquivo() throws Exception {
        Path arquivo = Files.createTempFile("estoque", ".txt");
        try {
            Medicamento[] original = dados();
            try (EstoqueOutputStream out = new EstoqueOutputStream(
                    new FileOutputStream(arquivo.toFile()), original, original.length)) {
                out.enviar();
            }
            try (EstoqueInputStream in = new EstoqueInputStream(new FileInputStream(arquivo.toFile()))) {
                verificar(Arrays.equals(original, in.lerMedicamentos()), "array lido do arquivo difere");
            }
        } finally {
            Files.deleteIfExists(arquivo);
        }
    }

    private static void testeTcp() throws Exception {
        Medicamento[] original = dados();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (ServerSocket servidor = new ServerSocket(0)) {
            // servidor remoto: lê dos bytes do cliente com EstoqueInputStream
            Future<Medicamento[]> recebido = pool.submit(() -> {
                try (Socket cliente = servidor.accept();
                     EstoqueInputStream in = new EstoqueInputStream(cliente.getInputStream())) {
                    return in.lerMedicamentos();
                }
            });
            // cliente: envia com EstoqueOutputStream sobre o OutputStream do socket
            try (Socket socket = new Socket("localhost", servidor.getLocalPort());
                 EstoqueOutputStream out = new EstoqueOutputStream(socket.getOutputStream(), original, original.length)) {
                out.enviar();
                verificar(Arrays.equals(original, recebido.get(5, TimeUnit.SECONDS)),
                        "dados recebidos via TCP diferem dos enviados");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void testeSubconjunto() throws Exception {
        Medicamento[] original = dados();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new EstoqueOutputStream(bytes, original, 2).enviar();
        Medicamento[] lido = new EstoqueInputStream(new ByteArrayInputStream(bytes.toByteArray())).lerMedicamentos();
        verificar(lido.length == 2, "deveria ter lido 2 objetos, leu " + lido.length);
        verificar(original[0].equals(lido[0]) && original[1].equals(lido[1]), "conteúdo difere");
    }

    private static void testeVazio() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new EstoqueOutputStream(bytes, new Medicamento[0], 0).enviar();
        Medicamento[] lido = new EstoqueInputStream(new ByteArrayInputStream(bytes.toByteArray())).lerMedicamentos();
        verificar(lido.length == 0, "deveria ser vazio");
    }

    private static void testeValidacaoConstrutor() {
        Medicamento[] d = dados();
        esperarExcecao(IllegalArgumentException.class, () -> new EstoqueOutputStream(null, d, 1));
        esperarExcecao(IllegalArgumentException.class, () -> new EstoqueOutputStream(System.out, null, 1));
        esperarExcecao(IllegalArgumentException.class, () -> new EstoqueOutputStream(System.out, d, -1));
        esperarExcecao(IllegalArgumentException.class, () -> new EstoqueOutputStream(System.out, d, d.length + 1));
        esperarExcecao(IllegalArgumentException.class,
                () -> new EstoqueOutputStream(System.out, new Medicamento[] { null }, 1));
        esperarExcecao(IllegalArgumentException.class, () -> new EstoqueInputStream(null));
    }

    private static void testeCorrompido() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new EstoqueOutputStream(bytes, dados(), 4).enviar();
        byte[] completo = bytes.toByteArray();
        byte[] truncado = Arrays.copyOf(completo, completo.length - 10);

        esperarExcecao(EOFException.class, () -> new EstoqueInputStream(new ByteArrayInputStream(truncado)).lerMedicamentos());
        esperarExcecao(EOFException.class, () -> new EstoqueInputStream(new ByteArrayInputStream(new byte[0])).lerMedicamentos());
        esperarExcecao(StreamCorruptedException.class, () -> new EstoqueInputStream(
                new ByteArrayInputStream("LIXO\n1\n".getBytes(StandardCharsets.UTF_8))).lerMedicamentos());
        esperarExcecao(StreamCorruptedException.class, () -> new EstoqueInputStream(
                new ByteArrayInputStream("ESTOQUE-V1\nabc\n".getBytes(StandardCharsets.UTF_8))).lerMedicamentos());
        esperarExcecao(StreamCorruptedException.class, () -> new EstoqueInputStream(
                new ByteArrayInputStream("ESTOQUE-V1\n1\n1;a;b;x;1.0;\n".getBytes(StandardCharsets.UTF_8))).lerMedicamentos());
    }

    private static void testeDuasMensagens() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Medicamento[] d = dados();
        new EstoqueOutputStream(bytes, d, 1).enviar();
        new EstoqueOutputStream(bytes, d, 3).enviar();
        EstoqueInputStream in = new EstoqueInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        verificar(in.lerMedicamentos().length == 1, "primeira mensagem deveria ter 1 objeto");
        verificar(in.lerMedicamentos().length == 3, "segunda mensagem deveria ter 3 objetos");
    }

    // Q3

    private static void testeAnimais(int porta) throws Exception {
        try (ClienteVeterinaria c = new ClienteVeterinaria("localhost", porta)) {
            Animal rex = c.registrarAnimal(new Cachorro(0, "Rex", 5, 28.5, "Carlos", "Labrador", Cachorro.Porte.GRANDE));
            Animal mimi = c.registrarAnimal(new Gato(0, "Mimi", 3, 4.2, "Ana", "Siamês", true));
            Animal thor = c.registrarAnimal(new Coelho(0, "Thor", 2, 2.1, "Beatriz", "Mini Lop", "Curta"));

            verificar(rex.getId() > 0 && mimi.getId() > rex.getId() && thor.getId() > mimi.getId(),
                    "ids deveriam ser gerados em sequência pelo servidor");
            verificar(rex instanceof Cachorro && ((Cachorro) rex).getPorte() == Cachorro.Porte.GRANDE, "Cachorro");
            verificar(mimi instanceof Gato && ((Gato) mimi).isCastrado(), "Gato");
            verificar(thor instanceof Coelho && "Curta".equals(((Coelho) thor).getPelagem()), "Coelho");

            Animal buscado = c.buscarAnimal(mimi.getId());
            verificar(buscado instanceof Gato && "Mimi".equals(buscado.getNome()), "buscarAnimal");
            verificar(c.listarAnimais().size() >= 3, "listarAnimais deveria ter ao menos 3");
        }
    }

    private static void testeConsulta(int porta) throws Exception {
        try (ClienteVeterinaria c = new ClienteVeterinaria("localhost", porta)) {
            Animal a = c.registrarAnimal(new Gato(0, "Luna", 4, 3.8, "Paula", "Persa", false));
            int antes = quantidade(c, 2);   // Vermífugo Oral (id 2)

            RegistroConsulta r = c.realizarConsulta(a.getId(), "Coceira", "Verminose", 2, 3);
            verificar("Vermífugo Oral".equals(r.getMedicamento()) && r.getQuantidade() == 3, "dados da consulta");
            verificar(r.getAnimal() instanceof Gato, "animal dentro da consulta preserva a subclasse");
            verificar(quantidade(c, 2) == antes - 3, "estoque deveria ter diminuído em 3");

            c.realizarConsulta(a.getId(), "Rotina", "Saudável", 0, 0);
            verificar(c.historico(a.getId()).size() == 2, "histórico deveria ter 2 consultas");
        }
    }

    private static void testeErros(int porta) throws Exception {
        try (ClienteVeterinaria c = new ClienteVeterinaria("localhost", porta)) {
            Animal a = c.registrarAnimal(new Coelho(0, "Bolinha", 1, 1.5, "Rui", "Anão", "Longa"));
            esperarExcecao(ErroRemotoException.class, () -> c.buscarAnimal(99999));
            esperarExcecao(ErroRemotoException.class, () -> c.realizarConsulta(a.getId(), "x", "y", 3, 1000));   // estoque insuficiente
            esperarExcecao(ErroRemotoException.class, () -> c.realizarConsulta(a.getId(), "x", "y", 4, 1));      // vencido
            esperarExcecao(ErroRemotoException.class, () -> c.realizarConsulta(a.getId(), "x", "y", 777, 1));    // inexistente
            esperarExcecao(ErroRemotoException.class, () -> c.registrarAnimal(new Gato(0, " ", 1, 1, "t", "r", false)));
            // a conexão continua utilizável depois dos erros
            verificar(c.buscarAnimal(a.getId()).getNome().equals("Bolinha"), "conexão deveria seguir ativa");
        }
    }

    private static void testeCadastroMedicamento(int porta) throws Exception {
        try (ClienteVeterinaria c = new ClienteVeterinaria("localhost", porta)) {
            Medicamento novo = c.cadastrarMedicamento(
                    new Medicamento(0, "Colírio", "OftalVet", 10, 31.0, LocalDate.of(2029, 5, 1)));
            verificar(novo.getId() > 0, "id deveria ser gerado");
            verificar(c.listarMedicamentos().contains(novo), "medicamento deveria estar na lista");
        }
    }

    private static void testeConcorrencia(int porta) throws Exception {
        final int clientes = 8, porCliente = 25;
        ExecutorService pool = Executors.newFixedThreadPool(clientes);
        try {
            List<Future<List<Integer>>> futuros = new ArrayList<>();
            for (int i = 0; i < clientes; i++) {
                final int n = i;
                Callable<List<Integer>> tarefa = () -> {
                    List<Integer> ids = new ArrayList<>();
                    try (ClienteVeterinaria c = new ClienteVeterinaria("localhost", porta)) {
                        for (int j = 0; j < porCliente; j++) {
                            ids.add(c.registrarAnimal(
                                    new Gato(0, "Gato" + n + "-" + j, 1, 3.0, "Tutor", "SRD", false)).getId());
                        }
                    }
                    return ids;
                };
                futuros.add(pool.submit(tarefa));
            }
            Set<Integer> todos = new HashSet<>();
            for (Future<List<Integer>> f : futuros) todos.addAll(f.get(20, TimeUnit.SECONDS));
            verificar(todos.size() == clientes * porCliente,
                    "ids duplicados: " + todos.size() + " únicos de " + (clientes * porCliente));
        } finally {
            pool.shutdownNow();
        }
    }

    // Envia um objeto de classe não permitida (java.util.Date) e espera erro, não execução
    private static void testeFiltro(int porta) throws Exception {
        try (Socket s = new Socket("localhost", porta);
             java.io.DataOutputStream out = new java.io.DataOutputStream(s.getOutputStream());
             java.io.DataInputStream in = new java.io.DataInputStream(s.getInputStream())) {
            byte[] malicioso = clinica.serializacao.Empacotador.empacotar(new java.util.Date());
            clinica.serializacao.Empacotador.enviar(out, malicioso);
            byte[] bytes = clinica.serializacao.Empacotador.receber(in);
            clinica.serializacao.Resposta r =
                    clinica.serializacao.Empacotador.desempacotar(bytes, clinica.serializacao.Resposta.class);
            verificar(!r.isSucesso(), "servidor deveria rejeitar a mensagem");
        }
    }

    private static int quantidade(ClienteVeterinaria c, int idMedicamento) throws IOException {
        for (Medicamento m : c.listarMedicamentos()) {
            if (m.getId() == idMedicamento) return m.getQuantidade();
        }
        throw new AssertionError("medicamento " + idMedicamento + " não encontrado");
    }

    // utilitários
    
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
