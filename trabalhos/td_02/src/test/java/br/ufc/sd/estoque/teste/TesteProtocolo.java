package br.ufc.sd.estoque.teste;

import static br.ufc.sd.estoque.teste.Teste.igual;
import static br.ufc.sd.estoque.teste.Teste.lanca;
import static br.ufc.sd.estoque.teste.Teste.verdadeiro;

import br.ufc.sd.estoque.protocolo.ProtocoloServidor;
import br.ufc.sd.estoque.protocolo.RequisicaoRecebida;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * getRequest/sendReply da porta servidora, testados diretamente (sem rede): a chamada
 * {@code entregar} é feita de outras threads, como faria o RMI.
 */
final class TesteProtocolo {

    private TesteProtocolo() { }

    static void executar(Teste t) {
        t.secao("Protocolo: getRequest / sendReply na porta servidora");

        t.caso("entregar → getRequest → sendReply devolve a resposta a quem chamou", () -> {
            ProtocoloServidor p = new ProtocoloServidor();
            ExecutorService cliente = Executors.newSingleThreadExecutor();
            try {
                Future<byte[]> resposta = cliente.submit(() -> p.entregar(bytes("ping")));
                RequisicaoRecebida req = p.getRequest(2, TimeUnit.SECONDS);
                verdadeiro(req != null, "a requisição deve chegar à fila");
                igual("ping", texto(req.dados()), "dados recebidos");
                igual(InetAddress.getLoopbackAddress(), req.clientHost(), "host do cliente");
                verdadeiro(p.sendReply(bytes("pong"), req.clientHost(), req.clientPort()), "resposta entregue");
                igual("pong", texto(resposta.get(2, TimeUnit.SECONDS)), "resposta recebida pelo cliente");
                igual(0, p.pendentes(), "nenhuma chamada pendente no fim");
            } finally {
                cliente.shutdownNow();
            }
        });

        t.caso("sendReply recusa endereço que não é o da requisição e canal desconhecido", () -> {
            ProtocoloServidor p = new ProtocoloServidor();
            ExecutorService cliente = Executors.newSingleThreadExecutor();
            try {
                Future<byte[]> resposta = cliente.submit(() -> p.entregar(bytes("x")));
                RequisicaoRecebida req = p.getRequest(2, TimeUnit.SECONDS);
                InetAddress outro = InetAddress.getByName("10.9.8.7");
                lanca(IllegalArgumentException.class, () -> p.sendReply(bytes("y"), outro, req.clientPort()));
                verdadeiro(!p.sendReply(bytes("y"), req.clientHost(), req.clientPort() + 1000), "canal desconhecido");
                verdadeiro(p.sendReply(bytes("ok"), req.clientHost(), req.clientPort()), "resposta certa");
                igual("ok", texto(resposta.get(2, TimeUnit.SECONDS)), "cliente recebe só a resposta certa");
            } finally {
                cliente.shutdownNow();
            }
        });

        t.caso("20 chamadas simultâneas: cada cliente recebe a própria resposta (sem troca)", () -> {
            ProtocoloServidor p = new ProtocoloServidor();
            ExecutorService clientes = Executors.newFixedThreadPool(20);
            Thread servidor = new Thread(() -> {
                try {
                    for (int i = 0; i < 20; i++) {
                        RequisicaoRecebida r = p.getRequest();
                        p.sendReply(bytes("eco:" + texto(r.dados())), r.clientHost(), r.clientPort());
                    }
                } catch (InterruptedException ignorado) {
                    // fim do teste
                }
            });
            servidor.start();
            try {
                List<Future<byte[]>> respostas = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    String corpo = "req-" + i;
                    respostas.add(clientes.submit(() -> p.entregar(bytes(corpo))));
                }
                for (int i = 0; i < 20; i++) {
                    igual("eco:req-" + i, texto(respostas.get(i).get(5, TimeUnit.SECONDS)), "resposta " + i);
                }
            } finally {
                servidor.interrupt();
                clientes.shutdownNow();
            }
        });

        t.caso("Requisição vazia e servidor encerrado são recusados", () -> {
            ProtocoloServidor p = new ProtocoloServidor();
            lanca(RemoteException.class, () -> p.entregar(new byte[0]));
            lanca(RemoteException.class, () -> p.entregar(null));
            p.encerrar();
            lanca(RemoteException.class, () -> p.entregar(bytes("depois")));
        });
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String texto(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
