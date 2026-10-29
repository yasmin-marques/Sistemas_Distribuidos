package br.ufc.sd.estoque.protocolo;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface remota Java RMI que transporta as mensagens do protocolo requisição-resposta.
 *
 * <p>É o único objeto exportado pelo RMI no servidor e o único registrado no registro RMI.
 * O RMI cuida da conexão (o trabalho não cria sockets); a aplicação só troca arrays de bytes
 * já empacotados em JSON — a chamada RMI recebe a mensagem de requisição e devolve a
 * mensagem de resposta.
 */
public interface PortaServidora extends Remote {

    /** Nome padrão sob o qual a porta servidora é registrada no registro RMI. */
    String NOME_REGISTRO = "ClinicaVeterinaria";

    /**
     * Entrega uma mensagem de requisição empacotada e aguarda a mensagem de resposta.
     *
     * @param requisicao mensagem de requisição (JSON UTF-8)
     * @return mensagem de resposta (JSON UTF-8)
     */
    byte[] entregar(byte[] requisicao) throws RemoteException;
}
