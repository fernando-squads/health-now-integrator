WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_equipe AS source_id,
           s.nu_ine,
           s.no_equipe,
           s.co_unidade_saude,
           s.st_ativo,
           s.co_unico_equipe,
           s.ds_area,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_equipe s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_ine, no_equipe, co_unidade_saude, st_ativo, co_unico_equipe, ds_area, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
