WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_cidadao_territorio AS source_id,
           s.co_fat_cidadao_pec,
           s.co_dim_municipio,
           s.co_dim_unidade_saude,
           s.co_dim_equipe,
           s.nu_micro_area,
           s.co_fat_familia_territorio,
           s.co_fat_cad_individual,
           s.st_responsavel,
           s.st_mudou_se,
           s.st_vivo,
           (FALSE) AS _invalid,
           (f.co_seq_fat_familia_territorio IS NULL OR c.co_seq_fat_cidadao_pec IS NULL) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_fat_cidadao_territorio s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_fat_familia_territorio f ON f.co_seq_fat_familia_territorio=s.co_fat_familia_territorio
    LEFT JOIN public.tb_fat_cidadao_pec c ON c.co_seq_fat_cidadao_pec=s.co_fat_cidadao_pec
    WHERE TRUE
)
SELECT source_id, co_fat_cidadao_pec, co_dim_municipio, co_dim_unidade_saude, co_dim_equipe, nu_micro_area, co_fat_familia_territorio, co_fat_cad_individual, st_responsavel, st_mudou_se, st_vivo, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
