CREATE DATABASE auth_password;
\connect auth_password;
CREATE TABLE "auth_entry"(
  id SERIAL PRIMARY KEY,
  identity_id BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  email VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL
)