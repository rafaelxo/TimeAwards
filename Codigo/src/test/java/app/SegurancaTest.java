package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dao.UsuarioDAO;

class SegurancaTest {

    @Test
    void senhaPbkdf2EMd5Legado() {
        String hash = UsuarioDAO.hashSenha("Senha@123");
        assertTrue(UsuarioDAO.conferirSenha("Senha@123", hash));
        assertFalse(UsuarioDAO.conferirSenha("senha@123", hash));
        // MD5("Senha@123") como o PostgreSQL gravava antes
        assertTrue(UsuarioDAO.conferirSenha("Senha@123", md5("Senha@123")));
        assertFalse(UsuarioDAO.conferirSenha("outra", md5("Senha@123")));
    }

    @Test
    void tokenAssinadoEExpiracao() {
        long futuro = System.currentTimeMillis() / 1000 + 60;
        String token = Aplicacao.gerarToken(42, futuro);
        assertEquals(42, Aplicacao.validarToken(token));
        assertNull(Aplicacao.validarToken(token.replaceFirst("^42", "43")));   // id adulterado
        assertNull(Aplicacao.validarToken(Aplicacao.gerarToken(42, 1)));        // expirado
        assertNull(Aplicacao.validarToken("lixo"));
        assertNull(Aplicacao.validarToken(null));
    }

    private static String md5(String s) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("MD5")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
