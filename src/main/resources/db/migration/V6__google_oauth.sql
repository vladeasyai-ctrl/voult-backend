ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

ALTER TABLE users ADD COLUMN google_sub VARCHAR(255);
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';

CREATE UNIQUE INDEX idx_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

UPDATE users SET auth_provider = 'LOCAL' WHERE auth_provider IS NULL;
