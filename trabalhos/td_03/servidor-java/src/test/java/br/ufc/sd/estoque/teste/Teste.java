package br.ufc.sd.estoque.teste;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mini-framework de testes (sem dependências externas, como no TD01). */
public final class Teste {

    @FunctionalInterface
    public interface Corpo {
        void executar() throws Exception;
    }

    private int passou = 0;
    private int falhou = 0;
    private final List<String> falhas = new ArrayList<>();

    public void secao(String nome) {
        System.out.println();
        System.out.println("== " + nome + " ==");
    }

    public void caso(String nome, Corpo corpo) {
        long inicio = System.nanoTime();
        try {
            corpo.executar();
            passou++;
            System.out.printf("  [OK]     %s (%d ms)%n", nome, (System.nanoTime() - inicio) / 1_000_000);
        } catch (Throwable t) {
            falhou++;
            falhas.add(nome + ": " + t);
            System.out.println("  [FALHOU] " + nome);
            System.out.println("           " + t);
            StackTraceElement[] pilha = t.getStackTrace();
            for (int i = 0; i < Math.min(4, pilha.length); i++) System.out.println("             em " + pilha[i]);
        }
    }

    public int passou() { return passou; }
    public int falhou() { return falhou; }
    public List<String> falhas() { return falhas; }

    // ------------------------------------------------------------------ verificações

    public static void verdadeiro(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }

    public static void igual(Object esperado, Object obtido, String mensagem) {
        if (!Objects.equals(esperado, obtido)) {
            throw new AssertionError(mensagem + " — esperado <" + esperado + "> mas foi <" + obtido + ">");
        }
    }

    public static <T extends Throwable> T lanca(Class<T> tipo, Corpo corpo) {
        try {
            corpo.executar();
        } catch (Throwable t) {
            if (tipo.isInstance(t)) return tipo.cast(t);
            throw new AssertionError("Esperava " + tipo.getSimpleName() + " mas foi lançada " + t, t);
        }
        throw new AssertionError("Esperava " + tipo.getSimpleName() + ", mas nada foi lançado.");
    }
}
