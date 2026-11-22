package br.ufc.sd.consultas;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.PossibleAuthenticationFailureException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publica as solicitações de baixa no RabbitMQ. A fila é declarada durável (assim ela existe
 * mesmo que o Estoque nunca tenha rodado), a mensagem é persistente e o método só retorna depois
 * do publisher confirm do broker.
 */
public final class PublicadorBaixas implements AutoCloseable {

    private static final int TEMPO_CONEXAO_MS = 5_000;
    private static final long TEMPO_CONFIRMACAO_MS = 5_000;

    private final ConfiguracaoRabbit config;
    private final AtomicBoolean devolvida = new AtomicBoolean();
    private Connection conexao;
    private Channel canal;

    public PublicadorBaixas(ConfiguracaoRabbit config) {
        this.config = config;
    }

    public void conectar() throws FalhaPublicacaoException {
        ConnectionFactory fabrica = new ConnectionFactory();
        fabrica.setHost(config.host());
        fabrica.setPort(config.porta());
        fabrica.setUsername(config.usuario());
        fabrica.setPassword(config.senha());
        fabrica.setVirtualHost(config.vhost());
        fabrica.setConnectionTimeout(TEMPO_CONEXAO_MS);
        fabrica.setAutomaticRecoveryEnabled(false); // se o broker cair, o erro tem que aparecer
        try {
            conexao = fabrica.newConnection("consultas");
            canal = conexao.createChannel();
            canal.queueDeclare(config.fila(), true, false, false, null); // durável, não exclusiva, sem auto-delete
            canal.confirmSelect(); // habilita o publisher confirm
            canal.addReturnListener(retorno -> devolvida.set(true));
        } catch (IOException | TimeoutException | RuntimeException e) {
            close();
            throw new FalhaPublicacaoException("não foi possível conectar ao RabbitMQ em "
                    + config.descricao() + ": " + descrever(e), e);
        }
    }

    /** Só retorna depois que o broker confirma; senão lança FalhaPublicacaoException. */
    public void publicar(SolicitacaoBaixa solicitacao) throws FalhaPublicacaoException {
        if (canal == null || !canal.isOpen()) {
            throw new FalhaPublicacaoException("canal com o RabbitMQ não está aberto", null);
        }
        AMQP.BasicProperties propriedades = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .contentEncoding("utf-8")
                .deliveryMode(2) // persistente
                .messageId(solicitacao.idMensagem())
                .timestamp(Date.from(solicitacao.registradaEm()))
                .appId("consultas")
                .type("estoque.baixa")
                .build();
        byte[] corpo = solicitacao.toJson().getBytes(StandardCharsets.UTF_8);
        devolvida.set(false);
        try {
            canal.basicPublish("", config.fila(), true, propriedades, corpo);
            canal.waitForConfirmsOrDie(TEMPO_CONFIRMACAO_MS);
        } catch (TimeoutException e) {
            throw new FalhaPublicacaoException("o broker não confirmou a mensagem em "
                    + TEMPO_CONFIRMACAO_MS / 1000 + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaPublicacaoException("publicação interrompida", e);
        } catch (IOException | RuntimeException e) {
            throw new FalhaPublicacaoException("o broker recusou a mensagem ou a conexão caiu: "
                    + descrever(e), e);
        }
        if (devolvida.get()) {
            throw new FalhaPublicacaoException("a mensagem não foi roteada para a fila '"
                    + config.fila() + "'", null);
        }
    }

    @Override
    public void close() {
        try {
            if (conexao != null && conexao.isOpen()) conexao.close(TEMPO_CONEXAO_MS);
        } catch (IOException | RuntimeException ignorada) {
            // já está encerrando
        }
        canal = null;
        conexao = null;
    }

    /** Causa raiz resumida em uma linha. */
    private static String descrever(Throwable e) {
        Throwable raiz = e;
        while (true) {
            if (raiz instanceof PossibleAuthenticationFailureException) {
                return "usuário ou senha recusados pelo broker (confira RABBITMQ_USER e RABBITMQ_PASSWORD)";
            }
            if (raiz.getCause() == null || raiz.getCause() == raiz) break;
            raiz = raiz.getCause();
        }
        String m = raiz.getMessage();
        return raiz.getClass().getSimpleName() + (m == null || m.isBlank() ? "" : ": " + m);
    }
}
