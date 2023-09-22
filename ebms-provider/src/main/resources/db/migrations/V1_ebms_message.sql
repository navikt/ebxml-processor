CREATE TABLE ebms_message
(
	cpa_id				VARCHAR(256)		NOT NULL,
	conversation_id		VARCHAR(256)		NOT NULL,
	message_id			VARCHAR(256)		NOT NULL,
	ref_to_message_id	VARCHAR(256)		NULL,
	from_party_id		VARCHAR(256)		NOT NULL,
	from_role			VARCHAR(256)		NULL,
	to_party_id			VARCHAR(256)		NOT NULL,
	to_role				VARCHAR(256)		NULL,
	service				VARCHAR(256)		NOT NULL,
	action				VARCHAR(256)		NOT NULL,
	content				TEXT                NULL
	PRIMARY KEY (message_id,message_nr)
);