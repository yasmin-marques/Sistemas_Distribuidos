package br.ufc.sd.estoque.cliente;

import br.ufc.sd.estoque.api.AssinaturaRemota;
import br.ufc.sd.estoque.modelo.Notificacao;
import br.ufc.sd.estoque.protocolo.ExcecaoRemota;
import br.ufc.sd.estoque.protocolo.FalhaComunicacaoException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Thread do cliente que escuta o grupo de notificações (papel do {@code OuvinteMulticast} do TD01).
 *
 * <p>Em laço, chama {@code aguardar} na assinatura remota: a chamada fica retida no servidor
 * até chegar uma mensagem, que então é exibida na hora. A outra thread do cliente cuida da
 * interação com o usuário.
 */
public class OuvinteNotificacoes implements Runnable {

    /** Espera de cada chamada {@code aguardar}. */
    public static final long ESPERA_MS = 10_000;

    private static final int FALHAS_MAXIMAS = 10;

    private final AssinaturaRemota assinatura;
    private final Consumer<Notificacao> aoReceber;
    private final Consumer<String> aoAvisar;
    private volatile boolean ativo = true;
    private Thread thread;

    public OuvinteNotificacoes(AssinaturaRemota assinatura, Consumer<Notificacao> aoReceber, Consumer<String> aoAvisar) {
        this.assinatura = Objects.requireNonNull(assinatura);
        this.aoReceber = Objects.requireNonNull(aoReceber);
        this.aoAvisar = Objects.requireNonNull(aoAvisar);
    }

    public synchronized void iniciar() {
        if (thread != null) throw new IllegalStateException("Ouvinte já iniciado.");
        thread = new Thread(this, "thread-escuta-notificacoes");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        int falhas = 0;
        while (ativo) {
            try {
                List<Notificacao> recebidas = assinatura.aguardar(ESPERA_MS);
                falhas = 0;
                for (Notificacao n : recebidas) {
                    if (ativo) aoReceber.accept(n);
                }
            } catch (ExcecaoRemota e) {
                if (ativo) aoAvisar.accept("Assinatura encerrada pelo servidor (" + e.getMessage()
                        + "). Digite 'entrar' para voltar ao grupo.");
                ativo = false;
            } catch (FalhaComunicacaoException e) {
                if (!ativo) break;
                falhas++;
                if (falhas == 1) aoAvisar.accept("Sem contato com o servidor: " + e.getMessage() + " Tentando de novo...");
                if (falhas >= FALHAS_MAXIMAS) {
                    aoAvisar.accept("Desisti de escutar as notificações. Digite 'entrar' para tentar de novo.");
                    ativo = false;
                    break;
                }
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }

    /** Para de exibir notificações (a chamada em andamento termina sozinha no servidor). */
    public void parar() {
        ativo = false;
    }

    public boolean ativo() {
        return ativo;
    }

    public AssinaturaRemota assinatura() {
        return assinatura;
    }
}
