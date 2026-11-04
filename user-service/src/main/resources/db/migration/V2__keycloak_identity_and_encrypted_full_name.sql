-- Profiles are now keyed by the Keycloak user id (the access token's sub claim),
-- assigned by the application at first login, so the column has no default.
-- full_name holds AES-256-GCM ciphertext ("v1:" + Base64), which is longer than
-- the plaintext and has no useful maximum length.
ALTER TABLE user_profiles ALTER COLUMN full_name TYPE TEXT;

COMMENT ON COLUMN user_profiles.id IS 'Keycloak user id (JWT sub)';
COMMENT ON COLUMN user_profiles.full_name IS 'AES-256-GCM encrypted (EncryptedStringConverter)';
