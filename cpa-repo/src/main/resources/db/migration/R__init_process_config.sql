insert into process_config
/*      Role               Service                     Action                  Krypt  Komp   Sign   IntF  Val    AppR   Adapter          ErrorAction */
values ('Behandler',       'HarBorgerFrikort',         'EgenandelForesporsel', false, false, false, TRUE, TRUE,  TRUE,  'LoggEgenandel', 'Avvisning'),
       ('Frikortregister', 'HarBorgerFrikort',         'Svar',                 false, false, false, TRUE, false, false, null,            null),
       ('Frikortregister', 'HarBorgerFrikort',         'Avvisning',            false, false, false, TRUE, false, false, null,            null),
       ('Utleverer',       'HarBorgerEgenandelFritak', 'EgenandelForesporsel', false, false, false, TRUE, TRUE,  TRUE,  'LoggEgenandel', 'Avvisning'),
       ('Frikortregister', 'HarBorgerEgenandelFritak', 'Svar',                 false, false, false, TRUE, false, false, null,            null),
       ('Frikortregister', 'HarBorgerEgenandelFritak', 'Avvisning',            false, false, false, TRUE, false, false, null,            null)
on conflict (ROLE, SERVICE, ACTION) do update set KRYPTERING   = EXCLUDED.KRYPTERING,
                                                  KOMPRIMERING = EXCLUDED.KOMPRIMERING,
                                                  SIGNERING    = EXCLUDED.SIGNERING,
                                                  INTERNFORMAT = EXCLUDED.INTERNFORMAT,
                                                  VALIDERING   = EXCLUDED.VALIDERING,
                                                  APPREC       = EXCLUDED.APPREC,
    /* ADAPTER refererer til ekstra custom processering, nesten ingen bruker det. */
                                                  ADAPTER      = EXCLUDED.ADAPTER,
                                                  ERROR_ACTION = EXCLUDED.ERROR_ACTION;
