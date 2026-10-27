package service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import dao.AtividadeDAO;
import dao.DAO;
import dao.UsuarioDAO;
import model.Atividade;
import model.Usuario;

public class NotificacoesService {

    private static final String SMTP_HOST = DAO.env("SMTP_HOST", "smtp.gmail.com");
    private static final String SMTP_PORT = DAO.env("SMTP_PORT", "587");
    private static final String EMAIL_FROM = DAO.env("EMAIL_FROM", "");
    private static final String EMAIL_PASSWORD = DAO.env("EMAIL_PASSWORD", "");
    private static final String APP_URL = DAO.env("APP_URL", "http://localhost:6789");

    private static Timer timerRelatorio;
    private static Timer timerRelatorioSemanal;
    private static Timer timerLembretes;
    private static Timer timerHeartbeat;
    private static AtividadeDAO atividadeDAO;
    private static UsuarioDAO usuarioDAO;

    private static final String[] CLASSE_NOMES = Atividade.CLASSES;
    private static final int[] METAS_DIARIAS = Atividade.METAS_DIARIAS;
    private static final int[] HORARIOS_LEMBRETES = { 6, 12, 20 };
    private static final int DIA_RELATORIO_SEMANAL = 6;

    public static void iniciarTodosAgendamentos(AtividadeDAO ativDAO, UsuarioDAO usuDAO) {
        atividadeDAO = ativDAO;
        usuarioDAO = usuDAO;

        iniciarAgendamentoRelatorioDiario();
        iniciarAgendamentoRelatorioSemanal();
        iniciarAgendamentoLembretes();
        iniciarHeartbeat();
    }

    private static void iniciarHeartbeat() {
        if (timerHeartbeat != null) {
            timerHeartbeat.cancel();
        }

        timerHeartbeat = new Timer("HeartbeatTimer", true);

        timerHeartbeat.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    usuarioDAO.verificarConexao();
                    atividadeDAO.verificarConexao();
                    System.out
                            .println("[" + LocalDateTime.now().toLocalTime() + "] Heartbeat: Conexões verificadas");
                } catch (Exception e) {
                    System.err.println("❌ Erro no heartbeat: " + e.getMessage());
                }
            }
        }, 0, 5 * 60 * 1000);
    }

    private static void iniciarAgendamentoRelatorioDiario() {
        if (timerRelatorio != null) {
            timerRelatorio.cancel();
        }

        timerRelatorio = new Timer("RelatorioServiceTimer", true);
        agendarRelatoriosDiarios();
    }

    public static void iniciarAgendamentoRelatorioSemanal() {
        if (timerRelatorioSemanal != null) {
            timerRelatorioSemanal.cancel();
        }

        timerRelatorioSemanal = new Timer("RelatorioSemanalServiceTimer", true);
        agendarRelatoriosSemanais();
    }

    public static void iniciarAgendamentoLembretes() {
        if (timerLembretes != null) {
            timerLembretes.cancel();
        }

        timerLembretes = new Timer("LembretesServiceTimer", true);
        agendarLembretes();
    }

    private static void agendarLembretes() {
        LocalDateTime agora = LocalDateTime.now();

        for (int hora : HORARIOS_LEMBRETES) {
            LocalDateTime proximoLembrete = agora.withHour(hora).withMinute(0).withSecond(0);

            if (agora.isAfter(proximoLembrete)) {
                proximoLembrete = proximoLembrete.plusDays(1);
            }

            long delayMs = ChronoUnit.MILLIS.between(agora, proximoLembrete);

            timerLembretes.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    enviarTodosLembretes();
                }
            }, delayMs, 24 * 60 * 60 * 1000);
        }
    }

    private static void agendarRelatoriosDiarios() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime proximaExecucao = agora.withHour(23).withMinute(59).withSecond(0);

        if (agora.isAfter(proximaExecucao)) {
            proximaExecucao = proximaExecucao.plusDays(1);
        }

        long delayMs = ChronoUnit.MILLIS.between(agora, proximaExecucao);

        timerRelatorio.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                enviarTodosRelatorios();
            }
        }, delayMs, 24 * 60 * 60 * 1000);
    }

    private static void agendarRelatoriosSemanais() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime proximoSabado = agora;

        while (proximoSabado.getDayOfWeek().getValue() != DIA_RELATORIO_SEMANAL) {
            proximoSabado = proximoSabado.plusDays(1);
        }

        proximoSabado = proximoSabado.withHour(23).withMinute(59).withSecond(0);

        if (agora.getDayOfWeek().getValue() == DIA_RELATORIO_SEMANAL && agora.isAfter(proximoSabado)) {
            proximoSabado = proximoSabado.plusWeeks(1);
        }

        long delayMs = ChronoUnit.MILLIS.between(agora, proximoSabado);

        timerRelatorioSemanal.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                enviarTodosRelatoriosSemanais();
            }
        }, delayMs, 7 * 24 * 60 * 60 * 1000);
    }

    private static void enviarTodosRelatorios() {
        System.out.println("\n📧 Relatórios Diários iniciados");

        usuarioDAO.verificarConexao();
        atividadeDAO.verificarConexao();

        try {
            Usuario[] usuarios = usuarioDAO.listarUsuarios();

            if (usuarios == null || usuarios.length == 0) {
                System.out.println("⚠️ Nenhum usuário encontrado");
                return;
            }

            int enviados = 0;
            int pulados = 0;

            for (Usuario usuario : usuarios) {
                try {
                    if (!usuario.isNotificacoes()) {
                        pulados++;
                        continue;
                    }

                    if (enviarRelatorioUsuario(usuario)) {
                        enviados++;
                    }

                } catch (Exception e) {
                    System.err.println("❌ Erro ao enviar relatório para " + usuario.getNome() + ": " + e.getMessage());
                }
            }

            System.out.println("✅ Relatórios enviados: " + enviados + " | Pulados: " + pulados);

        } catch (Exception e) {
            System.err.println("❌ Erro crítico ao processar relatórios: " + e.getMessage());
        }
    }

    private static void enviarTodosRelatoriosSemanais() {
        System.out.println("\n📊 Relatórios Semanais iniciados");

        usuarioDAO.verificarConexao();
        atividadeDAO.verificarConexao();

        try {
            Usuario[] usuarios = usuarioDAO.listarUsuarios();

            if (usuarios == null || usuarios.length == 0) {
                System.out.println("⚠️ Nenhum usuário encontrado");
                return;
            }

            int enviados = 0;
            int pulados = 0;

            for (Usuario usuario : usuarios) {
                try {
                    if (!usuario.isNotificacoes()) {
                        pulados++;
                        continue;
                    }

                    if (enviarRelatorioSemanalUsuario(usuario)) {
                        enviados++;
                    }

                } catch (Exception e) {
                    System.err.println(
                            "❌ Erro ao enviar relatório semanal para " + usuario.getNome() + ": " + e.getMessage());
                }
            }

            System.out.println("✅ Relatórios semanais enviados: " + enviados + " | Pulados: " + pulados);

        } catch (Exception e) {
            System.err.println("❌ Erro crítico ao processar relatórios semanais: " + e.getMessage());
        }
    }

    private static void enviarTodosLembretes() {
        usuarioDAO.verificarConexao();
        atividadeDAO.verificarConexao();

        try {
            Usuario[] usuarios = usuarioDAO.listarUsuarios();

            if (usuarios == null || usuarios.length == 0) {
                System.out.println("⚠️ Nenhum usuário encontrado");
                return;
            }

            int enviados = 0;
            int pulados = 0;

            for (Usuario usuario : usuarios) {
                try {
                    if (!usuario.isNotificacoes()) {
                        pulados++;
                        continue;
                    }

                    if (precisaLembrete(usuario)) {
                        if (enviarLembreteUsuario(usuario)) {
                            enviados++;
                        }
                    } else {
                        pulados++;
                    }

                } catch (Exception e) {
                    System.err.println("❌ Erro ao enviar lembrete para " + usuario.getNome() + ": " + e.getMessage());
                }
            }

            System.out.println("Lembretes enviados: " + enviados + " | Pulados: " + pulados);

        } catch (Exception e) {
            System.err.println("❌ Erro crítico ao processar lembretes: " + e.getMessage());
        }
    }

    private static boolean precisaLembrete(Usuario usuario) {
        try {
            Atividade[] todasAtividades = atividadeDAO.listarAtividades(usuario.getId());

            if (todasAtividades == null || todasAtividades.length == 0) {
                return true;
            }

            LocalDateTime agora = LocalDateTime.now();
            LocalDateTime limite = agora.minusHours(4);

            for (Atividade a : todasAtividades) {
                if (a.getDataHora() != null && a.getDataHora().isAfter(limite)) {
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Erro ao verificar atividades de " + usuario.getNome());
            return false;
        }
    }

    private static boolean enviarLembreteUsuario(Usuario usuario) {
        try {
            LocalDateTime agora = LocalDateTime.now();
            String periodo = obterPeriodoDia(agora.getHour());
            String emoji = obterEmojiPeriodo(agora.getHour());

            String corpoHTML = gerarTemplateLembrete(
                    usuario.getNome(),
                    periodo,
                    emoji);

            String assunto = emoji + " Lembrete TimeAwards - Registre suas atividades!";
            return enviarRelatorio(usuario.getEmail(), assunto, corpoHTML);

        } catch (Exception e) {
            System.err.println("❌ Erro ao processar lembrete: " + e.getMessage());
            return false;
        }
    }

    private static String obterPeriodoDia(int hora) {
        if (hora >= 5 && hora < 12)
            return "Bom dia";
        else if (hora >= 12 && hora < 18)
            return "Boa tarde";
        else
            return "Boa noite";
    }

    private static String obterEmojiPeriodo(int hora) {
        if (hora >= 5 && hora < 12)
            return "☀️";
        else if (hora >= 12 && hora < 18)
            return "🌤️";
        else
            return "🌙";
    }

    private static boolean enviarRelatorioUsuario(Usuario usuario) {
        try {
            Atividade[] todasAtividades = atividadeDAO.listarAtividades(usuario.getId());

            if (todasAtividades == null || todasAtividades.length == 0) {
                return false;
            }

            LocalDateTime hoje = LocalDateTime.now();
            Atividade[] atividadesDia = Arrays.stream(todasAtividades)
                    .filter(a -> a.getDataHora() != null && a.getDataHora().toLocalDate().equals(hoje.toLocalDate()))
                    .toArray(Atividade[]::new);

            if (atividadesDia.length == 0) {
                return false;
            }

            double tempoTotal = 0;
            double[] temposPorClasse = new double[6];

            for (Atividade a : atividadesDia) {
                tempoTotal += a.getHorasGastas();
                if (a.getClasse() >= 0 && a.getClasse() < temposPorClasse.length) {
                    temposPorClasse[a.getClasse()] += a.getHorasGastas();
                }
            }

            String categoriaDestaque = "Nenhuma";
            double maiorTempo = 0;
            for (int i = 1; i < temposPorClasse.length; i++) {
                if (temposPorClasse[i] > maiorTempo) {
                    maiorTempo = temposPorClasse[i];
                    categoriaDestaque = CLASSE_NOMES[i];
                }
            }

            int metasAtingidas = 0;
            int totalMetas = 0;
            for (int i = 1; i < METAS_DIARIAS.length; i++) {
                totalMetas++;
                if (temposPorClasse[i] >= METAS_DIARIAS[i]) {
                    metasAtingidas++;
                }
            }

            String textoMetas = metasAtingidas + "/" + totalMetas + " metas cumpridas";

            StringBuilder detalhesHTML = new StringBuilder();
            for (Atividade a : atividadesDia) {
                String classeName = a.getClasse() >= 0 && a.getClasse() < CLASSE_NOMES.length
                        ? CLASSE_NOMES[a.getClasse()]
                        : "Outros";
                String emoji = a.getPrioridade() >= 3 ? "🔴" : a.getPrioridade() == 2 ? "🟡" : "🟢";

                detalhesHTML.append(String.format(
                        "<div class=\"atividade-item\"><strong>%s</strong> - <span style=\"color: #6b7280;\">%s</span> | <strong>%.2f h</strong> %s</div>",
                        esc(a.getNomeAtividade()), classeName, a.getHorasGastas(), emoji));
            }

            StringBuilder metasNaoAtingidas = new StringBuilder();
            for (int i = 1; i < METAS_DIARIAS.length; i++) {
                if (temposPorClasse[i] < METAS_DIARIAS[i]) {
                    if (metasNaoAtingidas.length() > 0) {
                        metasNaoAtingidas.append(", ");
                    }
                    metasNaoAtingidas.append(CLASSE_NOMES[i]);
                }
            }

            String sugestao = metasNaoAtingidas.length() > 0
                    ? "💡 Tente dedicar mais tempo para: " + metasNaoAtingidas.toString()
                    : "🎉 Parabéns! Todas as metas do dia foram atingidas!";

            String data = hoje.toLocalDate().toString();

            String corpoHTML = gerarRelatorioMonitoramentoDiario(
                    usuario.getNome(),
                    data,
                    tempoTotal,
                    textoMetas,
                    categoriaDestaque,
                    detalhesHTML.toString(),
                    sugestao);

            String assunto = "📊 Seu Relatório Diário - " + data;
            return enviarRelatorio(usuario.getEmail(), assunto, corpoHTML);

        } catch (Exception e) {
            System.err.println("❌ Erro ao processar relatório: " + e.getMessage());
            return false;
        }
    }

    private static boolean enviarRelatorioSemanalUsuario(Usuario usuario) {
        try {
            Atividade[] todasAtividades = atividadeDAO.listarAtividades(usuario.getId());

            if (todasAtividades == null || todasAtividades.length == 0) {
                return false;
            }

            LocalDateTime agora = LocalDateTime.now();

            int diaDaSemana = agora.getDayOfWeek().getValue();
            int diasAteProximoSabado = 6 - diaDaSemana;
            LocalDateTime fimSemana = agora.plusDays(diasAteProximoSabado).toLocalDate().atTime(23, 59, 59);
            LocalDateTime inicioSemana = fimSemana.minusDays(6).toLocalDate().atStartOfDay();

            Atividade[] atividadesSemana = Arrays.stream(todasAtividades)
                    .filter(a -> a.getDataHora() != null && !a.getDataHora().isBefore(inicioSemana)
                            && !a.getDataHora().isAfter(fimSemana))
                    .toArray(Atividade[]::new);

            if (atividadesSemana.length == 0) {
                return false;
            }

            double tempoTotal = 0;
            double[] temposPorClasse = new double[6];
            double[] temposPorDia = new double[7];

            for (Atividade a : atividadesSemana) {
                tempoTotal += a.getHorasGastas();
                if (a.getClasse() >= 0 && a.getClasse() < temposPorClasse.length) {
                    temposPorClasse[a.getClasse()] += a.getHorasGastas();
                }
                int diaSemana = (a.getDataHora().getDayOfWeek().getValue() % 7);
                temposPorDia[diaSemana] += a.getHorasGastas();
            }

            String categoriaDestaque = "Nenhuma";
            double maiorTempo = 0;
            for (int i = 1; i < temposPorClasse.length; i++) {
                if (temposPorClasse[i] > maiorTempo) {
                    maiorTempo = temposPorClasse[i];
                    categoriaDestaque = CLASSE_NOMES[i];
                }
            }

            String melhorDia = "Nenhum";
            double maiorTempoDia = 0;
            String[] diasSemana = { "Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado" };
            for (int i = 0; i < temposPorDia.length; i++) {
                if (temposPorDia[i] > maiorTempoDia) {
                    maiorTempoDia = temposPorDia[i];
                    melhorDia = diasSemana[i];
                }
            }

            double mediaHoras = tempoTotal / 7.0;
            String periodo = inicioSemana.toLocalDate() + " a " + fimSemana.toLocalDate();

            int metasAtingidas = 0;
            for (int i = 1; i < METAS_DIARIAS.length; i++) {
                if (temposPorClasse[i] >= METAS_DIARIAS[i] * 7) {
                    metasAtingidas++;
                }
            }

            String corpoHTML = gerarTemplateRelatorioSemanal(
                    usuario.getNome(),
                    periodo,
                    tempoTotal,
                    String.format("%.1f h/dia", mediaHoras),
                    melhorDia + " (" + String.format("%.1f h", maiorTempoDia) + ")",
                    categoriaDestaque,
                    metasAtingidas + "/5 categorias com metas semanais atingidas",
                    atividadesSemana.length);

            String assunto = "📈 Seu Relatório Semanal - " + periodo;
            return enviarRelatorio(usuario.getEmail(), assunto, corpoHTML);

        } catch (Exception e) {
            System.err.println("❌ Erro ao processar relatório semanal: " + e.getMessage());
            return false;
        }
    }

    public static void pararAgendamento() {
        if (timerRelatorio != null) {
            timerRelatorio.cancel();
            System.out.println("⏹️ Agendamento de relatórios parado");
        }
        if (timerRelatorioSemanal != null) {
            timerRelatorioSemanal.cancel();
            System.out.println("⏹️ Agendamento de relatórios semanais parado");
        }
        if (timerLembretes != null) {
            timerLembretes.cancel();
            System.out.println("⏹️ Agendamento de lembretes parado");
        }
        if (timerHeartbeat != null) {
            timerHeartbeat.cancel();
            System.out.println("⏹️ Heartbeat parado");
        }
    }

    // Escapa texto antes de inserir em HTML de e-mail
    public static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static boolean enviarRelatorio(String emailDestino, String assunto, String corpoHTML) {
        if (EMAIL_FROM.isBlank() || EMAIL_PASSWORD.isBlank()) {
            System.err.println("SMTP não configurado (EMAIL_FROM / EMAIL_PASSWORD); e-mail não enviado.");
            return false;
        }
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.trust", SMTP_HOST);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_FROM, "TimeAwards"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(emailDestino));
            message.setSubject(assunto);
            message.setContent(corpoHTML.replace("{{APP_URL}}", APP_URL), "text/html; charset=utf-8");

            Transport.send(message);
            return true;

        } catch (Exception e) {
            System.err.println("❌ Erro ao enviar email: " + e.getMessage());
            return false;
        }
    }

    public static String gerarTemplateLembrete(String nomeUsuario, String periodo, String emoji) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="pt-BR">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body {
                                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                                    background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%);
                                    margin: 0;
                                    padding: 20px;
                                }
                                .container {
                                    max-width: 600px;
                                    margin: 0 auto;
                                    background: white;
                                    border-radius: 16px;
                                    overflow: hidden;
                                    box-shadow: 0 8px 24px rgba(59, 130, 246, 0.15);
                                }
                                .header {
                                    background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%);
                                    color: white;
                                    padding: 40px 30px;
                                    text-align: center;
                                    position: relative;
                                }
                                .header::before {
                                    content: '%s';
                                    font-size: 60px;
                                    display: block;
                                    margin-bottom: 15px;
                                    animation: float 3s ease-in-out infinite;
                                }
                                @keyframes float {
                                    0%%, 100%% { transform: translateY(0px); }
                                    50%% { transform: translateY(-10px); }
                                }
                                .header h1 {
                                    margin: 0;
                                    font-size: 28px;
                                    font-weight: 700;
                                    text-shadow: 0 2px 4px rgba(0,0,0,0.1);
                                }
                                .header p {
                                    margin: 10px 0 0 0;
                                    font-size: 16px;
                                    opacity: 0.95;
                                }
                                .content {
                                    padding: 40px 30px;
                                }
                                .greeting {
                                    font-size: 22px;
                                    color: #2563eb;
                                    font-weight: 700;
                                    margin-bottom: 20px;
                                    text-align: center;
                                }
                                .message {
                                    background: linear-gradient(135deg, #eff6ff 0%%, #dbeafe 100%%);
                                    padding: 25px;
                                    border-radius: 12px;
                                    margin: 25px 0;
                                    border-left: 5px solid #3b82f6;
                                    font-size: 16px;
                                    line-height: 1.8;
                                    color: #1f2937;
                                }
                                .message strong {
                                    color: #2563eb;
                                    font-weight: 700;
                                }
                                .cta-section {
                                    text-align: center;
                                    margin: 35px 0;
                                }
                                .cta-button {
                                    display: inline-block;
                                    background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%);
                                    color: white;
                                    text-decoration: none;
                                    padding: 16px 40px;
                                    border-radius: 50px;
                                    font-weight: 700;
                                    font-size: 16px;
                                    box-shadow: 0 4px 15px rgba(59, 130, 246, 0.4);
                                    transition: all 0.3s ease;
                                }
                                .cta-button:hover {
                                    transform: translateY(-2px);
                                    box-shadow: 0 6px 20px rgba(59, 130, 246, 0.6);
                                }
                                .tips {
                                    background: #fef3c7;
                                    border-left: 4px solid #fbbf24;
                                    padding: 20px;
                                    border-radius: 8px;
                                    margin-top: 25px;
                                }
                                .tips h3 {
                                    color: #92400e;
                                    margin: 0 0 12px 0;
                                    font-size: 16px;
                                    font-weight: 600;
                                }
                                .tips ul {
                                    margin: 0;
                                    padding-left: 20px;
                                    color: #78350f;
                                    line-height: 1.8;
                                }
                                .tips li {
                                    margin-bottom: 8px;
                                }
                                .footer {
                                    background: linear-gradient(135deg, #eff6ff 0%%, #dbeafe 100%%);
                                    padding: 25px 30px;
                                    text-align: center;
                                    border-top: 2px solid #bfdbfe;
                                }
                                .footer-text {
                                    color: #1e40af;
                                    font-weight: 700;
                                    font-size: 14px;
                                    margin: 0;
                                }
                                .footer-small {
                                    font-size: 12px;
                                    color: #6b7280;
                                    margin-top: 12px;
                                    line-height: 1.5;
                                }
                                .emoji-large {
                                    font-size: 48px;
                                    margin: 20px 0;
                                    display: block;
                                    text-align: center;
                                }
                                @media (max-width: 600px) {
                                    .header { padding: 30px 20px; }
                                    .content { padding: 30px 20px; }
                                    .header h1 { font-size: 24px; }
                                    .greeting { font-size: 20px; }
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>⏰ Hora de Registrar!</h1>
                                    <p>Não deixe suas atividades passarem despercebidas</p>
                                </div>

                                <div class="content">
                                    <div class="greeting">
                                        %s, %s! 👋
                                    </div>

                                    <div class="message">
                                        <strong>Notamos que você não registrou atividades recentemente.</strong><br><br>
                                        Manter seu registro atualizado é essencial para:
                                        <ul style="margin: 15px 0 0 20px; padding: 0;">
                                            <li>📊 Acompanhar seu progresso diário</li>
                                            <li>🎯 Alcançar suas metas de produtividade</li>
                                            <li>📈 Visualizar insights sobre sua rotina</li>
                                            <li>🏆 Conquistar suas recompensas</li>
                                        </ul>
                                    </div>

                                    <span class="emoji-large">✨</span>

                                    <div class="cta-section">
                                        <a href="{{APP_URL}}/modulos/atividades/atividades.html" class="cta-button">
                                            📝 Registrar Atividades Agora
                                        </a>
                                    </div>

                                    <div class="tips">
                                        <h3>💡 Dicas Rápidas:</h3>
                                        <ul>
                                            <li>Registre suas atividades assim que finalizá-las</li>
                                            <li>Seja específico ao nomear suas tarefas</li>
                                            <li>Defina prioridades para organizar melhor seu dia</li>
                                            <li>Acompanhe seu progresso no painel de monitoramento</li>
                                        </ul>
                                    </div>
                                </div>

                                <div class="footer">
                                    <p class="footer-text">🔔 Lembrete Automático - TimeAwards</p>
                                    <p class="footer-small">
                                        Organize sua rotina com inteligência | Enviado às %s<br>
                                        <a href="{{APP_URL}}/modulos/perfil/perfil.html" style="color: #2563eb; text-decoration: none; font-weight: 600;">
                                            Gerenciar notificações
                                        </a>
                                    </p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                emoji, periodo, esc(nomeUsuario), LocalDateTime.now().toLocalTime().toString().substring(0, 5));
    }

    public static String gerarRelatorioMonitoramentoDiario(String nomeUsuario, String data,
            double tempoTotal, String metasAtingidas, String categoriaDestaque,
            String detalhesAtividades, String sugestao) {

        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="pt-BR">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #e8f4fd; margin: 0; padding: 20px; }
                                .container { max-width: 700px; margin: 0 auto; background: white; border-radius: 16px; overflow: hidden; box-shadow: 0 8px 24px rgba(59, 130, 246, 0.15); }
                                .header { background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%); color: white; padding: 40px 30px; text-align: center; position: relative; }
                                .header::after { content: ''; position: absolute; bottom: -20px; left: 0; right: 0; height: 20px; background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%); clip-path: polygon(0 0, 100%% 0, 100%% 0, 0 100%%); }
                                .header h1 { margin: 0 0 10px 0; font-size: 28px; font-weight: 800; text-shadow: 0 2px 8px rgba(0,0,0,0.2); }
                                .header p { margin: 0; font-size: 16px; opacity: 0.95; font-weight: 500; }
                                .badge { display: inline-block; background: rgba(255,255,255,0.25); padding: 6px 16px; border-radius: 20px; font-size: 13px; margin-top: 12px; font-weight: 600; }
                                .content { padding: 45px 30px 35px 30px; }
                                .date-section { background: linear-gradient(135deg, #eff6ff 0%%, #dbeafe 100%%); border-left: 5px solid #3b82f6; padding: 20px; border-radius: 10px; margin-bottom: 30px; }
                                .date-section h2 { margin: 0; color: #1e40af; font-size: 20px; font-weight: 700; }
                                .metric-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 30px; }
                                .metric { background: linear-gradient(135deg, #f9fafb 0%%, #f3f4f6 100%%); padding: 20px; border-radius: 12px; border: 2px solid #e5e7eb; transition: transform 0.2s; }
                                .metric:hover { transform: translateY(-2px); border-color: #3b82f6; }
                                .metric .icon { font-size: 24px; margin-bottom: 8px; display: block; }
                                .metric-label { font-weight: 600; color: #6b7280; font-size: 13px; display: block; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }
                                .metric-value { font-weight: 800; font-size: 24px; color: #1f2937; }
                                .full-width { grid-column: 1 / -1; }
                                .atividades-section { background: linear-gradient(135deg, #dbeafe 0%%, #bfdbfe 100%%); border-radius: 12px; padding: 25px; margin: 25px 0; border-left: 6px solid #2563eb; }
                                .atividades-section h3 { color: #1e40af; margin: 0 0 15px 0; font-size: 18px; font-weight: 700; }
                                .atividade-item { background: white; padding: 15px; margin-bottom: 12px; border-radius: 8px; border-left: 3px solid #3b82f6; font-size: 14px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); transition: transform 0.2s; }
                                .atividade-item:hover { transform: translateX(4px); }
                                .atividade-item:last-child { margin-bottom: 0; }
                                .atividade-item strong { color: #1f2937; font-weight: 700; }
                                .sugestao { background: linear-gradient(135deg, #fef3c7 0%%, #fde68a 100%%); border-left: 5px solid #f59e0b; padding: 20px; border-radius: 10px; margin-top: 25px; }
                                .sugestao h4 { margin: 0 0 10px 0; color: #92400e; font-size: 16px; font-weight: 700; }
                                .sugestao p { margin: 0; color: #78350f; font-size: 14px; line-height: 1.6; }
                                .footer { background: linear-gradient(135deg, #eff6ff 0%%, #dbeafe 100%%); padding: 30px; text-align: center; border-top: 2px solid #bfdbfe; }
                                .footer-text { color: #1e40af; font-weight: 700; font-size: 15px; margin: 0 0 10px 0; }
                                .footer-small { font-size: 12px; color: #6b7280; line-height: 1.6; }
                                .footer a { color: #2563eb; text-decoration: none; font-weight: 600; }
                                .emoji { margin-right: 6px; }
                                @media (max-width: 600px) {
                                    .metric-grid { grid-template-columns: 1fr; }
                                    .header { padding: 25px 20px; }
                                    .content { padding: 25px 20px; }
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>📊 Relatório Diário</h1>
                                    <p>Olá, %s! Aqui está seu resumo de atividades</p>
                                    <span class="badge">📅 Resumo de Hoje</span>
                                </div>
                                <div class="content">
                                    <div class="date-section">
                                        <h2>📅 %s</h2>
                                    </div>

                                    <div class="metric-grid">
                                        <div class="metric">
                                            <span class="icon">⏱️</span>
                                            <span class="metric-label">Tempo Total</span>
                                            <span class="metric-value">%.2f h</span>
                                        </div>

                                        <div class="metric">
                                            <span class="icon">🎯</span>
                                            <span class="metric-label">Metas Atingidas</span>
                                            <span class="metric-value">%s</span>
                                        </div>

                                        <div class="metric full-width">
                                            <span class="icon">⭐</span>
                                            <span class="metric-label">Categoria Destaque</span>
                                            <span class="metric-value">%s</span>
                                        </div>
                                    </div>

                                    <div class="atividades-section">
                                        <h3>📝 Atividades Registradas</h3>
                                        %s
                                    </div>

                                    <div class="sugestao">
                                        <h4>💡 Dica do dia</h4>
                                        <p>%s</p>
                                    </div>
                                </div>
                                <div class="footer">
                                    <p class="footer-text">✅ Relatório Diário Gerado Automaticamente</p>
                                    <p class="footer-small">
                                        TimeAwards - Organize sua rotina com inteligência<br>
                                        <a href="{{APP_URL}}/modulos/monitoramento/monitoramento.html">Ver painel completo</a> |
                                        <a href="{{APP_URL}}/modulos/perfil/perfil.html">Configurações</a>
                                    </p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                esc(nomeUsuario), data, tempoTotal, metasAtingidas, categoriaDestaque, detalhesAtividades, sugestao);
    }

    public static String gerarTemplateRelatorioDiario(String nomeUsuario, String data,
            double tempoTotal, String metasAtingidas, String categoriaDestaque, String sugestao) {

        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="pt-BR">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f0f8ff; margin: 0; padding: 20px; }
                                .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 10px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                                .header { background: linear-gradient(to right, #3b82f6, #2563eb); color: white; padding: 30px; text-align: center; }
                                .header h1 { margin: 0; font-size: 24px; }
                                .content { padding: 30px; }
                                .metric { background: #f9fafb; padding: 15px; margin: 10px 0; border-radius: 8px; display: flex; justify-content: space-between; align-items: center; }
                                .metric-label { font-weight: 500; color: #6b7280; }
                                .metric-value { font-weight: 700; font-size: 18px; color: #1f2937; }
                                .footer { background: #f0f8ff; padding: 20px; text-align: center; color: #3b82f6; font-weight: 500; }
                                .emoji { font-size: 24px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <span class="emoji">📊</span>
                                    <h1>Relatório Diário - TimeAwards</h1>
                                    <p>Olá, %s! Aqui está seu resumo do dia</p>
                                </div>
                                <div class="content">
                                    <h2 style="color: #3b82f6; margin-bottom: 20px;">📅 %s</h2>

                                    <div class="metric">
                                        <span class="metric-label">⏱️ Tempo Total:</span>
                                        <span class="metric-value">%.2f h</span>
                                    </div>

                                    <div class="metric">
                                        <span class="metric-label">🎯 Metas Atingidas:</span>
                                        <span class="metric-value">%s</span>
                                    </div>

                                    <div class="metric">
                                        <span class="metric-label">⭐ Categoria Destaque:</span>
                                        <span class="metric-value">%s</span>
                                    </div>
                                </div>
                                <div class="footer">
                                    <p>%s</p>
                                    <p style="font-size: 12px; color: #6b7280; margin-top: 15px;">
                                        TimeAwards - Organize sua rotina com inteligência
                                    </p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                esc(nomeUsuario), esc(data), tempoTotal, esc(metasAtingidas), esc(categoriaDestaque), esc(sugestao));
    }

    public static String gerarTemplateRelatorioSemanal(String nomeUsuario, String periodo,
            double tempoTotal, String comparacao, String melhorDia, String categoriaDestaque, String insight,
            int totalAtividades) {

        return String.format(
                """
                        <!DOCTYPE html>
                        <html lang="pt-BR">
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #e8f4fd; margin: 0; padding: 20px; }
                                .container { max-width: 700px; margin: 0 auto; background: white; border-radius: 16px; overflow: hidden; box-shadow: 0 8px 24px rgba(59, 130, 246, 0.15); }
                                .header { background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%); color: white; padding: 40px 30px; text-align: center; position: relative; }
                                .header::after { content: ''; position: absolute; bottom: -20px; left: 0; right: 0; height: 20px; background: linear-gradient(135deg, #3b82f6 0%%, #2563eb 100%%); clip-path: polygon(0 0, 100%% 0, 100%% 0, 0 100%%); }
                                .header h1 { margin: 0 0 10px 0; font-size: 32px; font-weight: 800; text-shadow: 0 2px 8px rgba(0,0,0,0.2); }
                                .header p { margin: 0; font-size: 16px; opacity: 0.95; font-weight: 500; }
                                .badge { display: inline-block; background: rgba(255,255,255,0.25); padding: 6px 16px; border-radius: 20px; font-size: 13px; margin-top: 12px; font-weight: 600; }
                                .content { padding: 45px 30px 35px 30px; }
                                .period-section { background: linear-gradient(135deg, #eff6ff 0%%, #dbeafe 100%%); border-left: 5px solid #3b82f6; padding: 20px; border-radius: 10px; margin-bottom: 30px; }
                                .period-section h2 { margin: 0; color: #1e40af; font-size: 20px; font-weight: 700; }
                                .summary-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 30px; }
                                .summary-card { background: linear-gradient(135deg, #f9fafb 0%%, #f3f4f6 100%%); padding: 20px; border-radius: 12px; border: 2px solid #e5e7eb; transition: transform 0.2s; }
                                .summary-card:hover { transform: translateY(-2px); border-color: #3b82f6; }
                                .summary-card .icon { font-size: 28px; margin-bottom: 8px; }
                                .summary-card .label { font-size: 13px; color: #6b7280; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px; }
                                .summary-card .value { font-size: 24px; color: #1f2937; font-weight: 800; }
                                .highlight-section { background: linear-gradient(135deg, #dbeafe 0%%, #bfdbfe 100%%); border-radius: 12px; padding: 25px; margin: 25px 0; border-left: 6px solid #2563eb; }
                                .highlight-section h3 { margin: 0 0 15px 0; color: #1e40af; font-size: 18px; font-weight: 700; }
                                .highlight-item { background: white; padding: 15px; border-radius: 8px; margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center; box-shadow: 0 2px 4px rgba(0,0,0,0.05); }
                                .highlight-item:last-child { margin-bottom: 0; }
                                .highlight-item .label { font-weight: 600; color: #374151; }
                                .highlight-item .value { font-weight: 800; color: #2563eb; font-size: 18px; }
                                .stats-row { display: flex; justify-content: space-around; background: #f9fafb; padding: 20px; border-radius: 12px; margin: 25px 0; }
                                .stat-item { text-align: center; }
                                .stat-item .number { font-size: 36px; font-weight: 800; color: #3b82f6; }
                                .stat-item .text { font-size: 13px; color: #6b7280; font-weight: 600; margin-top: 5px; text-transform: uppercase; }
                                .insight { background: linear-gradient(135deg, #fef3c7 0%%, #fde68a 100%%); border-left: 5px solid #f59e0b; padding: 20px; border-radius: 10px; margin-top: 25px; }
                                .insight h4 { margin: 0 0 10px 0; color: #92400e; font-size: 16px; font-weight: 700; }
                                .insight p { margin: 0; color: #78350f; line-height: 1.6; font-size: 14px; }
                                .footer { background: linear-gradient(135deg, #eff6ff 0%%, #dbeafe 100%%); padding: 30px; text-align: center; border-top: 2px solid #bfdbfe; }
                                .footer-text { color: #1e40af; font-weight: 700; font-size: 15px; margin: 0 0 10px 0; }
                                .footer-small { font-size: 12px; color: #6b7280; line-height: 1.6; }
                                .footer a { color: #2563eb; text-decoration: none; font-weight: 600; }
                                @media (max-width: 600px) { .summary-grid { grid-template-columns: 1fr; } .stats-row { flex-direction: column; gap: 15px; } .header h1 { font-size: 26px; } }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>📈 Relatório Semanal</h1>
                                    <p>Olá, %s! Confira seu desempenho da semana</p>
                                    <span class="badge">🗓️ Resumo de 7 dias</span>
                                </div>

                                <div class="content">
                                    <div class="period-section">
                                        <h2>📅 Período: %s</h2>
                                    </div>

                                    <div class="summary-grid">
                                        <div class="summary-card">
                                            <div class="icon">⏱️</div>
                                            <div class="label">Tempo Total</div>
                                            <div class="value">%.1f h</div>
                                        </div>

                                        <div class="summary-card">
                                            <div class="icon">📊</div>
                                            <div class="label">Média Diária</div>
                                            <div class="value">%s</div>
                                        </div>

                                        <div class="summary-card">
                                            <div class="icon">🏆</div>
                                            <div class="label">Melhor Dia</div>
                                            <div class="value" style="font-size: 18px;">%s</div>
                                        </div>

                                        <div class="summary-card">
                                            <div class="icon">⭐</div>
                                            <div class="label">Destaque</div>
                                            <div class="value" style="font-size: 18px;">%s</div>
                                        </div>
                                    </div>

                                    <div class="stats-row">
                                        <div class="stat-item">
                                            <div class="number">%d</div>
                                            <div class="text">Atividades</div>
                                        </div>
                                        <div class="stat-item">
                                            <div class="number">7</div>
                                            <div class="text">Dias</div>
                                        </div>
                                    </div>

                                    <div class="highlight-section">
                                        <h3>🎯 Metas e Conquistas</h3>
                                        <div class="highlight-item">
                                            <span class="label">Metas Semanais</span>
                                            <span class="value">%s</span>
                                        </div>
                                    </div>

                                    <div class="insight">
                                        <h4>💡 Análise da Semana</h4>
                                        <p>Continue mantendo o foco em suas atividades! Seu melhor dia foi %s. Tente manter essa consistência nos próximos dias para maximizar sua produtividade.</p>
                                    </div>
                                </div>

                                <div class="footer">
                                    <p class="footer-text">✅ Relatório Semanal Gerado Automaticamente</p>
                                    <p class="footer-small">
                                        TimeAwards - Organize sua rotina com inteligência<br>
                                        <a href="{{APP_URL}}/modulos/monitoramento/monitoramento.html">Ver painel completo</a> |
                                        <a href="{{APP_URL}}/modulos/perfil/perfil.html">Configurações</a>
                                    </p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                esc(nomeUsuario), esc(periodo), tempoTotal, esc(comparacao), esc(melhorDia), esc(categoriaDestaque),
                totalAtividades, esc(insight), esc(melhorDia.split(" ")[0]));
    }
}
