CREATE TABLE payload
(
    reference_id        VARCHAR(256)		NOT NULL,
    content_id		    VARCHAR(256)		NOT NULL,
    content_type		VARCHAR(256)		NOT NULL,
    content             BYTEA		        NOT NULL,
    direction           VARCHAR(3)          NOT NULL CHECK (direction IN ('IN', 'OUT')),
    created_at          TIMESTAMP           DEFAULT now(),
	PRIMARY KEY (reference_id, content_id)
);
