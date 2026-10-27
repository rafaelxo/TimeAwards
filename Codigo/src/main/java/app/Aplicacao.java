package app;

import static spark.Spark.before;
import static spark.Spark.delete;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.halt;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.put;
import static spark.Spark.staticFiles;
import static spark.Spark.stop;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import dao.AtividadeDAO;
import dao.DAO;
import dao.RecomendacaoDAO;
import dao.UsuarioDAO;
import model.Atividade;
import model.Recomendacao;
import model.Usuario;
import service.NotificacoesService;
import service.RecomendadorService;
import spark.Request;
import spark.Response;

public class Aplicacao {
    private static final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private static final AtividadeDAO atividadeDAO = new AtividadeDAO();
    private static final RecomendacaoDAO recomendacaoDAO = new RecomendacaoDAO();
    private static final RecomendadorService recomendadorIA = new RecomendadorService(atividadeDAO, recomendacaoDAO);
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (d, t, c) -> new JsonPrimitive(d.toString()))
            .create();

    // ==================== AUTENTICAÇÃO (cookie HMAC) ====================

    private static final String COOKIE = "ta_auth";
    private static final int LEMBRAR_SEGUNDOS = 30 * 24 * 3600;
    private static final int SESSAO_SEGUNDOS = 12 * 3600;
    // Sem AUTH_SECRET definido, um segredo aleatório é gerado (todos deslogam ao reiniciar o servidor)
    private static final byte[] SEGREDO = segredo();

    private static byte[] segredo() {
        String s = DAO.env("AUTH_SECRET", "");
        if (!s.isBlank())
            return s.getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static String assinar(String dados) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SEGREDO, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(dados.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String gerarToken(int usuarioId, long expiraEm) {
        String dados = usuarioId + "." + expiraEm;
        return dados + "." + assinar(dados);
    }

    // Retorna o id do usuário se o token for válido e não expirado; senão null
    static Integer validarToken(String token) {
        if (token == null)
            return null;
        String[] p = token.split("\\.");
        if (p.length != 3)
            return null;
        byte[] esperado = assinar(p[0] + "." + p[1]).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(esperado, p[2].getBytes(StandardCharsets.UTF_8)))
            return null;
        try {
            if (Long.parseLong(p[1]) < System.currentTimeMillis() / 1000)
                return null;
            return Integer.parseInt(p[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int uid(Request req) {
        return req.attribute("uid");
    }

    // ==================== HELPERS ====================

    private static String json(Response res, int status, Object corpo) {
        res.type("application/json; charset=UTF-8");
        res.status(status);
        return gson.toJson(corpo);
    }

    private static String resultado(Response res, boolean ok, int statusOk, int statusFalha) {
        return json(res, ok ? statusOk : statusFalha, Map.of("success", ok));
    }

    private static String erro(Response res, int status, String mensagem) {
        return json(res, status, Map.of("success", false, "message", mensagem));
    }

    private static String erroCampo(Response res, String mensagem, String campo) {
        return json(res, 400, Map.of("success", false, "message", mensagem, "field", campo));
    }

    // Lê parâmetro da query string ou do corpo x-www-form-urlencoded (Jetty não parseia corpo de PUT)
    static String param(Request req, String nome) {
        String v = req.queryParams(nome);
        if (v != null)
            return v;
        String body = req.body();
        if (body == null || body.isEmpty())
            return null;
        for (String par : body.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length == 2 && URLDecoder.decode(kv[0], StandardCharsets.UTF_8).equals(nome))
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String texto(Request req, String nome) {
        String v = param(req, nome);
        return v == null ? "" : v.trim();
    }

    private static int inteiro(Request req, String nome) {
        return Integer.parseInt(texto(req, nome));
    }

    private static int idRota(Request req) {
        return Integer.parseInt(req.params(":id"));
    }

    private static String soDigitos(String telefone) {
        return telefone.replaceAll("[^0-9+]", "");
    }

    // Valida campos de cadastro/edição de usuário; retorna null se ok
    private static String validarUsuario(Response res, Usuario u, int idAtual, boolean senhaObrigatoria) {
        if (u.getNome().length() < 3)
            return erroCampo(res, "O nome deve ter no mínimo 3 caracteres.", "nome");
        if (!u.getLogin().matches("[A-Za-z0-9_]{5,50}"))
            return erroCampo(res, "Login deve ter 5 a 50 letras, números ou _.", "login");
        if (!u.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            return erroCampo(res, "Email inválido.", "email");
        if (!u.getTelefone().matches("\\+?[0-9]{10,15}"))
            return erroCampo(res, "Telefone deve ter de 10 a 15 dígitos.", "telefone");
        boolean temSenha = u.getSenha() != null && !u.getSenha().isEmpty();
        if ((senhaObrigatoria || temSenha) && u.getSenha().length() < 8)
            return erroCampo(res, "A senha deve ter no mínimo 8 caracteres.", "senha");
        if (usuarioDAO.loginExiste(u.getLogin(), idAtual))
            return erroCampo(res, "Este login já está em uso. Escolha outro.", "login");
        if (usuarioDAO.emailExiste(u.getEmail(), idAtual))
            return erroCampo(res, "Este email já está em uso. Escolha outro.", "email");
        if (usuarioDAO.telefoneExiste(u.getTelefone(), idAtual))
            return erroCampo(res, "Este telefone já está em uso. Escolha outro.", "telefone");
        return null;
    }

    private static Usuario lerUsuario(Request req) {
        Usuario u = new Usuario();
        u.setNome(texto(req, "nome"));
        u.setLogin(texto(req, "login"));
        u.setEmail(texto(req, "email").toLowerCase());
        u.setTelefone(soDigitos(texto(req, "telefone")));
        u.setSenha(texto(req, "senha"));
        return u;
    }

    // Lê e valida uma atividade do request; responde 400 (halt) se inválida
    private static Atividade lerAtividade(Request req, Response res) {
        Atividade a = new Atividade();
        a.setUsuarioId(uid(req));
        a.setNomeAtividade(texto(req, "nomeAtividade"));
        a.setClasse(inteiro(req, "classe"));
        a.setPrioridade(inteiro(req, "prioridade"));
        String horas = texto(req, "horasGastas").replace(',', '.');
        try {
            a.setHorasGastas(Double.parseDouble(horas));
        } catch (NumberFormatException e) {
            halt(400, erro(res, 400, "horasGastas inválido"));
        }
        if (a.getNomeAtividade().isEmpty() || a.getNomeAtividade().length() > 255) {
            halt(400, erro(res, 400, "Nome da atividade é obrigatório (máx. 255 caracteres)"));
        }
        if (a.getClasse() < 1 || a.getClasse() >= Atividade.CLASSES.length
                || a.getPrioridade() < 1 || a.getPrioridade() >= Atividade.PRIORIDADES.length) {
            halt(400, erro(res, 400, "Classe ou prioridade inválida"));
        }
        if (!(a.getHorasGastas() > 0 && a.getHorasGastas() <= 24)) {
            halt(400, erro(res, 400, "horasGastas deve estar entre 0 e 24"));
        }
        a.setMetaHoras(Atividade.METAS_DIARIAS[a.getClasse()]);
        return a;
    }

    private static double numero(Map<?, ?> dados, String chave) {
        Object v = dados.get(chave);
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    private static String str(Map<?, ?> dados, String chave) {
        Object v = dados.get(chave);
        return v == null ? "" : v.toString();
    }

    public static void main(String[] args) {
        port(Integer.parseInt(DAO.env("PORT", "6789")));
        staticFiles.location("/public");

        if (!usuarioDAO.conectar() || !atividadeDAO.conectar() || !recomendacaoDAO.conectar()) {
            System.err.println("Falha ao conectar no banco! Verifique DB_URL, DB_USER e DB_PASSWORD.");
            stop();
            return;
        }
        usuarioDAO.migrarColunaSenha();

        // Lembretes (6h, 12h, 20h), relatórios diários (23:59) e semanais (sábado 23:59)
        NotificacoesService.iniciarTodosAgendamentos(atividadeDAO, usuarioDAO);

        exception(NumberFormatException.class, (e, req, res) -> res.body(erro(res, 400, "Parâmetro numérico inválido")));
        exception(Exception.class, (e, req, res) -> {
            e.printStackTrace();
            res.body(erro(res, 500, "Erro interno do servidor"));
        });

        // Rotas protegidas: exigem cookie de login válido. Bloqueia também requisições de outras origens (CSRF).
        for (String rota : new String[] { "/usuarios/*", "/atividades", "/atividades/*", "/recomendacoes",
                "/recomendacoes/*", "/relatorios/*", "/ia/*" }) {
            before(rota, (req, res) -> {
                String origem = req.headers("Origin");
                if (origem != null && !origem.endsWith("://" + req.host()))
                    halt(403, erro(res, 403, "Origem não permitida"));
                Integer id = validarToken(req.cookie(COOKIE));
                if (id == null)
                    halt(401, erro(res, 401, "Não autenticado"));
                req.attribute("uid", id);
            });
        }

        // ==================== ROTAS DE USUÁRIO ====================

        // Cadastro (público)
        post("/usuarios", (req, res) -> {
            Usuario u = lerUsuario(req);
            String invalido = validarUsuario(res, u, 0, true);
            if (invalido != null)
                return invalido;
            return resultado(res, usuarioDAO.inserirUsuario(u), 201, 500);
        });

        // Buscar o próprio usuário
        get("/usuarios/:id", (req, res) -> {
            int id = idRota(req);
            if (id != uid(req))
                return erro(res, 403, "Acesso negado");
            Usuario u = usuarioDAO.buscarUsuarioPorId(id);
            if (u == null)
                return erro(res, 404, "Usuário não encontrado");
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("success", true);
            corpo.put("id", u.getId());
            corpo.put("nome", u.getNome());
            corpo.put("login", u.getLogin());
            corpo.put("email", u.getEmail());
            corpo.put("telefone", u.getTelefone());
            corpo.put("notificacoes", u.isNotificacoes());
            return json(res, 200, corpo);
        });

        // Atualizar o próprio usuário (senha vazia mantém a atual)
        put("/usuarios/:id", (req, res) -> {
            int id = idRota(req);
            if (id != uid(req))
                return erro(res, 403, "Acesso negado");
            Usuario u = lerUsuario(req);
            u.setId(id);
            u.setNotificacoes("true".equals(param(req, "notificacoes")));
            String invalido = validarUsuario(res, u, id, false);
            if (invalido != null)
                return invalido;
            return resultado(res, usuarioDAO.atualizarUsuario(u), 200, 404);
        });

        // Atualizar apenas preferência de notificações
        put("/usuarios/:id/notificacoes", (req, res) -> {
            int id = idRota(req);
            if (id != uid(req))
                return erro(res, 403, "Acesso negado");
            boolean notificacoes = "true".equals(param(req, "notificacoes"));
            boolean ok = usuarioDAO.atualizarNotificacoes(id, notificacoes);
            return json(res, ok ? 200 : 500, Map.of("success", ok, "notificacoes", notificacoes));
        });

        // Excluir a própria conta
        delete("/usuarios/:id", (req, res) -> {
            int id = idRota(req);
            if (id != uid(req))
                return erro(res, 403, "Acesso negado");
            boolean ok = usuarioDAO.excluirUsuario(id);
            if (ok)
                res.removeCookie("/", COOKIE);
            return resultado(res, ok, 200, 404);
        });

        // Login: devolve dados básicos e grava cookie HttpOnly assinado
        post("/login", (req, res) -> {
            Usuario u = usuarioDAO.autenticarUsuario(texto(req, "login"), texto(req, "senha"));
            if (u == null)
                return erro(res, 401, "Login ou senha inválidos");
            boolean lembrar = "true".equals(param(req, "lembrar"));
            long expira = System.currentTimeMillis() / 1000 + (lembrar ? LEMBRAR_SEGUNDOS : SESSAO_SEGUNDOS);
            res.cookie("/", COOKIE, gerarToken(u.getId(), expira), lembrar ? LEMBRAR_SEGUNDOS : -1,
                    req.scheme().equals("https"), true);
            return json(res, 200, Map.of("success", true, "id", u.getId(), "nome", u.getNome()));
        });

        post("/logout", (req, res) -> {
            res.removeCookie("/", COOKIE);
            return json(res, 200, Map.of("success", true));
        });

        // ==================== ROTAS DE ATIVIDADE ====================

        post("/atividades", (req, res) -> {
            Atividade a = lerAtividade(req, res);
            return resultado(res, atividadeDAO.inserirAtividade(a), 201, 500);
        });

        get("/atividades/:id", (req, res) -> {
            Atividade a = atividadeDAO.buscarAtividadePorId(idRota(req), uid(req));
            if (a == null)
                return erro(res, 404, "Atividade não encontrada");
            JsonObject corpo = gson.toJsonTree(a).getAsJsonObject();
            corpo.addProperty("success", true);
            return json(res, 200, corpo);
        });

        get("/atividades", (req, res) -> json(res, 200, atividadeDAO.listarAtividades(uid(req))));

        put("/atividades/:id", (req, res) -> {
            Atividade a = lerAtividade(req, res);
            a.setId(idRota(req));
            return resultado(res, atividadeDAO.atualizarAtividade(a), 200, 404);
        });

        delete("/atividades/:id", (req, res) -> resultado(res,
                atividadeDAO.excluirAtividade(idRota(req), uid(req)), 200, 404));

        // ==================== ROTAS DE RECOMENDAÇÃO ====================

        post("/recomendacoes", (req, res) -> {
            Recomendacao r = new Recomendacao(0, uid(req), texto(req, "titulo"), texto(req, "descricao"),
                    inteiro(req, "relevancia"), Boolean.parseBoolean(param(req, "status")));
            return resultado(res, recomendacaoDAO.inserirRecomendacao(r), 201, 500);
        });

        get("/recomendacoes/:id", (req, res) -> {
            Recomendacao r = recomendacaoDAO.buscarRecomendacaoPorId(idRota(req), uid(req));
            if (r == null)
                return erro(res, 404, "Recomendação não encontrada");
            JsonObject corpo = gson.toJsonTree(r).getAsJsonObject();
            corpo.addProperty("success", true);
            return json(res, 200, corpo);
        });

        get("/recomendacoes", (req, res) -> json(res, 200, recomendacaoDAO.listarRecomendacoes(uid(req))));

        put("/recomendacoes/:id", (req, res) -> {
            Recomendacao r = new Recomendacao(idRota(req), uid(req), texto(req, "titulo"), texto(req, "descricao"),
                    inteiro(req, "relevancia"), Boolean.parseBoolean(param(req, "status")));
            return resultado(res, recomendacaoDAO.atualizarRecomendacao(r), 200, 404);
        });

        put("/recomendacoes/:id/status", (req, res) -> resultado(res, recomendacaoDAO.atualizarStatus(
                idRota(req), uid(req), Boolean.parseBoolean(param(req, "status"))), 200, 404));

        delete("/recomendacoes/:id", (req, res) -> resultado(res,
                recomendacaoDAO.excluirRecomendacao(idRota(req), uid(req)), 200, 404));

        // Gerar recomendações via IA (ou heurística, se a IA não estiver configurada)
        post("/recomendacoes/gerar", (req, res) -> {
            Recomendacao[] recs = recomendadorIA.gerarRecomendacoesUsuario(uid(req));
            return json(res, 200, Map.of("success", true, "recomendacoes", recs, "total", recs.length));
        });

        // ==================== IA: interpretar texto livre de atividade ====================
        // Proxy para o Azure OpenAI: a chave fica só no servidor, nunca no navegador.
        post("/ia/interpretar", (req, res) -> {
            String entrada = texto(req, "texto");
            if (entrada.isEmpty() || entrada.length() > 2000)
                return erro(res, 400, "Texto vazio ou muito longo");
            if (!RecomendadorService.iaConfigurada())
                return erro(res, 503, "IA não configurada");
            String system = "Você extrai dados de uma descrição de atividade em linguagem natural. "
                    + "Retorne SOMENTE um objeto JSON com as chaves: nome (string), "
                    + "classe (Trabalho|Estudos|Atividade Física|Lazer|Sono), horas (número, pode ser decimal, mínimo 0.1) "
                    + "e prioridade (Baixa|Média|Alta). Exemplo: "
                    + "{\"nome\":\"Reunião com cliente\",\"classe\":\"Trabalho\",\"horas\":2,\"prioridade\":\"Alta\"}. "
                    + "Se algum campo for incerto, gere um valor sensato. Não inclua texto fora do JSON.";
            try {
                String conteudo = RecomendadorService.chat(system, entrada, 300, 0);
                return json(res, 200, Map.of("success", true, "resultado", RecomendadorService.extrairJson(conteudo)));
            } catch (Exception e) {
                System.err.println("Erro ao interpretar atividade: " + e.getMessage());
                return erro(res, 502, "Falha ao consultar a IA");
            }
        });

        // ==================== ENVIO MANUAL DE RELATÓRIOS ====================

        post("/relatorios/enviar", (req, res) -> {
            Map<?, ?> dados = gson.fromJson(req.body(), Map.class);
            if (dados == null)
                return erro(res, 400, "Corpo JSON obrigatório");

            Usuario usuario = usuarioDAO.buscarUsuarioPorId(uid(req));
            if (usuario == null)
                return erro(res, 404, "Usuário não encontrado");
            if (!usuario.isNotificacoes())
                return erro(res, 400, "Notificações desabilitadas para este usuário");

            String tipo = str(dados, "tipo");
            String assunto, corpoHTML;
            if ("diario".equals(tipo)) {
                String data = str(dados, "data");
                assunto = "📊 Seu Relatório Diário - " + data;
                corpoHTML = NotificacoesService.gerarTemplateRelatorioDiario(usuario.getNome(), data,
                        numero(dados, "tempoTotal"), str(dados, "metasAtingidas"),
                        str(dados, "categoriaDestaque"), str(dados, "sugestao"));
            } else if ("semanal".equals(tipo)) {
                String periodo = str(dados, "periodo");
                assunto = "📈 Seu Relatório Semanal - " + periodo;
                corpoHTML = NotificacoesService.gerarTemplateRelatorioSemanal(usuario.getNome(), periodo,
                        numero(dados, "tempoTotal"), str(dados, "comparacao"), str(dados, "melhorDia"),
                        str(dados, "categoriaDestaque"), str(dados, "insight"),
                        (int) numero(dados, "totalAtividades"));
            } else {
                return erro(res, 400, "Tipo de relatório inválido");
            }

            if (NotificacoesService.enviarRelatorio(usuario.getEmail(), assunto, corpoHTML))
                return json(res, 200, Map.of("success", true, "message", "Relatório enviado com sucesso!"));
            return erro(res, 500, "Erro ao enviar email");
        });
    }
}
