-- Blocos de hashtags por categoria: além do campo global, o usuário monta conjuntos
-- nomeados e injeta cada um na legenda com um clique no Guia de Copy.
CREATE TABLE hashtag_blocks (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(60) NOT NULL UNIQUE,
    hashtags   TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL
);

INSERT INTO hashtag_blocks (name, hashtags, created_at, updated_at) VALUES
('Manutenção', '#ManutencaoPC #LimpezaDeNotebook #UpgradeDeSSD', NOW(), NOW()),
('Sites & Sistemas', '#CriacaoDeSites #SistemasWeb #DesenvolvimentoWeb', NOW(), NOW()),
('Local', '#Guarulhos #SaoPaulo #TecnologiaLocal', NOW(), NOW());
