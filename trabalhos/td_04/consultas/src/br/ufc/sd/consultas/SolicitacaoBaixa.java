package br.ufc.sd.consultas;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensagem de baixa enviada ao Estoque. Leva só ids e quantidade; nome e saldo do produto
 * pertencem ao Estoque. O idMensagem permite ao Estoque ignorar entregas repetidas.
 */
public record SolicitacaoBaixa(String idMensagem, int idConsulta, int idProduto, int quantidade,
                               Instant registradaEm) {

    public static SolicitacaoBaixa nova(int idConsulta, int idProduto, int quantidade) {
        return new SolicitacaoBaixa(UUID.randomUUID().toString(), idConsulta, idProduto, quantidade,
                Instant.now());
    }

    /** JSON montado à mão: os campos são só números, UUID e data, sem nada para escapar. */
    public String toJson() {
        return "{\"id_mensagem\":\"" + idMensagem + "\",\"id_consulta\":" + idConsulta
                + ",\"id_produto\":" + idProduto + ",\"quantidade\":" + quantidade
                + ",\"registrada_em\":\"" + registradaEm + "\"}";
    }
}
