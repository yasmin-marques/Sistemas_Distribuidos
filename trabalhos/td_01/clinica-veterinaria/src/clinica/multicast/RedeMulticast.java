package clinica.multicast;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

// Escolhe a interface de rede usada para enviar e receber multicast.
// Para forçar uma interface: java -Dclinica.interface=eth0 ...

public final class RedeMulticast {

    private static final String[] NOMES_VIRTUAIS = {
        "virtual", "vmware", "vbox", "hyper-v", "vethernet", "docker", "wsl", "veth", "virbr", "br-", "bluetooth"
    };

    private RedeMulticast() { }

    public static NetworkInterface escolherInterface() throws IOException {
        String nome = System.getProperty("clinica.interface");
        if (nome != null) {
            NetworkInterface forcada = NetworkInterface.getByName(nome);
            if (forcada == null || !forcada.isUp() || !forcada.supportsMulticast()) {
                throw new IOException("A interface '" + nome + "' não existe, está desativada ou não suporta multicast.");
            }
            return forcada;
        }

        NetworkInterface fisica = null;
        NetworkInterface virtual = null;
        NetworkInterface loopback = null;
        for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!n.isUp() || !n.supportsMulticast() || !temIpv4(n)) continue;
            if (n.isLoopback()) {
                if (loopback == null) loopback = n;
            } else if (pareceVirtual(n)) {
                if (virtual == null) virtual = n;
            } else if (fisica == null) {
                fisica = n;
            }
        }

        if (fisica != null) return fisica;
        if (virtual != null) return virtual;
        if (loopback != null) return loopback;
        throw new IOException("Nenhuma interface IPv4 com suporte a multicast foi encontrada. "
                + "Use -Dclinica.interface=<nome> para escolher uma.");
    }

    public static boolean disponivel() {
        try {
            escolherInterface();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean temIpv4(NetworkInterface n) {
        for (InetAddress a : Collections.list(n.getInetAddresses())) {
            if (a instanceof Inet4Address) return true;
        }
        return false;
    }

    private static boolean pareceVirtual(NetworkInterface n) {
        if (n.isVirtual()) return true;
        String texto = (n.getName() + " " + n.getDisplayName()).toLowerCase();
        for (String parte : NOMES_VIRTUAIS) {
            if (texto.contains(parte)) return true;
        }
        return false;
    }
}
