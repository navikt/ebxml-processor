CREATE TABLE payload
(
    reference_id        VARCHAR(256)		NOT NULL,
    content_id		    VARCHAR(256)		NOT NULL,
    content_type		VARCHAR(256)		NOT NULL,
    payload             TEXT		        NOT NULL,
    created_date		DATE		        NOT NULL,
	PRIMARY KEY (reference_id)
);
