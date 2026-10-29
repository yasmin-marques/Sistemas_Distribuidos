package br.ufc.sd.estoque.protocolo;

import java.net.InetAddress;

/**
 * O que {@link ProtocoloServidor#getRequest()} devolve: os bytes da requisição e o endereço
 * para onde a resposta deve ser enviada com {@link ProtocoloServidor#sendReply}.
 *
 * @param dados      mensagem de requisição empacotada
 * @param clientHost endereço IP do cliente, obtido pelo RMI ({@code RemoteServer.getClientHost()})
 * @param clientPort canal de retorno da chamada RMI que aguarda a resposta (ver {@link ProtocoloServidor})
 */
public record RequisicaoRecebida(byte[] dados, InetAddress clientHost, int clientPort) {
}
