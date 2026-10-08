WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_unidade_saude AS source_id,
           s.nu_cnes,
           s.no_unidade_saude,
           s.st_registro_valido,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_unidade_saude s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_cnes, no_unidade_saude, st_registro_valido, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
