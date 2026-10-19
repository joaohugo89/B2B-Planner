-- A publicação no Instagram passa a rodar em segundo plano: o post guarda o progresso,
-- o erro da última tentativa e a situação anterior, para voltar a ela quando falha.
ALTER TABLE posts ADD COLUMN publish_state VARCHAR(16);
ALTER TABLE posts ADD COLUMN publish_error TEXT;
ALTER TABLE posts ADD COLUMN publish_started_at TIMESTAMP;
ALTER TABLE posts ADD COLUMN publish_previous_status VARCHAR(32);

CREATE INDEX idx_posts_publish_state ON posts (publish_state);
