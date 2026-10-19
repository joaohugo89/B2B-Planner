-- Tons de copy deixam de ser enum fixo e passam a ser cadastrados pelo usuário.
CREATE TABLE copy_tones (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(24)  NOT NULL UNIQUE,
    label       VARCHAR(60)  NOT NULL,
    description VARCHAR(240),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

INSERT INTO copy_tones (code, label, description, created_at, updated_at) VALUES
('AUTORIDADE', 'Autoridade', 'Método, bastidor e prova técnica', NOW(), NOW()),
('DOR',        'Dor & solução', 'Sintoma do cliente e o conserto', NOW(), NOW()),
('B2B',        'B2B / Web', 'Decisor de empresa, site e sistemas', NOW(), NOW());

-- Configurações simples da marca (hashtags fixas de todo post, etc.).
CREATE TABLE app_settings (
    setting_key   VARCHAR(60) PRIMARY KEY,
    setting_value TEXT        NOT NULL,
    updated_at    TIMESTAMP   NOT NULL
);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('global_hashtags', '#JHComp', NOW());
