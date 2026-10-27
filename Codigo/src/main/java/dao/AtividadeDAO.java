package dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import model.Atividade;

public class AtividadeDAO extends DAO {

    private static Atividade ler(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("dataHora");
        return new Atividade(
                rs.getInt("id"),
                rs.getInt("usuario_id"),
                rs.getString("nomeAtividade"),
                rs.getInt("classe"),
                rs.getDouble("horasGastas"),
                rs.getInt("metaHoras"),
                rs.getBoolean("metaCumprida"),
                rs.getInt("prioridade"),
                ts != null ? ts.toLocalDateTime() : null);
    }

    // Inserir nova atividade para um usuário
    public boolean inserirAtividade(Atividade atividade) {
        verificarConexao();
        String sql = "INSERT INTO atividade (usuario_id, nomeAtividade, classe, horasGastas, metaHoras, prioridade) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setInt(1, atividade.getUsuarioId());
            st.setString(2, atividade.getNomeAtividade());
            st.setInt(3, atividade.getClasse());
            st.setDouble(4, atividade.getHorasGastas());
            st.setInt(5, atividade.getMetaHoras());
            st.setInt(6, atividade.getPrioridade());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao inserir atividade: " + e.getMessage());
            return false;
        }
    }

    // Listar atividades de um usuário
    public Atividade[] listarAtividades(int usuarioId) {
        List<Atividade> lista = new ArrayList<>();
        if (!verificarConexao())
            return new Atividade[0];

        String sql = "SELECT * FROM atividade WHERE usuario_id = ? ORDER BY dataHora DESC";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setInt(1, usuarioId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next())
                    lista.add(ler(rs));
            }
        } catch (SQLException e) {
            System.err.println("Erro ao listar atividades: " + e.getMessage());
        }
        return lista.toArray(new Atividade[0]);
    }

    // Buscar uma atividade específica por ID
    public Atividade buscarAtividadePorId(int id, int usuarioId) {
        verificarConexao();
        String sql = "SELECT * FROM atividade WHERE id = ? AND usuario_id = ?";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setInt(1, id);
            st.setInt(2, usuarioId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? ler(rs) : null;
            }
        } catch (SQLException e) {
            System.err.println("Erro ao buscar atividade: " + e.getMessage());
            return null;
        }
    }

    // Atualizar nome, classe, horas, meta ou prioridade de uma atividade
    public boolean atualizarAtividade(Atividade atividade) {
        verificarConexao();
        String sql = "UPDATE atividade SET nomeAtividade = ?, classe = ?, horasGastas = ?, metaHoras = ?, prioridade = ? "
                + "WHERE id = ? AND usuario_id = ?";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setString(1, atividade.getNomeAtividade());
            st.setInt(2, atividade.getClasse());
            st.setDouble(3, atividade.getHorasGastas());
            st.setInt(4, atividade.getMetaHoras());
            st.setInt(5, atividade.getPrioridade());
            st.setInt(6, atividade.getId());
            st.setInt(7, atividade.getUsuarioId());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar atividade: " + e.getMessage());
            return false;
        }
    }

    // Excluir atividade
    public boolean excluirAtividade(int id, int usuarioId) {
        verificarConexao();
        try (PreparedStatement st = conexao.prepareStatement("DELETE FROM atividade WHERE id = ? AND usuario_id = ?")) {
            st.setInt(1, id);
            st.setInt(2, usuarioId);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao excluir atividade: " + e.getMessage());
            return false;
        }
    }
}
