package br.ufc.sd.estoque.modelo;

import java.util.Objects;

/** Empresa que fornece itens ao estoque da clínica. */
public class Fornecedor {

    private int id;
    private String nome;
    private String cnpj;
    private String telefone;

    public Fornecedor() {
    }

    public Fornecedor(int id, String nome, String cnpj, String telefone) {
        this.id = id;
        this.nome = nome;
        this.cnpj = cnpj;
        this.telefone = telefone;
    }

    public Fornecedor(Fornecedor outro) {
        this(outro.id, outro.nome, outro.cnpj, outro.telefone);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fornecedor f)) return false;
        return id == f.id && Objects.equals(nome, f.nome) && Objects.equals(cnpj, f.cnpj)
                && Objects.equals(telefone, f.telefone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nome, cnpj, telefone);
    }

    @Override
    public String toString() {
        return "Fornecedor #" + id + " " + nome + " (CNPJ " + cnpj + ", tel. " + telefone + ")";
    }
}
