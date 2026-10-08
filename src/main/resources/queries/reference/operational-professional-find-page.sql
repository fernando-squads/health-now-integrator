WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_prof AS source_id,
           s.no_civil_profissional,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_prof s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, no_civil_profissional, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
