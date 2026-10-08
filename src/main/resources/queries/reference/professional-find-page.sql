WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_dim_profissional AS source_id,
           s.no_profissional,
           CASE WHEN s.nu_cns ~ '^[0-9]{15}$' THEN s.nu_cns END AS cns,
           s.st_registro_valido,
           ((s.nu_cns IS NOT NULL AND s.nu_cns !~ '^[0-9]{15}$')) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_dim_profissional s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, no_profissional, cns, st_registro_valido, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
