-- Conexão com o Instagram (uma conta por instalação).
CREATE TABLE instagram_accounts (
    id            BIGSERIAL PRIMARY KEY,
    ig_user_id    VARCHAR(40)  NOT NULL UNIQUE,
    username      VARCHAR(120),
    page_id       VARCHAR(40),
    page_name     VARCHAR(160),
    access_token  TEXT         NOT NULL,
    token_expires TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

-- Guarda o id da publicação no Instagram, para não publicar o mesmo post duas vezes.
ALTER TABLE posts ADD COLUMN instagram_media_id VARCHAR(40);
ALTER TABLE posts ADD COLUMN published_at TIMESTAMP;
