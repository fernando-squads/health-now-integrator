WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_unidade_saude AS source_id,
           s.nu_cnes,
           s.no_unidade_saude,
           s.st_ativo,
           l.co_ibge AS municipality_ibge,
           (FALSE) AS _invalid,
           (FALSE) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_unidade_saude s
    LEFT JOIN public.tb_localidade l ON l.co_localidade = s.co_localidade_endereco
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, nu_cnes, no_unidade_saude, st_ativo, municipality_ibge, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
