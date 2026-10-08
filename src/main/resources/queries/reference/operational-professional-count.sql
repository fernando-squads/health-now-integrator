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
SELECT count(*) AS source_count,
       count(*) FILTER (WHERE NOT _eligible) AS skipped_count
FROM records;
