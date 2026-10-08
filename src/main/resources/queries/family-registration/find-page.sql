WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_cad_dom_familia AS source_id,
           s.co_fat_cidadao_pec,
           s.co_fat_cad_domiciliar,
           s.co_dim_unidade_saude,
           s.co_dim_equipe,
           s.co_dim_tempo,
           s.nu_micro_area,
           s.qt_membro_familiar,
           s.st_mudou,
           (t.dt_registro IS NULL) AS _invalid,
           (s.co_fat_cidadao_pec IS NULL) AS _unmatched,
           (t.dt_registro IS NULL OR t.dt_registro <= p.cutoff) AS _eligible
    FROM public.tb_fat_cad_dom_familia s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo=s.co_dim_tempo
    WHERE TRUE
)
SELECT source_id, co_fat_cidadao_pec, co_fat_cad_domiciliar, co_dim_unidade_saude, co_dim_equipe, co_dim_tempo, nu_micro_area, qt_membro_familiar, st_mudou, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
