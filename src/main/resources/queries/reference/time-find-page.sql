WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_tempo AS source_id,
           s.dt_registro,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_tempo s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, dt_registro, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
