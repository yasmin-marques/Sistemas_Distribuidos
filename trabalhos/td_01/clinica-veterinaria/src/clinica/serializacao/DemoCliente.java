package clinica.serializacao;

import clinica.modelo.Animal;
import clinica.modelo.Cachorro;
import clinica.modelo.Coelho;
import clinica.modelo.Gato;
import clinica.modelo.Medicamento;
import clinica.modelo.RegistroConsulta;


// Demonstração da Questão 3: cliente que usa o serviço remoto
public class DemoCliente {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 6000;

        try (ClienteVeterinaria cliente = new ClienteVeterinaria(host, porta)) {

            titulo("1) Registrando animais (id é gerado pelo servidor)");
            Animal rex = cliente.registrarAnimal(
                    new Cachorro(0, "Rex", 5, 28.5, "Carlos", "Labrador", Cachorro.Porte.GRANDE));
            Animal mimi = cliente.registrarAnimal(
                    new Gato(0, "Mimi", 3, 4.2, "Ana", "Siamês", true));
            Animal thor = cliente.registrarAnimal(
                    new Coelho(0, "Thor", 2, 2.1, "Beatriz", "Mini Lop", "Curta"));
            System.out.println(rex);
            System.out.println(mimi);
            System.out.println(thor);

            titulo("2) Listando animais (subclasses preservadas após a serialização)");
            for (Animal a : cliente.listarAnimais()) {
                System.out.println(a.getClass().getSimpleName() + " -> " + a);
            }

            titulo("3) Estoque inicial");
            for (Medicamento m : cliente.listarMedicamentos()) System.out.println(m);

            titulo("4) Consulta do Rex com dispensação de 2x Amoxicilina (id 1)");
            RegistroConsulta c = cliente.realizarConsulta(rex.getId(),
                    "Tosse e febre", "Infecção respiratória", 1, 2);
            System.out.println(c);
            System.out.println("Estoque da Amoxicilina agora: "
                    + cliente.listarMedicamentos().get(0).getQuantidade());

            titulo("5) Consulta sem medicamento (Mimi)");
            System.out.println(cliente.realizarConsulta(mimi.getId(), "Rotina", "Saudável", 0, 0));

            titulo("6) Histórico do Rex");
            cliente.historico(rex.getId()).forEach(System.out::println);

            titulo("7) Erros devolvidos pelo servidor");
            tentar(() -> cliente.buscarAnimal(999));
            tentar(() -> cliente.realizarConsulta(thor.getId(), "Apatia", "Estresse", 3, 50));
            tentar(() -> cliente.realizarConsulta(thor.getId(), "Ferida", "Corte", 4, 1));
        }
    }

    private interface Acao { void executar() throws Exception; }

    private static void tentar(Acao acao) throws Exception {
        try {
            acao.executar();
        } catch (ClienteVeterinaria.ErroRemotoException e) {
            System.out.println("Erro do servidor (esperado): " + e.getMessage());
        }
    }

    private static void titulo(String texto) {
        System.out.println("\n=== " + texto + " ===");
    }
}
