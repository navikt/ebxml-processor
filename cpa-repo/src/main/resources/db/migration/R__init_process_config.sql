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
        'LoggEgenandel')
on conflict ("ROLE", "SERVICE", "ACTION") do update set
                          "KRYPTERING"   = EXCLUDED."KRYPTERING",
                          "KOMPRIMERING" = EXCLUDED."KOMPRIMERING",
                          "SIGNERING"    = EXCLUDED."SIGNERING",
                          "INTERNFORMAT" = EXCLUDED."INTERNFORMAT",
                          "VALIDERING"   = EXCLUDED."VALIDERING",
                          "APPREC"       = EXCLUDED."APPREC",
    /* ADAPTER refererer til ekstra custom processering, nesten ingen bruker det. */
                          "ADAPTER"      = EXCLUDED."ADAPTER";
