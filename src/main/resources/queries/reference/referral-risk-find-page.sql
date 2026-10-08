WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_classific_risc_enc AS source_id,
           s.nu_identificador,
           s.co_classificacao_risco,
           s.no_classificacao_risco,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_classificacao_risc_enc s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_identificador, co_classificacao_risco, no_classificacao_risco, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
