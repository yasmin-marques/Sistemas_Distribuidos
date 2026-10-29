package br.ufc.sd.estoque.api;

/**
 * Marca as interfaces cujos objetos são passados <b>por referência</b>.
 *
 * <p>Papel análogo ao de {@code java.rmi.Remote}: quando um método devolve (ou recebe) um
 * objeto destes, o que viaja na mensagem é uma {@code RemoteObjectRef}, não uma cópia. O
 * objeto continua vivendo no servidor e quem tem a referência invoca seus métodos remotamente.
 * Os demais tipos (entidades, listas, números, textos) são passados <b>por valor</b>, como
 * cópias em JSON.
 */
public interface ObjetoRemoto {
}
