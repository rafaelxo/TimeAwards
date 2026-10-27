package service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dao.AtividadeDAO;
import dao.DAO;
import dao.RecomendacaoDAO;
import model.Atividade;
import model.Recomendacao;

public class RecomendadorService {

    // Configuração via variáveis de ambiente (nunca no código / frontend)
    private static final String ENDPOINT = DAO.env("AZURE_OPENAI_ENDPOINT", "");
    private static final String API_KEY = DAO.env("AZURE_OPENAI_KEY", "");
    private static final String DEPLOYMENT = DAO.env("AZURE_OPENAI_DEPLOYMENT", "");
    private static final String API_VERSION = DAO.env("AZURE_OPENAI_API_VERSION", "2024-08-01-preview");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Gson GSON = new Gson();

    private final AtividadeDAO atividadeDAO;
    private final RecomendacaoDAO recomendacaoDAO;

    public RecomendadorService(AtividadeDAO atividadeDAO, RecomendacaoDAO recomendacaoDAO) {
        this.atividadeDAO = atividadeDAO;
        this.recomendacaoDAO = recomendacaoDAO;
    }

    public static boolean iaConfigurada() {
        return !ENDPOINT.isBlank() && !API_KEY.isBlank() && !DEPLOYMENT.isBlank();
    }

    // Gera recomendações (IA ou fallback heurístico) e salva no banco
    public Recomendacao[] gerarRecomendacoesUsuario(int usuarioId) {
        Atividade[] atividades = atividadeDAO.listarAtividades(usuarioId);
        Recomendacao[] recomendacoes;
        if (atividades.length == 0 || !iaConfigurada()) {
            recomendacoes = gerarRecomendacoesPadrao(usuarioId);
        } else {
            try {
                String resposta = chat("Você é um assistente de produtividade.",
                        construirPrompt(construirContextoAtividades(atividades)), 1000, 0.7);
                recomendacoes = parseResposta(resposta, usuarioId);
            } catch (Exception e) {
                System.err.println("IA indisponível, usando recomendações padrão: " + e.getMessage());
                recomendacoes = gerarRecomendacoesPadrao(usuarioId);
            }
        }
        for (Recomendacao r : recomendacoes)
            recomendacaoDAO.inserirRecomendacao(r);
        return recomendacoes;
    }

    private String construirContextoAtividades(Atividade[] atividades) {
        StringBuilder sb = new StringBuilder("Lista de atividades do usuário:\n");

        Map<String, List<Atividade>> porClasse = new LinkedHashMap<>();
        for (Atividade a : atividades)
            porClasse.computeIfAbsent(Atividade.nomeClasse(a.getClasse()), k -> new ArrayList<>()).add(a);

        porClasse.forEach((classe, lista) -> {
            double total = lista.stream().mapToDouble(Atividade::getHorasGastas).sum();
            sb.append("\n").append(classe).append(" (").append(total).append("h total):\n");
            for (Atividade a : lista)
                sb.append("  - ").append(a.getNomeAtividade())
                        .append(" [prioridade ").append(Atividade.nomePrioridade(a.getPrioridade())).append("]")
                        .append(" (").append(a.getHorasGastas()).append("h, ").append(a.getDataHora()).append(")\n");
        });

        sb.append("\nResumo por prioridade:\n");
        for (int prio = 3; prio >= 1; prio--) {
            long qtd = 0;
            double horas = 0;
            for (Atividade a : atividades) {
                if (a.getPrioridade() == prio) {
                    qtd++;
                    horas += a.getHorasGastas();
                }
            }
            if (qtd > 0)
                sb.append("  ").append(Atividade.nomePrioridade(prio)).append(": ").append(qtd)
                        .append(" atividades, ").append(horas).append("h\n");
        }
        return sb.toString();
    }

    private String construirPrompt(String contextoAtividades) {
        return """
                Com base nas atividades abaixo (organizadas por classe e prioridade), gere 3 recomendações práticas para melhorar a rotina do usuário.
                Considere:
                - Equilíbrio entre estudo, trabalho, exercício, lazer e sono
                - Priorização: tarefas de alta prioridade devem ter atenção especial
                - Se há muitas tarefas de baixa prioridade consumindo tempo, sugira delegar ou eliminar
                - Técnicas de gestão de tempo (Pomodoro, blocos de tempo, matriz de Eisenhower)
                - Saúde mental (pausas, sono, lazer)
                - Se tarefas de alta prioridade têm poucas horas, sugira redistribuir tempo

                """ + contextoAtividades + """

                Retorne APENAS um JSON válido no formato:
                {"recomendacoes": [{"titulo": "Título curto", "descricao": "Descrição detalhada e prática", "relevancia": 1-3 (1=baixa, 2=média, 3=alta)}]}
                Não inclua texto adicional fora do JSON.""";
    }

    // Chamada genérica ao Azure OpenAI (chat completions). Retorna o conteúdo da resposta.
    public static String chat(String system, String user, int maxTokens, double temperature) throws Exception {
        if (!iaConfigurada())
            throw new IllegalStateException("Azure OpenAI não configurado");

        String url = (ENDPOINT.endsWith("/") ? ENDPOINT : ENDPOINT + "/")
                + "openai/deployments/" + DEPLOYMENT + "/chat/completions?api-version=" + API_VERSION;

        Map<String, Object> payload = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)),
                "max_tokens", maxTokens,
                "temperature", temperature);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("api-key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300)
            throw new Exception("Erro Azure OpenAI: HTTP " + resp.statusCode());

        JsonArray choices = JsonParser.parseString(resp.body()).getAsJsonObject().getAsJsonArray("choices");
        if (choices == null || choices.isEmpty())
            throw new Exception("Nenhuma resposta retornada pela IA");

        return choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString().trim();
    }

    // Extrai o primeiro objeto JSON de um texto (remove ```json ... ``` etc.)
    public static JsonObject extrairJson(String texto) {
        int ini = texto.indexOf('{'), fim = texto.lastIndexOf('}');
        if (ini < 0 || fim <= ini)
            throw new IllegalArgumentException("Resposta sem JSON");
        return JsonParser.parseString(texto.substring(ini, fim + 1)).getAsJsonObject();
    }

    private Recomendacao[] parseResposta(String respostaIA, int usuarioId) {
        try {
            JsonArray recs = extrairJson(respostaIA).getAsJsonArray("recomendacoes");
            List<Recomendacao> lista = new ArrayList<>();
            for (int i = 0; i < recs.size(); i++) {
                JsonObject o = recs.get(i).getAsJsonObject();
                int relevancia = Math.max(1, Math.min(3, o.get("relevancia").getAsInt()));
                lista.add(new Recomendacao(0, usuarioId, o.get("titulo").getAsString(),
                        o.get("descricao").getAsString(), relevancia, false));
            }
            if (!lista.isEmpty())
                return lista.toArray(new Recomendacao[0]);
        } catch (Exception e) {
            System.err.println("Erro ao parsear resposta IA: " + e.getMessage());
        }
        return gerarRecomendacoesPadrao(usuarioId);
    }

    private Recomendacao[] gerarRecomendacoesPadrao(int usuarioId) {
        return new Recomendacao[] {
                new Recomendacao(0, usuarioId, "Organize sua rotina",
                        "Comece registrando suas atividades diárias. Use blocos de tempo dedicados para estudo, trabalho e lazer.",
                        2, false),
                new Recomendacao(0, usuarioId, "Inclua pausas regulares",
                        "A cada 50 minutos de foco, faça uma pausa de 10 minutos. A técnica Pomodoro ajuda a manter a produtividade.",
                        2, false)
        };
    }
}
