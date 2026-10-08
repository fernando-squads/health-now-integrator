WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_familia AS source_id,
           s.co_fat_cad_dom_familia,
           s.co_fat_cad_domiciliar,
           s.co_fat_cidadao,
           s.co_dim_unidade_saude,
           s.co_dim_equipe,
           s.co_dim_tempo,
           s.st_registro_valido,
           s.st_familia_ainda_reside,
           (t.dt_registro IS NULL) AS _invalid,
           (s.co_fat_cad_dom_familia IS NULL) AS _unmatched,
           (t.dt_registro IS NULL OR t.dt_registro <= p.cutoff) AS _eligible
    FROM public.tb_fat_familia s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo=s.co_dim_tempo
    WHERE TRUE
)
SELECT source_id, co_fat_cad_dom_familia, co_fat_cad_domiciliar, co_fat_cidadao, co_dim_unidade_saude, co_dim_equipe, co_dim_tempo, st_registro_valido, st_familia_ainda_reside, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
