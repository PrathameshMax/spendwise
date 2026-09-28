CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    credential_id UUID NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by_jti UUID,
    CONSTRAINT fk_refresh_tokens_credential FOREIGN KEY (credential_id) REFERENCES credentials (id)
);

CREATE INDEX idx_refresh_tokens_credential_id ON refresh_tokens (credential_id);
