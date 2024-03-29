insert into process_config
values ('Behandler',
        'HarBorgerFrikort',
        'EgenandelForesporsel',
        false,
        false,
        false,
        TRUE,
        TRUE,
        TRUE,
           /* ADAPTER refererer til ekstra custom processering, nesten ingen flyter bruker det. */
        'LoggEgenandel',
        'Avvisning'),
    ('Utlever',
        'HarBorgerEgenandelFritak',
        'EgenandelForesporsel',
        false,
        false,
        false,
        TRUE,
        TRUE,
        TRUE,
           /* ADAPTER refererer til ekstra custom processering, nesten ingen flyter bruker det. */
        'LoggEgenandel',
        'Avvisning'),
    ('Frikortregister',
        'HarBorgerEgenandelFritak',
        'Svar',
        false,
        false,
        false,
        TRUE,
        false,
        false,
           /* ADAPTER refererer til ekstra custom processering, nesten ingen flyter bruker det. */
        null,
        null),
    ('Frikortregister',
        'HarBorgerEgenandelFritak',
        'Avvisning',
        false,
        false,
        false,
        TRUE,
        false,
        false,
           /* ADAPTER refererer til ekstra custom processering, nesten ingen flyter bruker det. */
        null,
        null)
on conflict (ROLE, SERVICE, ACTION) do update set KRYPTERING   = EXCLUDED.KRYPTERING,
                                                  KOMPRIMERING = EXCLUDED.KOMPRIMERING,
                                                  SIGNERING    = EXCLUDED.SIGNERING,
                                                  INTERNFORMAT = EXCLUDED.INTERNFORMAT,
                                                  VALIDERING   = EXCLUDED.VALIDERING,
                                                  APPREC       = EXCLUDED.APPREC,
    /* ADAPTER refererer til ekstra custom processering, nesten ingen bruker det. */
                                                  ADAPTER      = EXCLUDED.ADAPTER,
                                                  ERROR_ACTION = EXCLUDED.ERROR_ACTION;
