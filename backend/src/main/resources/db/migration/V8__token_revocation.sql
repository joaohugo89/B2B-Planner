-- Revogação de refresh tokens: sair de todas as sessões incrementa a versão
-- e invalida todos os tokens emitidos antes disso.
ALTER TABLE app_users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
