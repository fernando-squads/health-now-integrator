WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_situacao AS source_id,
           s.nu_identificador,
           s.ds_situacao_problema,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_situacao_problema s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_identificador, ds_situacao_problema, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
