drop table process_config;

CREATE TABLE process_config
(
    ROLE VARCHAR(50) NOT NULL,
    SERVICE VARCHAR(50) NOT NULL,
    ACTION VARCHAR(50) NOT NULL,
    KRYPTERING boolean NOT NULL,
    KOMPRIMERING boolean NOT NULL,
    SIGNERING boolean NOT NULL,
    INTERNFORMAT boolean NOT NULL,
    VALIDERING boolean NOT NULL,
    APPREC boolean NOT NULL,
    /* ADAPTER refererer til ekstra custom processering, nesten ingen flyter bruker det. */
    ADAPTER VARCHAR(50),
    CONSTRAINT "PROCESS_CONFIG_PK" PRIMARY KEY (ROLE, SERVICE, ACTION)
);