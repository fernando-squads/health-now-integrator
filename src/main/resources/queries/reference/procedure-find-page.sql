WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_procedimento AS source_id,
           s.co_proced,
           s.ds_proced,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_procedimento s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, co_proced, ds_proced, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
