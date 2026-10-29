package br.ufc.sd.estoque.protocolo;

import java.nio.charset.StandardCharsets;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Lado cliente do protocolo requisição-resposta: {@link #doOperation}.
 *
 * <p>Para cada chamada: monta a mensagem de requisição (com um {@code requestId} novo),
 * empacota em JSON, entrega à porta servidora do objeto via Java RMI, espera a mensagem de
 * resposta, desempacota, confere se é a resposta daquela requisição e devolve o resultado.
 *
 * <p>A porta servidora de cada referência é localizada no registro RMI
 * ({@code host:porta/servico}) e guardada em cache. Thread-safe: a thread de interação e a
 * thread que escuta notificações usam o mesmo protocolo ao mesmo tempo.
 */
public class ProtocoloCliente {

    private final AtomicInteger proximoId = new AtomicInteger();
    private final ConcurrentMap<String, PortaServidora> portas = new ConcurrentHashMap<>();
    private volatile Consumer<String> observador;

    /** Recebe o texto de cada mensagem enviada e recebida (útil para demonstrar o protocolo). */
    public void setObservador(Consumer<String> observador) {
        this.observador = observador;
    }

    /**
     * Envia uma mensagem de requisição ao objeto remoto e devolve a resposta.
     *
     * <p>Assinatura adaptada: {@code methodId} é o nome do método (String), como o enunciado permite.
     *
     * @param o         referência do objeto remoto
     * @param methodId  nome do método a invocar
     * @param arguments argumentos empacotados (JSON UTF-8)
     * @return o conteúdo {@code arguments} da mensagem de resposta (resultado ou erro, ver {@link CargaResposta})
     * @throws FalhaComunicacaoException se a requisição não puder ser entregue ou a resposta for inválida
     */
    public byte[] doOperation(RemoteObjectRef o, String methodId, byte[] arguments) {
        int requestId = proximoId.incrementAndGet();
        Mensagem requisicao = new Mensagem(TipoMensagem.REQUEST, requestId, o, methodId, arguments);
        byte[] empacotada = CodecMensagem.empacotar(requisicao);
        observar("--> ", empacotada);

        byte[] bytesResposta = transmitir(o, empacotada);
        observar("<-- ", bytesResposta);

        Mensagem resposta;
        try {
            resposta = CodecMensagem.desempacotar(bytesResposta);
        } catch (IllegalArgumentException e) {
            throw new FalhaComunicacaoException("Resposta malformada: " + e.getMessage(), e);
        }
        if (resposta.getMessageType() != TipoMensagem.REPLY || resposta.getRequestId() != requestId) {
            throw new FalhaComunicacaoException("A resposta (" + TipoMensagem.nome(resposta.getMessageType())
                    + " #" + resposta.getRequestId() + ") não corresponde à requisição #" + requestId + ".");
        }
        return resposta.getArguments();
    }

    private byte[] transmitir(RemoteObjectRef o, byte[] mensagem) {
        String endereco = o.endereco();
        for (int tentativa = 1; ; tentativa++) {
            PortaServidora porta = localizar(o);
            try {
                return porta.entregar(mensagem);
            } catch (ConnectException | NoSuchObjectException e) {
                // A requisição não chegou a ser processada (conexão recusada ou stub antigo,
                // por exemplo após reiniciar o servidor): é seguro localizar de novo e repetir uma vez.
                portas.remove(endereco, porta);
                if (tentativa >= 2) {
                    throw new FalhaComunicacaoException("Servidor indisponível em " + endereco + ".", e);
                }
            } catch (RemoteException e) {
                portas.remove(endereco, porta);
                throw new FalhaComunicacaoException("Falha na chamada RMI a " + endereco + ": "
                        + e.getMessage(), e);
            }
        }
    }

    private PortaServidora localizar(RemoteObjectRef o) {
        PortaServidora porta = portas.get(o.endereco());
        if (porta != null) return porta;
        try {
            Registry registro = LocateRegistry.getRegistry(o.host(), o.porta());
            porta = (PortaServidora) registro.lookup(o.servico());
        } catch (NotBoundException e) {
            throw new FalhaComunicacaoException("O serviço '" + o.servico() + "' não está registrado em "
                    + o.host() + ":" + o.porta() + ".", e);
        } catch (RemoteException e) {
            throw new FalhaComunicacaoException("Registro RMI inacessível em " + o.host() + ":" + o.porta()
                    + " (" + e.getMessage() + ").", e);
        } catch (ClassCastException e) {
            throw new FalhaComunicacaoException("O objeto '" + o.servico() + "' não é uma PortaServidora.", e);
        }
        PortaServidora existente = portas.putIfAbsent(o.endereco(), porta);
        return existente != null ? existente : porta;
    }

    private void observar(String prefixo, byte[] dados) {
        Consumer<String> obs = observador;
        if (obs != null) obs.accept(prefixo + new String(dados, StandardCharsets.UTF_8));
    }
}
