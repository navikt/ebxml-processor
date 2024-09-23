insert into process_config(ROLE,SERVICE,ACTION,KRYPTERING,KOMPRIMERING,SIGNERING,INTERNFORMAT,VALIDERING,APPREC,OCSP_CHECK,ADAPTER,ERROR_ACTION)
/*      Role                Service                     Action                  Krypt  Komp   Sign   IntF   Val    AppR    ocspCheck   Adapter          ErrorAction */
values ('Behandler',        'HarBorgerFrikort',         'EgenandelForesporsel', false, false, false, TRUE,  TRUE,  TRUE,   false,  'LoggEgenandel', 'Avvisning'),
       ('Frikortregister',  'HarBorgerFrikort',         'Svar',                 false, false, false, TRUE,  false, false,  false,   null,            null       ),
       ('Frikortregister',  'HarBorgerFrikort',         'Avvisning',            false, false, false, TRUE,  false, false,  false,   null,            null       ),
       ('Utleverer',        'HarBorgerEgenandelFritak', 'EgenandelForesporsel', false, false, false, TRUE,  TRUE,  TRUE,   false,  'LoggEgenandel', 'Avvisning'),
       ('Frikortregister',  'HarBorgerEgenandelFritak', 'Svar',                 false, false, false, TRUE,  false, false,  false,   null,            null       ),
       ('Frikortregister',  'HarBorgerEgenandelFritak', 'Avvisning',            false, false, false, TRUE,  false, false,  false,   null,            null       ),
       ('Fastlegeregister', 'PasientlisteForesporsel',  'AbonnementStatus',     false, false, false, TRUE,  false, false,  false,   null,            null       ),
       ('Fastlegeregister', 'PasientlisteForesporsel',  'Avvisning',            false, false, false, TRUE,  false, false,  false,   null,            null       ),
       ('Fastlegeregister', 'PasientlisteForesporsel',  'Kvittering',           false, false, false, TRUE,  false, false,  false,   null,            null       ),
       ('Fastlege',         'PasientlisteForesporsel',  'HentAbonnementStatus', false, false, TRUE,  TRUE,  TRUE,  TRUE,   false,   null,            'Avvisning'),
       ('Fastlege',         'PasientlisteForesporsel',  'HentPasientliste',     false, false, TRUE,  TRUE,  TRUE,  TRUE,   TRUE,    null,            'Avvisning'),
       ('Fastlege',         'PasientlisteForesporsel',  'StartAbonnement',      false, false, TRUE,  TRUE,  TRUE,  TRUE,   false,   null,            'Avvisning'),
       ('Fastlege',         'PasientlisteForesporsel',  'StoppAbonnement',      false, false, TRUE,  TRUE,  TRUE,  TRUE,   false,   null,            'Avvisning'),
       ('Fordringshaver',   'Inntektsforesporsel',      'Foresporsel',          true,  false, TRUE,  false, TRUE,  TRUE,   false,   null,            'Avvisning')

on conflict (ROLE, SERVICE, ACTION) do update set KRYPTERING   = EXCLUDED.KRYPTERING,
                                                  KOMPRIMERING = EXCLUDED.KOMPRIMERING,
                                                  SIGNERING    = EXCLUDED.SIGNERING,
                                                  INTERNFORMAT = EXCLUDED.INTERNFORMAT,
                                                  VALIDERING   = EXCLUDED.VALIDERING,
                                                  APPREC       = EXCLUDED.APPREC,
                                                  OCSP_CHECK   = EXCLUDED.OCSP_CHECK,
    /* ADAPTER refererer til ekstra custom processering, nesten ingen bruker det. */
                                                  ADAPTER      = EXCLUDED.ADAPTER,
                                                  ERROR_ACTION = EXCLUDED.ERROR_ACTION;
