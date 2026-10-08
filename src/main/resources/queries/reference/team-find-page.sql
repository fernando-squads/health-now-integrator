WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_equipe AS source_id,
           s.nu_ine,
           s.no_equipe,
           s.st_registro_valido,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_equipe s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_ine, no_equipe, st_registro_valido, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
