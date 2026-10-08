WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_tipo_atendimento AS source_id,
           s.nu_identificador,
           s.ds_tipo_atendimento,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_tipo_atendimento s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_identificador, ds_tipo_atendimento, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
