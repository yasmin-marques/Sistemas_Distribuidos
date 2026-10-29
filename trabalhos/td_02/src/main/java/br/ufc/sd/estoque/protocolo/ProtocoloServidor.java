package br.ufc.sd.estoque.protocolo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lado servidor do protocolo requisição-resposta: {@link #getRequest()} e {@link #sendReply}.
 *
 * <p>Esta classe é a "porta servidora". Ela é exportada pelo Java RMI (implementa
 * {@link PortaServidora}); quando um cliente chama {@link #entregar(byte[])}, a thread do
 * RMI coloca a requisição numa fila e espera a resposta. O despachante do servidor segue o
 * laço da figura do enunciado:
 * <pre>
 *   req = getRequest();             // obtém a próxima requisição da porta servidora
 *   ... seleciona o objeto, executa o método ...
 *   sendReply(resposta, req.clientHost(), req.clientPort());
 * </pre>
 *
 * <p>Adaptação da assinatura de {@code sendReply}: no livro, {@code clientPort} é a porta UDP
 * do cliente. Com RMI, quem mantém a conexão com o cliente é o próprio RMI e a porta TCP não
 * é exposta à aplicação. Aqui {@code clientPort} identifica o <em>canal de retorno</em>: a
 * chamada RMI daquele cliente que está bloqueada esperando a resposta. {@code clientHost} é
 * o IP real do cliente (obtido com {@code RemoteServer.getClientHost()}) e é conferido no
 * envio, para que uma resposta nunca seja entregue ao cliente errado.
 */
public class ProtocoloServidor implements PortaServidora {

    /** Tempo máximo que uma chamada RMI espera o despachante produzir a resposta. */
    public static final long TEMPO_MAXIMO_RESPOSTA_MS = 60_000;

    private static final int CAPACIDADE_FILA = 1_000;

    private record Pendente(InetAddress host, CompletableFuture<byte[]> resposta) { }

    private final BlockingQueue<RequisicaoRecebida> fila = new LinkedBlockingQueue<>(CAPACIDADE_FILA);
    private final ConcurrentMap<Integer, Pendente> pendentes = new ConcurrentHashMap<>();
    private final AtomicInteger proximoCanal = new AtomicInteger();
    private volatile boolean ativo = true;
    private volatile boolean exportado = false;

    /** Exporta este objeto pelo RMI na porta indicada (0 = porta anônima escolhida pelo RMI). */
    public synchronized PortaServidora exportar(int porta) throws RemoteException {
        PortaServidora stub = (PortaServidora) UnicastRemoteObject.exportObject(this, porta);
        exportado = true;
        return stub;
    }

    // ---------------------------------------------------------------- chamado pelo RMI

    @Override
    public byte[] entregar(byte[] requisicao) throws RemoteException {
        if (!ativo) throw new RemoteException("O servidor está sendo encerrado.");
        if (requisicao == null || requisicao.length == 0) {
            throw new RemoteException("Requisição vazia.");
        }
        if (requisicao.length > CodecMensagem.TAMANHO_MAXIMO) {
            throw new RemoteException("Requisição excede " + CodecMensagem.TAMANHO_MAXIMO + " bytes.");
        }
        InetAddress host = hostDoCliente();
        int canal = proximoCanal.updateAndGet(c -> c == Integer.MAX_VALUE ? 1 : c + 1);
        Pendente pendente = new Pendente(host, new CompletableFuture<>());
        pendentes.put(canal, pendente);
        try {
            if (!fila.offer(new RequisicaoRecebida(requisicao.clone(), host, canal), 5, TimeUnit.SECONDS)) {
                throw new RemoteException("Servidor sobrecarregado: fila de requisições cheia.");
            }
            return pendente.resposta().get(TEMPO_MAXIMO_RESPOSTA_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Chamada interrompida no servidor.");
        } catch (TimeoutException e) {
            throw new RemoteException("O servidor não respondeu em " + TEMPO_MAXIMO_RESPOSTA_MS + " ms.");
        } catch (ExecutionException e) {
            throw new RemoteException("Falha ao produzir a resposta: " + e.getCause().getMessage());
        } finally {
            pendentes.remove(canal);
        }
    }

    private static InetAddress hostDoCliente() {
        try {
            return InetAddress.getByName(RemoteServer.getClientHost());
        } catch (ServerNotActiveException | UnknownHostException e) {
            // chamada local (fora de uma thread do RMI): trata como o próprio host
            return InetAddress.getLoopbackAddress();
        }
    }

    // ---------------------------------------------------------------- protocolo (livro, seção 5.2)

    /**
     * Obtém uma requisição de um cliente através da porta servidora. Bloqueia até chegar uma.
     *
     * <p>Assinatura adaptada: devolve, além dos bytes, o endereço de retorno do cliente — no
     * livro essa informação vem do datagrama recebido.
     */
    public RequisicaoRecebida getRequest() throws InterruptedException {
        return fila.take();
    }

    /** Como {@link #getRequest()}, mas desiste após o tempo indicado (devolve {@code null}). */
    public RequisicaoRecebida getRequest(long espera, TimeUnit unidade) throws InterruptedException {
        return fila.poll(espera, unidade);
    }

    /**
     * Envia a mensagem de resposta ao cliente, endereçando-a ao seu IP e canal de retorno.
     *
     * @return {@code false} se o cliente já não aguarda essa resposta (desistiu por tempo esgotado)
     * @throws IllegalArgumentException se o endereço não corresponder ao da requisição
     */
    public boolean sendReply(byte[] reply, InetAddress clientHost, int clientPort) {
        if (reply == null) throw new IllegalArgumentException("Resposta nula.");
        Pendente pendente = pendentes.get(clientPort);
        if (pendente == null) return false;
        if (!pendente.host().equals(clientHost)) {
            throw new IllegalArgumentException("O canal " + clientPort + " pertence a " + pendente.host()
                    + ", não a " + clientHost + ".");
        }
        return pendente.resposta().complete(reply.clone());
    }

    /** Requisições recebidas que ainda aguardam resposta. */
    public int pendentes() {
        return pendentes.size();
    }

    /** Recusa novas requisições, libera as chamadas que aguardam e retira o objeto do RMI. */
    public synchronized void encerrar() {
        ativo = false;
        pendentes.values().forEach(p -> p.resposta().completeExceptionally(
                new IllegalStateException("servidor encerrado")));
        if (exportado) {
            try {
                UnicastRemoteObject.unexportObject(this, true);
            } catch (NoSuchObjectException ignorado) {
                // já não estava exportado
            }
            exportado = false;
        }
    }
}
