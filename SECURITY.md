# Política de Segurança

## Versões suportadas

Apenas a versão mais recente da branch `main` recebe correções de segurança.

## Como reportar uma vulnerabilidade

**Não abra uma issue pública.** Use o reporte privado do GitHub:
aba **Security → Report a vulnerability** deste repositório.

Inclua, se possível:

- descrição do problema e impacto;
- passos para reproduzir (rota, payload, página);
- versão/commit afetado.

Você deve receber uma resposta em até 7 dias. Se a vulnerabilidade for confirmada,
a correção é publicada na `main` e o reporte é creditado (se desejar).

## Práticas adotadas no projeto

- Senhas com hash **PBKDF2-SHA256** (salt aleatório); hashes MD5 legados são migrados no login.
- Sessão por cookie **HttpOnly** assinado com HMAC (`AUTH_SECRET`); toda rota de dados usa o usuário do cookie, nunca um id enviado pelo cliente.
- Requisições de outras origens são recusadas nas rotas protegidas (CSRF).
- Todas as queries usam `PreparedStatement`.
- Dados do usuário são escapados antes de entrar em HTML (páginas e e-mails).
- Credenciais (banco, Azure OpenAI, SMTP) só por variáveis de ambiente; a chave da IA nunca vai ao navegador.

Nunca faça commit de arquivos `.env` ou de credenciais. Se uma chave vazar, **revogue-a imediatamente** no provedor.
