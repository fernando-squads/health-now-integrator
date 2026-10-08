WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_familia_territorio AS source_id,
           s.co_fat_cidadao_pec,
           s.co_fat_cidadao_territorio,
           s.co_fat_cad_domiciliar,
           s.co_dim_municipio,
           s.co_dim_unidade_saude,
           s.co_dim_equipe,
           s.nu_micro_area,
           s.st_familia_consistente,
           s.st_responsavel_vivo,
           (FALSE) AS _invalid,
           (s.co_fat_cidadao_pec IS NULL) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_fat_familia_territorio s
    CROSS JOIN parameters p

    WHERE TRUE
)
SELECT source_id, co_fat_cidadao_pec, co_fat_cidadao_territorio, co_fat_cad_domiciliar, co_dim_municipio, co_dim_unidade_saude, co_dim_equipe, nu_micro_area, st_familia_consistente, st_responsavel_vivo, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
