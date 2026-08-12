CREATE TABLE communication_party_cache
(
    her_id                  BIGINT          NOT NULL,
    communication_party     TEXT,
    signing_cert            TEXT,
    encryption_cert         TEXT,
    last_updated            TIMESTAMP       NOT NULL,
    PRIMARY KEY (her_id)
);
