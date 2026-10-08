WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_especialidade AS source_id,
           s.co_especialidade,
           s.ds_especialidade,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_especialidade s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, co_especialidade, ds_especialidade, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
