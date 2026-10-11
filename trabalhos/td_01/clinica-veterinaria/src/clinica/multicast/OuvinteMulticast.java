package clinica.multicast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

// Lado cliente do multicast
public class OuvinteMulticast implements AutoCloseable {

    private final MulticastSocket socket;
    private final InetSocketAddress endereco;
    private final NetworkInterface rede;
    private final Consumer<Notificacao> aoReceber;
    private volatile boolean noGrupo = false;

    public OuvinteMulticast(Grupo grupo, Consumer<Notificacao> aoReceber) throws IOException {
        this.rede = RedeMulticast.escolherInterface();
        this.endereco = new InetSocketAddress(InetAddress.getByName(grupo.getEndereco()), grupo.getPorta());
        this.aoReceber = aoReceber;
        this.socket = new MulticastSocket(grupo.getPorta());

        Thread escuta = new Thread(this::escutar, "thread-escuta-multicast");
        escuta.setDaemon(true);
        escuta.start();
    }

    public synchronized void entrar() throws IOException {
        if (noGrupo) return;
        socket.joinGroup(endereco, rede);
        noGrupo = true;
    }

    public synchronized void sair() throws IOException {
        if (!noGrupo) return;
        noGrupo = false;
        socket.leaveGroup(endereco, rede);
    }

    public boolean estaNoGrupo() {
        return noGrupo;
    }

    private void escutar() {
        byte[] buffer = new byte[2048];
        while (!socket.isClosed()) {
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(pacote);
            } catch (IOException e) {
                if (!socket.isClosed()) System.err.println("Erro ao receber do grupo: " + e.getMessage());
                return;
            }

            if (!noGrupo) continue;
            try {
                String json = new String(pacote.getData(), 0, pacote.getLength(), StandardCharsets.UTF_8);
                aoReceber.accept(Notificacao.fromJson(json));
            } catch (IllegalArgumentException e) {
                System.err.println("Mensagem multicast ignorada: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        try {
            sair();
        } catch (IOException ignorado) {
        }
        socket.close();
    }
}
