package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DAO {
    protected Connection conexao;

    // Lê configuração de variável de ambiente (ou -Dchave=valor), com valor padrão
    public static String env(String chave, String padrao) {
        String v = System.getenv(chave);
        if (v == null || v.isBlank())
            v = System.getProperty(chave);
        return (v == null || v.isBlank()) ? padrao : v;
    }

    // Conectar no PostgreSQL (configurado por DB_URL, DB_USER, DB_PASSWORD)
    public synchronized boolean conectar() {
        try {
            conexao = DriverManager.getConnection(
                    env("DB_URL", "jdbc:postgresql://localhost:5432/timeawards"),
                    env("DB_USER", "postgres"),
                    env("DB_PASSWORD", ""));
            return true;
        } catch (SQLException e) {
            System.err.println("Erro na conexão: " + e.getMessage());
            return false;
        }
    }

    // Verifica se conexão está ativa e reconecta se necessário
    public synchronized boolean verificarConexao() {
        try {
            if (conexao != null && !conexao.isClosed() && conexao.isValid(2))
                return true;
        } catch (SQLException e) {
            System.err.println("Erro ao verificar conexão: " + e.getMessage());
        }
        System.out.println("Reconectando ao banco...");
        return conectar();
    }

    // Fechar conexão
    public synchronized boolean close() {
        try {
            if (conexao != null && !conexao.isClosed())
                conexao.close();
            return true;
        } catch (SQLException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
            return false;
        }
    }
}
