# 💻 Documentação Técnica - TimeAwards

> **Guia completo de desenvolvimento, arquitetura e implementação do código-fonte**

Este documento detalha a **estrutura técnica**, **padrões de projeto**, **decisões arquiteturais** e **fluxos de desenvolvimento** do sistema TimeAwards. Destinado a desenvolvedores que precisam entender, modificar ou dar manutenção no código.

📌 **Para visão geral do projeto** (funcionalidades, instalação, equipe): [README Principal](../README.md)

## 📁 Estrutura de Diretórios

```plaintext
Codigo/
├── 📂 src/main/
│   ├── 📂 java/                           # Backend Java
│   │   ├── 📂 app/
│   │   │   └── Aplicacao.java             # 🚀 Entry point, rotas Spark
│   │   ├── 📂 dao/                        # 🗄️ Data Access Objects
│   │   │   ├── DAO.java                   # Classe base (conexão + config por env)
│   │   │   ├── UsuarioDAO.java            # CRUD usuários + hash PBKDF2
│   │   │   ├── AtividadeDAO.java          # CRUD atividades + queries
│   │   │   └── RecomendacaoDAO.java       # CRUD recomendações
│   │   ├── 📂 model/                      # 🏗️ Entidades/POJOs
│   │   │   ├── Usuario.java               # 7 atributos (senha transient)
│   │   │   ├── Atividade.java             # 9 atributos + constantes de classe/prioridade
│   │   │   └── Recomendacao.java          # 6 atributos + status
│   │   └── 📂 service/                    # 🔧 Lógica de negócio
│   │       ├── NotificacoesService.java   # Email SMTP + agendamentos
│   │       └── RecomendadorService.java   # Azure OpenAI GPT-4o
│   │
│   └── 📂 resources/public/               # Frontend estático
│       ├── 📂 assets/                     # Recursos globais
│       │   ├── 📂 css/
│       │   │   ├── geral.css              # ⭐ Estilos compartilhados
│       │   │   ├── dashboard.css
│       │   │   └── navbar.css
│       │   ├── 📂 js/
│       │   │   ├── dashboard.js           # Lógica + Chart.js
│       │   │   └── navbar.js              # Menu lateral, logout, 401 e escapeHtml
│       │   └── 📂 imagens/
│       │       └── anonimo.png            # Avatar padrão
│       │
│       ├── 📂 modulos/                    # Módulos SPA
│       │   ├── 📂 login/
│       │   │   ├── login.html
│       │   │   ├── login.css
│       │   │   └── login.js               # Login, cadastro, "lembrar de mim"
│       │   ├── 📂 perfil/
│       │   │   ├── perfil.html
│       │   │   ├── perfil.css
│       │   │   └── perfil.js              # Edição de usuário
│       │   ├── 📂 atividades/
│       │   │   ├── atividades.html
│       │   │   ├── atividades.css
│       │   │   ├── atividades.js          # CRUD principal
│       │   │   └── 📂 ia's/               # 🤖 Inteligências
│       │   │       ├── ia-prompt.js       # Interpretação via POST /ia/interpretar
│       │   │       ├── ia-voz.js          # Speech Recognition
│       │   │       └── ia-foto.js         # Tesseract OCR
│       │   ├── 📂 monitoramento/
│       │   │   ├── monitoramento.html
│       │   │   ├── monitoramento.css
│       │   │   └── monitoramento.js       # Timeline + gráficos
│       │   └── 📂 recomendacoes/
│       │       ├── recomendacoes.html
│       │       ├── recomendacoes.css
│       │       └── recomendacoes.js       # Chamada IA backend
│       │
│       └── dashboard.html                 # 🏠 Página principal
│
├── 📂 src/test/java/app/                 # 🧪 Testes JUnit (senha e token)
├── 📄 pom.xml                             # Dependências Maven
├── 📄 database.sql                        # Schema PostgreSQL completo
├── 📄 .gitignore                          # Exclusões Git
└── 📄 README.md                          # Este arquivo
```

### 📊 Estatísticas do Projeto

- **Linhas de Código**: ~14.000 (Java, JS, CSS e HTML)

## 🏗️ Arquitetura e Padrões de Projeto

### 📐 Padrão Arquitetural: MVC Modificado

```
┌─────────────┐
│   CLIENTE   │  (Navegador)
│  HTML/CSS/JS│
└──────┬──────┘
       │ HTTP REST (JSON) + cookie de sessão ta_auth
       ↓
┌─────────────────────────────────┐
│      SPARK FRAMEWORK            │
│  ┌─────────────────────────┐   │
│  │   Aplicacao.java        │   │  ← Controller (rotas, auth, validação)
│  │  - before(...) → 401    │   │
│  │  - post("/atividades")  │   │
│  │  - post("/ia/interpretar")│ │
│  └────────┬────────────────┘   │
└───────────┼─────────────────────┘
            ↓
┌───────────────────────────────────┐
│      SERVICE LAYER                │
│  ┌────────────────────────────┐  │
│  │ NotificacoesService        │  │  ← E-mails e agendamentos
│  │ RecomendadorService (IA)   │  │  ← Azure OpenAI + fallback
│  └────────┬───────────────────┘  │
└───────────┼───────────────────────┘
            ↓
┌───────────────────────────────────┐
│      DAO LAYER                    │
│  UsuarioDAO · AtividadeDAO ·      │  ← JDBC + PreparedStatement
│  RecomendacaoDAO                  │
└───────────┼───────────────────────┘
            ↓
┌───────────────────────────────────┐
│    PostgreSQL (database.sql)      │
│  - usuario (senha PBKDF2)         │
│  - atividade (trigger metaCumprida)│
│  - recomendacao (status boolean)  │
└───────────────────────────────────┘
```

### 🎯 Padrões Utilizados

| Padrão | Onde | Benefício |
|--------|------|-----------|
| **DAO (Data Access Object)** | `dao/` | Isola o SQL do resto da aplicação |
| **Instância única por DAO** | `Aplicacao.java` | Uma conexão reutilizada por DAO, com reconexão automática |
| **Strategy / Fallback** | `RecomendadorService`, `ia-prompt.js` | IA do Azure quando configurada, heurística local quando não |
| **Agendamento** | `TimerTask` em `NotificacoesService` | Lembretes e relatórios automáticos |
| **Filtro (middleware)** | `before(...)` em `Aplicacao.java` | Autenticação centralizada de todas as rotas protegidas |

### 🔄 Fluxo de Dados Completo

**Exemplo: Adicionar Atividade**

```
1. Frontend (atividades.js)
   ↓ fetch('POST /atividades', form-urlencoded)  — cookie ta_auth vai junto

2. Backend (Aplicacao.java)
   ↓ before(): valida o cookie → req.attribute("uid")
   ↓ lerAtividade(): valida nome, classe, prioridade, horas (0–24]
   ↓ metaHoras calculada no servidor (Atividade.METAS_DIARIAS)

3. DAO (AtividadeDAO.java)
   ↓ INSERT com PreparedStatement (usuario_id = uid do cookie)

4. PostgreSQL
   ↓ trigger calcula metaCumprida

5. Response
   ↓ 201 {"success": true}  |  400 {"success": false, "message": "..."}

6. Frontend
   ↓ recarrega a lista e mostra toast
```

## 🔍 Detalhamento das Camadas

### 📦 Model Layer (`model/`)

#### `Usuario.java`

```java
public class Usuario {
    private int id;
    private String nome;
    private String login;                  // UNIQUE
    private transient String senha;        // hash; transient = nunca serializado em JSON
    private String email;                  // UNIQUE
    private String telefone;               // UNIQUE, só dígitos (10–15)
    private boolean notificacoes;          // receber e-mails
}
```

#### `Atividade.java`

```java
public class Atividade {
    public static final String[] CLASSES = { "", "Trabalho", "Estudos", "Atividade Física", "Lazer", "Sono" };
    public static final int[] METAS_DIARIAS = { 0, 6, 4, 2, 4, 8 };
    public static final String[] PRIORIDADES = { "", "Baixa", "Média", "Alta" };

    private int id, usuarioId, classe, metaHoras, prioridade;   // classe 1–5, prioridade 1=Baixa … 3=Alta
    private String nomeAtividade;
    private double horasGastas;
    private boolean metaCumprida;                               // calculado pelo trigger
    private LocalDateTime dataHora;
}
```

> As constantes são a **fonte única** de nomes e metas no backend e espelham `CLASSE_MAP`/`PRIORIDADE_MAP` de `atividades.js`.

#### `Recomendacao.java`

```java
public class Recomendacao {
    private int id, usuarioId;
    private String titulo, descricao;
    private int relevancia;            // 1=Baixa, 2=Média, 3=Alta
    private boolean status;            // false = pendente, true = concluída
}
```

---

### 🗄️ DAO Layer (`dao/`)

JDBC puro (sem ORM), sempre com `PreparedStatement` e try-with-resources.

#### `DAO.java`

- `env(chave, padrao)`: lê variável de ambiente (ou `-Dchave=valor`).
- `conectar()`: usa `DB_URL`, `DB_USER`, `DB_PASSWORD`.
- `verificarConexao()`: reconecta se a conexão caiu (chamado no início de cada método).

#### `UsuarioDAO.java`

| Método | Descrição |
|--------|-----------|
| `inserirUsuario(u)` | Grava com `hashSenha()` (PBKDF2-SHA256, 210 mil iterações, salt aleatório) |
| `autenticarUsuario(login, senha)` | Busca por login e confere a senha em Java; senhas MD5 legadas são aceitas e migradas para PBKDF2 |
| `buscarUsuarioPorId`, `atualizarUsuario`, `atualizarNotificacoes`, `excluirUsuario` | CRUD (senha vazia no update mantém a atual) |
| `loginExiste`, `emailExiste`, `telefoneExiste` | Checagem de duplicidade |
| `listarUsuarios()` | Uso interno dos agendamentos de e-mail (não exposto na API) |
| `migrarColunaSenha()` | Na inicialização, garante `senha TEXT` (o hash não cabe em 32 caracteres) |

#### `AtividadeDAO.java` / `RecomendacaoDAO.java`

- Toda query filtra por `usuario_id`, então um usuário nunca enxerga dados de outro.
- `UPDATE`/`DELETE` retornam `true` só se alguma linha foi afetada (senão a rota responde 404).

---

### ⚙️ Service Layer (`service/`)

#### `NotificacoesService.java` 📧

- **SMTP** configurado por `SMTP_HOST`, `SMTP_PORT`, `EMAIL_FROM`, `EMAIL_PASSWORD`. Sem credenciais, os e-mails são apenas pulados (com log).
- **Agendamentos** (`java.util.Timer`):
  - Lembretes: 6h, 12h e 20h (só para quem não registrou nada nas últimas 4h)
  - Relatório diário: 23:59
  - Relatório semanal: sábado 23:59
  - Heartbeat: a cada 5 min mantém as conexões ativas
- **Templates HTML** com `String.format`; todo texto vindo do usuário passa por `esc()` (escape de HTML). Os links usam `APP_URL`.

#### `RecomendadorService.java` 🤖

```
1. Lista as atividades do usuário
2. Monta o contexto: horas por classe e por prioridade
3. chat(): POST {AZURE_OPENAI_ENDPOINT}/openai/deployments/{DEPLOYMENT}/chat/completions
4. extrairJson(): pega o objeto JSON da resposta (ignora ```json ... ```)
5. Valida (relevância limitada a 1–3) e salva no banco
```

- Sem IA configurada, sem atividades ou com erro na API → recomendações padrão (também salvas).
- `chat()` e `extrairJson()` são reutilizados pela rota `/ia/interpretar`.

---

### 🌐 Controller Layer (`app/Aplicacao.java`)

**Autenticação:** `POST /login` grava o cookie `ta_auth` (HttpOnly) com `usuarioId.expiracao.assinaturaHMAC`. Com `lembrar=true` ele dura 30 dias; sem isso, vale até fechar o navegador (máx. 12h). As rotas protegidas usam **sempre o usuário do cookie**. O `usuarioId` enviado pelo frontend é ignorado. Requisições com `Origin` de outro site são recusadas (proteção CSRF).

**Erros:** respostas sempre em JSON `{"success": false, "message": "..."}`, com 400 (validação), 401 (sem login), 403 (outro usuário/origem), 404, 500 e 502/503 (IA).

## 🔌 Endpoints da API

```http
# Públicas
POST   /usuarios                  # Cadastro (nome, login, email, telefone, senha)
POST   /login                     # login, senha, lembrar → grava cookie
POST   /logout                    # Remove o cookie

# Usuário (só o próprio :id)
GET    /usuarios/:id
PUT    /usuarios/:id              # senha vazia mantém a atual
PUT    /usuarios/:id/notificacoes
DELETE /usuarios/:id

# Atividades (do usuário logado)
GET    /atividades
GET    /atividades/:id
POST   /atividades                # nomeAtividade, classe (1–5), horasGastas (0–24], prioridade (1–3)
PUT    /atividades/:id
DELETE /atividades/:id

# Recomendações (do usuário logado)
GET    /recomendacoes
GET    /recomendacoes/:id
POST   /recomendacoes
PUT    /recomendacoes/:id
PUT    /recomendacoes/:id/status
DELETE /recomendacoes/:id
POST   /recomendacoes/gerar       # IA ou fallback

# IA e relatórios
POST   /ia/interpretar            # texto → {nome, classe, horas, prioridade}
POST   /relatorios/enviar         # JSON {tipo: "diario"|"semanal", ...} → e-mail para o usuário logado
```

## 🚀 Setup e Execução

### Pré-requisitos

- **JDK 21+**
- **Maven 3.9+**
- **PostgreSQL 12+**

### Banco de Dados

```bash
createdb timeawards
psql -d timeawards -f database.sql
```

### Configuração (variáveis de ambiente)

| Variável | Obrigatória | Padrão |
|---|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | sim | `jdbc:postgresql://localhost:5432/timeawards` / `postgres` / vazio |
| `AUTH_SECRET` | recomendada | aleatório (todos deslogam ao reiniciar) |
| `AZURE_OPENAI_ENDPOINT` / `AZURE_OPENAI_KEY` / `AZURE_OPENAI_DEPLOYMENT` | não | sem IA → fallback local |
| `AZURE_OPENAI_API_VERSION` | não | `2024-08-01-preview` |
| `EMAIL_FROM` / `EMAIL_PASSWORD` | não | sem e-mails |
| `SMTP_HOST` / `SMTP_PORT` | não | `smtp.gmail.com` / `587` |
| `APP_URL` | não | `http://localhost:6789` |
| `PORT` | não | `6789` |

Exemplo (bash):

```bash
export DB_URL="jdbc:postgresql://localhost:5432/timeawards" DB_USER="postgres" DB_PASSWORD="sua_senha"
export AUTH_SECRET="uma-string-longa-e-aleatoria"
```

Exemplo (PowerShell):

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/timeawards"; $env:DB_USER = "postgres"; $env:DB_PASSWORD = "sua_senha"
```

> No Eclipse/IntelliJ, defina as mesmas variáveis na configuração de execução de `Aplicacao.java`.

### Execução

```bash
cd Codigo
mvn compile exec:java      # inicia em http://localhost:6789
mvn test                   # testes de senha e token
```

Ou rode `app.Aplicacao` direto pela IDE (importar como projeto Maven).

## 🔧 Desenvolvimento

### Adicionando um Novo Módulo

1. **Modelo** em `model/` (POJO com getters/setters).
2. **DAO** em `dao/` estendendo `DAO`, com `verificarConexao()` no início de cada método e try-with-resources.
3. **Rotas** em `Aplicacao.java`, usando `uid(req)` para o usuário logado e `json(...)`/`erro(...)` para responder. Se a rota for protegida, inclua o caminho no laço de `before(...)`.
4. **Frontend** em `resources/public/modulos/novo_modulo/`, carregando `/assets/js/navbar.js` antes do script da página (ele trata 401 e fornece `escapeHtml`).

### Boas Práticas do Projeto

- ✅ `PreparedStatement` sempre (nada de concatenar valores no SQL)
- ✅ Nunca confiar no `usuarioId` vindo do cliente: use `uid(req)`
- ✅ Validar entrada no servidor, mesmo já validando no frontend
- ✅ Dados do usuário em `innerHTML` → `escapeHtml(...)` (ou use `textContent`)
- ✅ Segredos só em variáveis de ambiente, nunca no código nem no frontend
- ✅ URLs relativas no frontend (`/atividades`, não `http://localhost:6789/atividades`)

## 🐛 Troubleshooting

| Problema | Solução |
|---|---|
| `Falha ao conectar no banco!` | Confira `DB_URL`, `DB_USER`, `DB_PASSWORD` e se o PostgreSQL está rodando (`psql -d timeawards`) |
| Volta para o login toda hora | Cookie expirado ou servidor reiniciado sem `AUTH_SECRET` fixo |
| Porta 6789 em uso | Defina `PORT`, ou finalize o processo (`netstat -ano \| findstr :6789` no Windows) |
| IA "indisponível" | Defina as três variáveis `AZURE_OPENAI_*`; sem elas o fallback local é usado (comportamento esperado) |
| E-mails não chegam | Defina `EMAIL_FROM` e `EMAIL_PASSWORD` (senha de app do Gmail) e ative notificações no perfil |
| `column "nomeatividade" does not exist` | O banco foi criado com outro schema; use `database.sql` |

## 💡 Decisões Técnicas

- **Spark Framework**: leve, inicia em menos de 1s e é ideal para uma API REST pequena e didática.
- **JDBC puro**: controle total das queries e aprendizado de SQL.
- **PBKDF2 (stdlib Java)**: hash de senha forte sem dependência extra. O MD5 antigo é migrado de forma transparente.
- **Cookie HMAC em vez de sessão do servidor**: sobrevive a reinícios (com `AUTH_SECRET` fixo), não precisa de tabela de sessões e é HttpOnly (o JavaScript não lê).
- **IA via backend**: a chave do Azure nunca chega ao navegador.
- **Frontend vanilla JS**: sem etapa de build, basta editar e recarregar.

## 🧪 Testes

- `src/test/java/app/SegurancaTest.java` (JUnit 5): hash PBKDF2, compatibilidade com MD5 legado, token válido/adulterado/expirado.
- Testes manuais: CRUD de atividades, IA (texto, voz, OCR), recomendações, dashboard e e-mails.

---

## 📚 Documentação Complementar

- 📖 [README Principal do Projeto](../README.md): visão geral e funcionalidades
- 🗄️ [Schema SQL](database.sql): tabelas, índices e trigger
- 🔐 [Política de Segurança](../SECURITY.md)

---

## 👥 Desenvolvimento

**Grupo 21 - TimeAwards** | PUC Minas - Ciência da Computação 2025/2

> 📋 Informações completas da equipe (membros, orientadores, contatos) no [README principal](../README.md#-equipe)

---

<div align="center">

**Desenvolvido com 💙, ☕ e muita 🤖 pelo Grupo 21 - TimeAwards**

**Status:** ✅ Funcional | **Versão:** 1.1.0 | **Última atualização:** 26/10/2026

</div>
