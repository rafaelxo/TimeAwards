-- Schema PostgreSQL do TimeAwards
-- Nomes de colunas iguais aos usados nos DAOs (identificadores sem aspas viram minúsculas no PostgreSQL).
-- Uso: psql -U <usuario> -d timeawards -f database.sql

CREATE TABLE IF NOT EXISTS usuario (
    id           SERIAL PRIMARY KEY,
    nome         VARCHAR(255) NOT NULL,
    login        VARCHAR(100) NOT NULL UNIQUE,
    senha        TEXT         NOT NULL,          -- hash PBKDF2 (pbkdf2$iteracoes$salt$hash)
    email        VARCHAR(255) NOT NULL UNIQUE,
    telefone     VARCHAR(20)  UNIQUE,
    notificacoes BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS atividade (
    id            SERIAL PRIMARY KEY,
    usuario_id    INTEGER       NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    nomeAtividade VARCHAR(255)  NOT NULL,
    classe        INTEGER       NOT NULL CHECK (classe BETWEEN 1 AND 5),       -- 1 Trabalho, 2 Estudos, 3 Atividade Física, 4 Lazer, 5 Sono
    horasGastas   NUMERIC(5,2)  NOT NULL CHECK (horasGastas > 0 AND horasGastas <= 24),
    metaHoras     INTEGER       NOT NULL,
    metaCumprida  BOOLEAN       NOT NULL DEFAULT FALSE,
    prioridade    INTEGER       NOT NULL CHECK (prioridade BETWEEN 1 AND 3),   -- 1 Baixa, 2 Média, 3 Alta
    dataHora      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS recomendacao (
    id         SERIAL PRIMARY KEY,
    usuario_id INTEGER      NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    titulo     VARCHAR(255) NOT NULL,
    descricao  TEXT         NOT NULL,
    relevancia INTEGER      NOT NULL CHECK (relevancia BETWEEN 1 AND 3),       -- 1 Baixa, 2 Média, 3 Alta
    status     BOOLEAN      NOT NULL DEFAULT FALSE                             -- TRUE = concluída
);

CREATE INDEX IF NOT EXISTS idx_atividade_usuario_data ON atividade (usuario_id, dataHora DESC);
CREATE INDEX IF NOT EXISTS idx_recomendacao_usuario ON recomendacao (usuario_id);

-- metaCumprida calculada pelo banco
CREATE OR REPLACE FUNCTION calc_meta_cumprida() RETURNS TRIGGER AS $$
BEGIN
    NEW.metaCumprida := NEW.horasGastas >= NEW.metaHoras;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_meta_cumprida ON atividade;
CREATE TRIGGER trg_meta_cumprida
BEFORE INSERT OR UPDATE ON atividade
FOR EACH ROW EXECUTE FUNCTION calc_meta_cumprida();
