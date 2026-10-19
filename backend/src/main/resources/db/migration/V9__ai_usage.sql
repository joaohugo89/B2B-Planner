-- Consumo do assistente de IA: uma linha por chamada ao Claude, para o painel
-- mostrar o total do mês. O mesmo evento também vai para logs/api_usage.log.
CREATE TABLE ai_usage (
    id            BIGSERIAL   PRIMARY KEY,
    created_at    TIMESTAMP   NOT NULL,
    operation     VARCHAR(20) NOT NULL,
    model         VARCHAR(80) NOT NULL,
    input_tokens  BIGINT      NOT NULL,
    output_tokens BIGINT      NOT NULL,
    effort        VARCHAR(10)
);

CREATE INDEX idx_ai_usage_created_at ON ai_usage (created_at DESC);
