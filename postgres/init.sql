CREATE DATABASE auth_codecard;
\connect auth_codecard;
CREATE TABLE "auth_entry"(
  user_identifier CHAR(10) PRIMARY KEY,
  identity_id BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  last_card BIGINT
);

CREATE TABLE "code"(
  user_identifier CHAR(10) REFERENCES auth_entry,
  card_index BIGINT NOT NULL,
  code_index BIGINT NOT NULL,
  code VARCHAR(6) NOT NULL,
  created_at BIGINT NOT NULL,
  activated_at BIGINT,
  used_at BIGINT,
  PRIMARY KEY (user_identifier, card_index, code_index)
);