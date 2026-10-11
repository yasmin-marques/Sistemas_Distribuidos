package clinica.stream.teste;

import clinica.modelo.Medicamento;
import clinica.stream.EstoqueOutputStream;
import java.net.Socket;

// Questão 1d — destino: servidor remoto (TCP)
// Este programa é o CLIENTE: conecta ao servidor e envia os dados pelo OutputStream do socket.
// Uso: java clinica.stream.teste.Q1dEnviarParaServidorTcp [host] [porta] [quantidade]
// (o servidor pode ser Q2dReceberDeClienteTcp ou simplesmente "nc -l 5000")

public class Q1dEnviarParaServidorTcp {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        Medicamento[] dados = DadosExemplo.medicamentos();
        int n = DadosExemplo.quantidade(args, 2, dados.length);

        try (Socket socket = new Socket(host, porta);
             EstoqueOutputStream saida = new EstoqueOutputStream(socket.getOutputStream(), dados, n)) {
            saida.enviar();
            System.out.println(n + " medicamento(s) enviado(s) para " + host + ":" + porta);
        }
    }
}
