package dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import model.Recomendacao;

public class RecomendacaoDAO extends DAO {

    private static Recomendacao ler(ResultSet rs) throws SQLException {
        return new Recomendacao(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getString("titulo"),
                rs.getString("descricao"),
                rs.getInt("relevancia"),
                rs.getBoolean("status"));
    }

    // Inserir nova recomendação
    public boolean inserirRecomendacao(Recomendacao recomendacao) {
        verificarConexao();
        String sql = "INSERT INTO recomendacao (usuario_id, titulo, descricao, relevancia, status) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setInt(1, recomendacao.getUsuarioId());
            st.setString(2, recomendacao.getTitulo());
            st.setString(3, recomendacao.getDescricao());
            st.setInt(4, recomendacao.getRelevancia());
            st.setBoolean(5, recomendacao.isStatus());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao inserir recomendação: " + e.getMessage());
            return false;
        }
    }

    // Listar recomendações de um usuário
    public Recomendacao[] listarRecomendacoes(int usuarioId) {
        List<Recomendacao> lista = new ArrayList<>();
        if (!verificarConexao())
            return new Recomendacao[0];

        String sql = "SELECT * FROM recomendacao WHERE usuario_id = ? ORDER BY relevancia DESC";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setInt(1, usuarioId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next())
                    lista.add(ler(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao listar recomendações: " + e.getMessage());
        }
        return lista.toArray(new Recomendacao[0]);
    }

    // Buscar recomendação específica por ID
    public Recomendacao buscarRecomendacaoPorId(int id, int usuarioId) {
        verificarConexao();
        String sql = "SELECT * FROM recomendacao WHERE id = ? AND usuario_id = ?";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setInt(1, id);
            st.setInt(2, usuarioId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? ler(rs) : null;
            }
        } catch (SQLException e) {
            System.err.println("Erro ao buscar recomendação: " + e.getMessage());
            return null;
        }
    }

    // Atualizar recomendação completa
    public boolean atualizarRecomendacao(Recomendacao recomendacao) {
        verificarConexao();
        String sql = "UPDATE recomendacao SET titulo = ?, descricao = ?, relevancia = ?, status = ? WHERE id = ? AND usuario_id = ?";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setString(1, recomendacao.getTitulo());
            st.setString(2, recomendacao.getDescricao());
            st.setInt(3, recomendacao.getRelevancia());
            st.setBoolean(4, recomendacao.isStatus());
            st.setInt(5, recomendacao.getId());
            st.setInt(6, recomendacao.getUsuarioId());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar recomendação: " + e.getMessage());
            return false;
        }
    }

    // Atualizar status da recomendação
    public boolean atualizarStatus(int id, int usuarioId, boolean novoStatus) {
        verificarConexao();
        String sql = "UPDATE recomendacao SET status = ? WHERE id = ? AND usuario_id = ?";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setBoolean(1, novoStatus);
            st.setInt(2, id);
            st.setInt(3, usuarioId);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar status da recomendação: " + e.getMessage());
            return false;
        }
    }

    // Excluir recomendação
    public boolean excluirRecomendacao(int id, int usuarioId) {
        verificarConexao();
        try (PreparedStatement st = conexao.prepareStatement("DELETE FROM recomendacao WHERE id = ? AND usuario_id = ?")) {
            st.setInt(1, id);
            st.setInt(2, usuarioId);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao excluir recomendação: " + e.getMessage());
            return false;
        }
    }
}
