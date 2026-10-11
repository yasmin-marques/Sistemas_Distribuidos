package clinica.serializacao;

import clinica.modelo.Animal;
import clinica.modelo.Medicamento;
import clinica.modelo.RegistroConsulta;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.util.List;

// QUESTÃO 3 - Stub do cliente: empacota a requisição, envia, recebe e desempacota a resposta
public class ClienteVeterinaria implements AutoCloseable {

    // Erro de negócio devolvido pelo servidor
    public static class ErroRemotoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public ErroRemotoException(String mensagem) { super(mensagem); }
    }

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    public ClienteVeterinaria(String host, int porta) throws IOException {
        this.socket = new Socket(host, porta);
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public Animal registrarAnimal(Animal animal) throws IOException {
        return (Animal) chamar(Operacao.REGISTRAR_ANIMAL, animal);
    }

    public Animal buscarAnimal(int id) throws IOException {
        return (Animal) chamar(Operacao.BUSCAR_ANIMAL, id);
    }

    @SuppressWarnings("unchecked")
    public List<Animal> listarAnimais() throws IOException {
        return (List<Animal>) chamar(Operacao.LISTAR_ANIMAIS);
    }

    public RegistroConsulta realizarConsulta(int idAnimal, String sintomas, String diagnostico,
                                             int idMedicamento, int quantidade) throws IOException {
        return (RegistroConsulta) chamar(Operacao.REALIZAR_CONSULTA,
                idAnimal, sintomas, diagnostico, idMedicamento, quantidade);
    }

    @SuppressWarnings("unchecked")
    public List<RegistroConsulta> historico(int idAnimal) throws IOException {
        return (List<RegistroConsulta>) chamar(Operacao.HISTORICO, idAnimal);
    }

    public Medicamento cadastrarMedicamento(Medicamento m) throws IOException {
        return (Medicamento) chamar(Operacao.CADASTRAR_MEDICAMENTO, m);
    }

    @SuppressWarnings("unchecked")
    public List<Medicamento> listarMedicamentos() throws IOException {
        return (List<Medicamento>) chamar(Operacao.LISTAR_MEDICAMENTOS);
    }

    private synchronized Serializable chamar(Operacao operacao, Serializable... argumentos) throws IOException {
        byte[] bytesRequisicao = Empacotador.empacotar(new Requisicao(operacao, argumentos));
        Empacotador.enviar(out, bytesRequisicao);

        byte[] bytesResposta = Empacotador.receber(in);
        Resposta resposta = Empacotador.desempacotar(bytesResposta, Resposta.class);

        if (!resposta.isSucesso()) {
            throw new ErroRemotoException(resposta.getMensagem());
        }
        return resposta.getResultado();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
