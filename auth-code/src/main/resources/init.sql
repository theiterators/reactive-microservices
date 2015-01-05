CREATE TABLE "auth_entry"(
  user_identifier VARCHAR(10) PRIMARY KEY,
  identity_id BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  last_card BIGINT
)

CREATE TABLE "code"(
  user_identifier VARCHAR(10),
  card_index BIGINT NOT NULL,
  code_index BIGINT NOT NULL,
  code VARCHAR(6) NOT NULL,
  created_at BIGINT NOT NULL,
  activated_at BIGINT,
  used_at BIGINT
)