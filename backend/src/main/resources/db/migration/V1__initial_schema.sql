-- JH.COMP Engineering System :: esquema inicial

CREATE TABLE app_users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'ROLE_ADMIN',
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE TABLE posts (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(16)  NOT NULL UNIQUE,
    title           VARCHAR(180) NOT NULL,
    brand_pillar    VARCHAR(120),
    format          VARCHAR(24)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    audience        VARCHAR(16)  NOT NULL,
    slide_count     INTEGER      NOT NULL DEFAULT 1,
    summary         VARCHAR(400),
    caption         TEXT,
    hashtags        TEXT,
    cover_image_url VARCHAR(1024),
    cta_label       VARCHAR(160),
    scheduled_at    TIMESTAMP,
    estimated_reach INTEGER,
    estimated_saves INTEGER,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_posts_status ON posts (status);
CREATE INDEX idx_posts_audience ON posts (audience);

CREATE TABLE template_assets (
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(32)  NOT NULL UNIQUE,
    name         VARCHAR(160) NOT NULL,
    kind         VARCHAR(24)  NOT NULL,
    ratio        VARCHAR(24)  NOT NULL,
    theme        VARCHAR(16)  NOT NULL,
    composition  VARCHAR(240),
    preview_url  VARCHAR(1024),
    download_url VARCHAR(1024),
    file_name    VARCHAR(160),
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL
);

CREATE INDEX idx_assets_kind_ratio ON template_assets (kind, ratio);

CREATE TABLE copy_entries (
    id         BIGSERIAL PRIMARY KEY,
    post_code  VARCHAR(16),
    headline   VARCHAR(200) NOT NULL,
    tone       VARCHAR(24)  NOT NULL,
    body       TEXT         NOT NULL,
    hashtags   TEXT,
    cta        VARCHAR(240),
    strategy   VARCHAR(160),
    best_time  VARCHAR(60),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_copy_tone ON copy_entries (tone);
