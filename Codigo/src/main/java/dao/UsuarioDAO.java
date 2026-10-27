package dao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import model.Usuario;

public class UsuarioDAO extends DAO {

    private static final int ITERACOES = 210_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== HASH DE SENHA (PBKDF2) ====================

    public static String hashSenha(String senha) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERACOES + "$" + b64(salt) + "$" + b64(pbkdf2(senha, salt, ITERACOES));
    }

    // Aceita o formato novo (pbkdf2$...) e o legado (MD5 hex do PostgreSQL)
    public static boolean conferirSenha(String senha, String armazenado) {
        if (senha == null || armazenado == null)
            return false;
        byte[] esperado, calculado;
        if (armazenado.startsWith("pbkdf2$")) {
            String[] p = armazenado.split("\\$");
            if (p.length != 4)
                return false;
            esperado = Base64.getDecoder().decode(p[3]);
            calculado = pbkdf2(senha, Base64.getDecoder().decode(p[2]), Integer.parseInt(p[1]));
        } else {
            esperado = armazenado.toLowerCase().getBytes(StandardCharsets.US_ASCII);
            calculado = md5Hex(senha).getBytes(StandardCharsets.US_ASCII);
        }
        return MessageDigest.isEqual(esperado, calculado);
    }

    private static byte[] pbkdf2(String senha, byte[] salt, int iteracoes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, iteracoes, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String md5Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    // ==================== CRUD ====================

    private static Usuario ler(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("id"),
                rs.getString("nome"),
                rs.getString("login"),
                rs.getString("senha"),
                rs.getString("email"),
                rs.getString("telefone"),
                rs.getBoolean("notificacoes"));
    }

    // Garante que a coluna senha comporta o hash PBKDF2 (antes era MD5 de 32 caracteres)
    public void migrarColunaSenha() {
        verificarConexao();
        try (Statement st = conexao.createStatement()) {
            st.execute("ALTER TABLE usuario ALTER COLUMN senha TYPE TEXT");
        } catch (SQLException e) {
            System.err.println("Aviso: não foi possível ajustar a coluna senha: " + e.getMessage());
        }
    }

    // Cadastrar usuário (registrar)
    public boolean inserirUsuario(Usuario u) {
        verificarConexao();
        String sql = "INSERT INTO usuario (nome, login, senha, email, telefone, notificacoes) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setString(1, u.getNome());
            st.setString(2, u.getLogin());
            st.setString(3, hashSenha(u.getSenha()));
            st.setString(4, u.getEmail());
            st.setString(5, u.getTelefone());
            st.setBoolean(6, u.isNotificacoes());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao inserir usuário: " + e.getMessage());
            return false;
        }
    }

    // Verifica se já existe outro usuário (id != idExcluir) com o mesmo valor na coluna
    private boolean existe(String coluna, String valor, int idExcluir) {
        verificarConexao();
        // coluna vem só das constantes abaixo, nunca do usuário
        String sql = "SELECT 1 FROM usuario WHERE " + coluna + " = ? AND id != ?";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            st.setString(1, valor);
            st.setInt(2, idExcluir);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Erro ao verificar " + coluna + ": " + e.getMessage());
            return true;
        }
    }

    public boolean loginExiste(String login, int idExcluir) {
        return existe("login", login, idExcluir);
    }

    public boolean emailExiste(String email, int idExcluir) {
        return existe("email", email, idExcluir);
    }

    public boolean telefoneExiste(String telefone, int idExcluir) {
        return existe("telefone", telefone, idExcluir);
    }

    // Autenticar usuário (login). Senhas MD5 antigas são migradas para PBKDF2 no primeiro login.
    public Usuario autenticarUsuario(String login, String senha) {
        verificarConexao();
        Usuario u = null;
        try (PreparedStatement st = conexao.prepareStatement("SELECT * FROM usuario WHERE login = ?")) {
            st.setString(1, login);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next())
                    u = ler(rs);
            }
        } catch (SQLException e) {
            System.err.println("Erro ao autenticar usuário: " + e.getMessage());
            return null;
        }
        if (u == null || !conferirSenha(senha, u.getSenha()))
            return null;
        if (!u.getSenha().startsWith("pbkdf2$"))
            atualizarSenha(u.getId(), senha);
        return u;
    }

    private void atualizarSenha(int id, String senha) {
        try (PreparedStatement st = conexao.prepareStatement("UPDATE usuario SET senha = ? WHERE id = ?")) {
            st.setString(1, hashSenha(senha));
            st.setInt(2, id);
            st.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erro ao migrar senha: " + e.getMessage());
        }
    }

    // Listar todos os usuários (uso interno: agendamentos de e-mail)
    public Usuario[] listarUsuarios() {
        verificarConexao();
        List<Usuario> lista = new ArrayList<>();
        try (PreparedStatement st = conexao.prepareStatement("SELECT * FROM usuario ORDER BY nome");
                ResultSet rs = st.executeQuery()) {
            while (rs.next())
                lista.add(ler(rs));
        } catch (SQLException e) {
            System.err.println("Erro ao listar usuários: " + e.getMessage());
        }
        return lista.toArray(new Usuario[0]);
    }

    // Buscar usuário por ID
    public Usuario buscarUsuarioPorId(int id) {
        verificarConexao();
        try (PreparedStatement st = conexao.prepareStatement("SELECT * FROM usuario WHERE id = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? ler(rs) : null;
            }
        } catch (SQLException e) {
            System.err.println("Erro ao buscar usuário: " + e.getMessage());
            return null;
        }
    }

    // Atualizar usuário. Senha vazia/nula mantém a senha atual.
    public boolean atualizarUsuario(Usuario u) {
        verificarConexao();
        boolean trocaSenha = u.getSenha() != null && !u.getSenha().isBlank();
        String sql = "UPDATE usuario SET nome = ?, login = ?, email = ?, telefone = ?, notificacoes = ?"
                + (trocaSenha ? ", senha = ?" : "") + " WHERE id = ?";
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            int i = 1;
            st.setString(i++, u.getNome());
            st.setString(i++, u.getLogin());
            st.setString(i++, u.getEmail());
            st.setString(i++, u.getTelefone());
            st.setBoolean(i++, u.isNotificacoes());
            if (trocaSenha)
                st.setString(i++, hashSenha(u.getSenha()));
            st.setInt(i, u.getId());
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar usuário: " + e.getMessage());
            return false;
        }
    }

    // Atualizar apenas notificações
    public boolean atualizarNotificacoes(int id, boolean notificacoes) {
        verificarConexao();
        try (PreparedStatement st = conexao.prepareStatement("UPDATE usuario SET notificacoes = ? WHERE id = ?")) {
            st.setBoolean(1, notificacoes);
            st.setInt(2, id);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao atualizar notificações: " + e.getMessage());
            return false;
        }
    }

    // Excluir usuário
    public boolean excluirUsuario(int id) {
        verificarConexao();
        try (PreparedStatement st = conexao.prepareStatement("DELETE FROM usuario WHERE id = ?")) {
            st.setInt(1, id);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Erro ao excluir usuário: " + e.getMessage());
            return false;
        }
    }
}
