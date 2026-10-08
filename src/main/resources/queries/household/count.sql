WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_cad_domiciliar AS source_id, s.nu_uuid_ficha, s.nu_uuid_ficha_origem, s.co_dim_unidade_saude, s.co_dim_equipe, s.co_dim_tempo, s.nu_micro_area, s.st_ativo,
           (t.dt_registro IS NULL) AS _invalid,
           FALSE AS _unmatched,
           (t.dt_registro IS NULL OR t.dt_registro <= p.cutoff) AS _eligible
    FROM public.tb_fat_cad_domiciliar s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo=s.co_dim_tempo
)
SELECT count(*) AS source_count, count(*) FILTER (WHERE NOT _eligible) AS skipped_count FROM records;
