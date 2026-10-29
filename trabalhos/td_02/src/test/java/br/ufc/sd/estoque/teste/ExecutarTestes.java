package br.ufc.sd.estoque.teste;

import java.util.Arrays;
import java.util.List;

/**
 * Executa todos os testes do TD02.
 *
 * <pre>
 *   java -cp "out/classes;out/test-classes" br.ufc.sd.estoque.teste.ExecutarTestes            (todos)
 *   java -cp "out/classes;out/test-classes" br.ufc.sd.estoque.teste.ExecutarTestes --rapido   (sem os processos separados)
 * </pre>
 */
public final class ExecutarTestes {

    private ExecutarTestes() { }

    public static void main(String[] args) {
        List<String> opcoes = Arrays.asList(args);
        long inicio = System.currentTimeMillis();
        Teste t = new Teste();

        TesteRepresentacaoExterna.executar(t);
        TesteProtocolo.executar(t);
        TesteIntegracaoRmi.executar(t);
        if (!opcoes.contains("--rapido")) TesteProcessos.executar(t);
        TesteRestricoes.executar(t);

        System.out.println();
        System.out.printf("Resultado: %d passou, %d falhou (%.1f s).%n", t.passou(), t.falhou(),
                (System.currentTimeMillis() - inicio) / 1000.0);
        if (t.falhou() > 0) {
            System.out.println("Falhas:");
            t.falhas().forEach(f -> System.out.println("  - " + f));
        }
        System.exit(t.falhou() == 0 ? 0 : 1);
    }
}
