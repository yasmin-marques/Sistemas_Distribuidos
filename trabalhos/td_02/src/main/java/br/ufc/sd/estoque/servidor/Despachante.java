package br.ufc.sd.estoque.servidor;

import br.ufc.sd.estoque.api.NaoAutenticadoException;
import br.ufc.sd.estoque.json.Json;
import br.ufc.sd.estoque.json.ObjetoJson;
import br.ufc.sd.estoque.protocolo.CargaResposta;
import br.ufc.sd.estoque.protocolo.CodecMensagem;
import br.ufc.sd.estoque.protocolo.CodigoErro;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.Mensagem;
import br.ufc.sd.estoque.protocolo.ProtocoloServidor;
import br.ufc.sd.estoque.protocolo.RemoteObjectRef;
import br.ufc.sd.estoque.protocolo.RequisicaoRecebida;
import br.ufc.sd.estoque.protocolo.TipoMensagem;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Despachante do servidor: executa o laço da figura do enunciado.
 * <pre>
 *   while (ativo) {
 *       req = protocolo.getRequest();                 // obtém a requisição
 *       trabalhadores.execute(() -> {
 *           esqueleto = tabela.localizar(ref);        // seleciona o objeto
 *           resultado = esqueleto.invocar(metodo, a); // executa o método
 *           protocolo.sendReply(resposta, host, porta);
 *       });
 *   }
 * </pre>
 * A execução vai para um conjunto de threads trabalhadoras, então requisições de clientes
 * diferentes são atendidas em paralelo — inclusive as que ficam aguardando notificações.
 */
public class Despachante implements AutoCloseable {

    /** Limite de requisições executando ao mesmo tempo. */
    public static final int MAXIMO_TRABALHADORES = 256;

    private final ProtocoloServidor protocolo;
    private final TabelaObjetosRemotos tabela;
    private final Consumer<String> registro;
    private final boolean detalhado;
    private final ThreadPoolExecutor trabalhadores;
    private volatile boolean ativo = false;
    private Thread laco;

    public Despachante(ProtocoloServidor protocolo, TabelaObjetosRemotos tabela, Consumer<String> registro,
                       boolean detalhado) {
        this.protocolo = Objects.requireNonNull(protocolo);
        this.tabela = Objects.requireNonNull(tabela);
        this.registro = Objects.requireNonNull(registro);
        this.detalhado = detalhado;
        AtomicInteger contador = new AtomicInteger();
        this.trabalhadores = new ThreadPoolExecutor(4, MAXIMO_TRABALHADORES, 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), r -> {
                    Thread t = new Thread(r, "trabalhador-" + contador.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
    }

    public synchronized void iniciar() {
        if (ativo) return;
        ativo = true;
        laco = new Thread(this::executarLaco, "despachante");
        laco.setDaemon(true);
        laco.start();
    }

    private void executarLaco() {
        while (ativo) {
            RequisicaoRecebida requisicao;
            try {
                requisicao = protocolo.getRequest();
            } catch (InterruptedException e) {
                return;
            }
            try {
                trabalhadores.execute(() -> atender(requisicao));
            } catch (RejectedExecutionException e) {
                responder(requisicao, respostaDeErro(requisicao, CodigoErro.ERRO_INTERNO,
                        "Servidor sobrecarregado; tente novamente."));
            }
        }
    }

    private void atender(RequisicaoRecebida requisicao) {
        responder(requisicao, processar(requisicao));
    }

    private void responder(RequisicaoRecebida requisicao, byte[] resposta) {
        try {
            if (!protocolo.sendReply(resposta, requisicao.clientHost(), requisicao.clientPort())) {
                registro.accept("[Despachante] O cliente " + requisicao.clientHost().getHostAddress()
                        + " não aguardava mais a resposta do canal " + requisicao.clientPort() + ".");
            }
        } catch (IllegalArgumentException e) {
            registro.accept("[Despachante] Resposta não enviada: " + e.getMessage());
        }
    }

    /**
     * Desempacota a requisição, seleciona o objeto, executa o método e empacota a resposta.
     * Nunca lança exceção: qualquer problema vira uma resposta de erro.
     */
    public byte[] processar(RequisicaoRecebida requisicao) {
        Mensagem req;
        try {
            req = CodecMensagem.desempacotar(requisicao.dados());
        } catch (IllegalArgumentException e) {
            registro.accept("[Despachante] Requisição malformada de " + requisicao.clientHost().getHostAddress()
                    + ": " + e.getMessage());
            return respostaDeErro(requisicao, CodigoErro.REQUISICAO_INVALIDA, "Requisição malformada: " + e.getMessage());
        }
        if (req.getMessageType() != TipoMensagem.REQUEST) {
            return resposta(req, CargaResposta.erro(CodigoErro.REQUISICAO_INVALIDA,
                    "Esperava messageType " + TipoMensagem.REQUEST + " (Request)."));
        }
        if (detalhado || !"aguardar".equals(req.getMethodId())) {
            registro.accept("[" + requisicao.clientHost().getHostAddress() + " canal " + requisicao.clientPort()
                    + "] Request #" + req.getRequestId() + " " + req.getObjectReference().objeto() + "."
                    + req.getMethodId());
        }

        byte[] carga;
        try {
            Esqueleto esqueleto = tabela.localizar(req.getObjectReference());
            ObjetoJson argumentos = argumentos(req.getArguments());
            carga = CargaResposta.sucesso(esqueleto.invocar(req.getMethodId(), argumentos));
        } catch (ExcecaoRemota e) {
            carga = CargaResposta.erro(e.getCodigo(), e.getMessage());
        } catch (NaoAutenticadoException e) {
            carga = CargaResposta.erro(CodigoErro.NAO_AUTENTICADO, e.getMessage());
        } catch (IllegalArgumentException e) {
            carga = CargaResposta.erro(CodigoErro.ARGUMENTO_INVALIDO, e.getMessage());
        } catch (IllegalStateException e) {
            carga = CargaResposta.erro(CodigoErro.OPERACAO_NEGADA, e.getMessage());
        } catch (RuntimeException e) {
            registro.accept("[Despachante] Erro interno em " + req.getMethodId() + ": " + e);
            carga = CargaResposta.erro(CodigoErro.ERRO_INTERNO, "Erro interno no servidor.");
        }
        return resposta(req, carga);
    }

    private static ObjetoJson argumentos(byte[] bytes) {
        Object valor = Json.lerBytes(bytes);
        if (valor == null) return ObjetoJson.vazio();
        return ObjetoJson.de(valor);
    }

    private static byte[] resposta(Mensagem req, byte[] carga) {
        return CodecMensagem.empacotar(new Mensagem(TipoMensagem.REPLY, req.getRequestId(),
                req.getObjectReference(), req.getMethodId(), carga));
    }

    /** Resposta para uma requisição que não pôde ser lida: requestId -1 e referência à porta servidora. */
    private byte[] respostaDeErro(RequisicaoRecebida requisicao, CodigoErro codigo, String mensagem) {
        RemoteObjectRef ref = tabela.referencia("PortaServidora", "PortaServidora");
        int requestId = -1;
        String metodo = "desconhecido";
        try {
            Mensagem req = CodecMensagem.desempacotar(requisicao.dados());
            requestId = req.getRequestId();
            ref = req.getObjectReference();
            metodo = req.getMethodId();
        } catch (IllegalArgumentException ilegivel) {
            // mantém os valores padrão
        }
        return CodecMensagem.empacotar(new Mensagem(TipoMensagem.REPLY, requestId, ref, metodo,
                CargaResposta.erro(codigo, mensagem)));
    }

    @Override
    public synchronized void close() {
        ativo = false;
        if (laco != null) laco.interrupt();
        trabalhadores.shutdownNow();
        try {
            trabalhadores.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
