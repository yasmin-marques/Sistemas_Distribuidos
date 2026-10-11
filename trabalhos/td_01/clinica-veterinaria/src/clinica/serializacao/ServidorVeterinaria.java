package clinica.serializacao;

import clinica.modelo.Animal;
import clinica.modelo.Medicamento;
import clinica.servico.Consulta;
import clinica.servico.ConsultaServico;
import clinica.servico.DadosIniciais;
import clinica.servico.EstoqueServico;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// QUESTÃO 3 - Servidor TCP da clínica:
// Desempacota o pedido, executa no serviço e empacota a resposta (uma thread por cliente)
public class ServidorVeterinaria implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final EstoqueServico estoque = new EstoqueServico();
    private final Consulta consulta = new ConsultaServico(estoque);
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private volatile boolean ativo;

    /** @param porta porta TCP (0 = qualquer porta livre; veja {@link #getPorta()}) */
    public ServidorVeterinaria(int porta) throws IOException {
        this.serverSocket = new ServerSocket(porta);
        DadosIniciais.semearEstoque(estoque);
    }

    public int getPorta() {
        return serverSocket.getLocalPort();
    }

    public void iniciar() {
        ativo = true;
        System.out.println("[Servidor] Clínica Veterinária ouvindo na porta " + getPorta());
        while (ativo) {
            try {
                Socket cliente = serverSocket.accept();
                pool.execute(() -> atender(cliente));
            } catch (SocketException e) {
                if (ativo) System.err.println("[Servidor] Erro no accept: " + e);
            } catch (IOException e) {
                System.err.println("[Servidor] Erro no accept: " + e);
            }
        }
    }

    private void atender(Socket socket) {
        String origem = String.valueOf(socket.getRemoteSocketAddress());
        System.out.println("[Servidor] Cliente conectado: " + origem);
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            while (true) {
                byte[] bytesRequisicao;
                try {
                    bytesRequisicao = Empacotador.receber(in);
                } catch (EOFException fim) {
                    break;                                  // cliente encerrou a conexão
                }
                Resposta resposta;
                try {
                    Requisicao requisicao = Empacotador.desempacotar(bytesRequisicao, Requisicao.class);
                    System.out.println("[Servidor] " + origem + " -> " + requisicao);
                    resposta = processar(requisicao);
                } catch (IOException e) {
                    resposta = Resposta.erro("Mensagem inválida: " + e.getMessage());
                }
                Empacotador.enviar(out, Empacotador.empacotar(resposta));
            }
        } catch (IOException e) {
            System.err.println("[Servidor] Conexão com " + origem + " encerrada com erro: " + e);
        }
        System.out.println("[Servidor] Cliente desconectado: " + origem);
    }

    private Resposta processar(Requisicao r) {
        try {
            Serializable[] a = r.getArgumentos();
            switch (r.getOperacao()) {
                case REGISTRAR_ANIMAL:
                    return Resposta.ok(consulta.registrarAnimal((Animal) a[0]));
                case BUSCAR_ANIMAL:
                    return Resposta.ok(consulta.buscarAnimal((Integer) a[0]));
                case LISTAR_ANIMAIS:
                    return Resposta.ok(new ArrayList<>(consulta.listarAnimais()));
                case REALIZAR_CONSULTA:
                    return Resposta.ok(consulta.realizarConsulta((Integer) a[0], (String) a[1],
                            (String) a[2], (Integer) a[3], (Integer) a[4]));
                case HISTORICO:
                    return Resposta.ok(new ArrayList<>(consulta.historico((Integer) a[0])));
                case CADASTRAR_MEDICAMENTO:
                    return Resposta.ok(estoque.cadastrar((Medicamento) a[0]));
                case LISTAR_MEDICAMENTOS:
                    return Resposta.ok(new ArrayList<>(estoque.listar()));
                default:
                    return Resposta.erro("Operação desconhecida: " + r.getOperacao());
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Resposta.erro(e.getMessage());
        } catch (ClassCastException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            return Resposta.erro("Argumentos inválidos para " + r.getOperacao());
        }
    }

    @Override
    public void close() throws IOException {
        ativo = false;
        serverSocket.close();
        pool.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 6000;
        try (ServidorVeterinaria servidor = new ServidorVeterinaria(porta)) {
            servidor.iniciar();
        }
    }
}
