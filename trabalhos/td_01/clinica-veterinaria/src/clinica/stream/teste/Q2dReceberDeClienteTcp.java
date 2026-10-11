package clinica.stream.teste;

import clinica.modelo.Medicamento;
import clinica.stream.EstoqueInputStream;
import java.net.ServerSocket;
import java.net.Socket;

// Questão 2d — origem: cliente remoto (TCP)
// Este programa é o SERVIDOR: aceita conexões e lê os dados pelo InputStream do socket do cliente
// Uso: java clinica.stream.teste.Q2dReceberDeClienteTcp [porta]


public class Q2dReceberDeClienteTcp {
    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("Aguardando clientes na porta " + porta + "...");
            while (true) {
                try (Socket cliente = servidor.accept();
                     EstoqueInputStream entrada = new EstoqueInputStream(cliente.getInputStream())) {
                    System.out.println("Cliente conectado: " + cliente.getRemoteSocketAddress());
                    Medicamento[] lidos = entrada.lerMedicamentos();
                    ImprimirMedicamentos.imprimir(lidos);
                } catch (java.io.IOException e) {
                    System.err.println("Erro ao receber dados: " + e);
                }
            }
        }
    }
}
