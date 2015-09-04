CREATE DATABASE identity_manager;
\connect identity_manager;
CREATE TABLE "identity"(
  id SERIAL PRIMARY KEY,
  created_at BIGINT NOT NULL
)
