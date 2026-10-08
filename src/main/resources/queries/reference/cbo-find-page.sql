WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_cbo AS source_id,
           s.nu_cbo,
           s.no_cbo,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_cbo s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_cbo, no_cbo, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
