WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_fat_atend_ind_proced AS source_id,
           s.co_fat_atd_ind,
           s.co_fat_cidadao_pec,
           s.co_dim_tempo,
           s.co_dim_profissional_1,
           s.co_dim_profissional_2,
           s.co_dim_unidade_saude_1,
           s.co_dim_unidade_saude_2,
           s.co_dim_equipe_1,
           s.co_dim_equipe_2,
           s.co_dim_cbo_1,
           s.co_dim_cbo_2,
           s.co_dim_procedimento_avaliado,
           s.co_dim_procedimento_solicitado,
           (t.dt_registro IS NULL) AS _invalid,
           (a.co_seq_fat_atd_ind IS NULL OR c.co_seq_fat_cidadao_pec IS NULL) AS _unmatched,
           (COALESCE(pt.dt_registro, t.dt_registro) IS NULL OR COALESCE(pt.dt_registro, t.dt_registro) <= p.cutoff) AS _eligible
    FROM public.tb_fat_atd_ind_procedimentos s
    CROSS JOIN parameters p
    LEFT JOIN public.tb_fat_atendimento_individual a ON a.co_seq_fat_atd_ind=s.co_fat_atd_ind
    LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo=s.co_dim_tempo
    LEFT JOIN public.tb_dim_tempo pt ON pt.co_seq_dim_tempo=a.co_dim_tempo
    LEFT JOIN public.tb_fat_cidadao_pec c ON c.co_seq_fat_cidadao_pec=s.co_fat_cidadao_pec
    WHERE TRUE
)
SELECT source_id, co_fat_atd_ind, co_fat_cidadao_pec, co_dim_tempo, co_dim_profissional_1, co_dim_profissional_2, co_dim_unidade_saude_1, co_dim_unidade_saude_2, co_dim_equipe_1, co_dim_equipe_2, co_dim_cbo_1, co_dim_cbo_2, co_dim_procedimento_avaliado, co_dim_procedimento_solicitado, _invalid, _unmatched
FROM records
WHERE _eligible AND source_id > ?
ORDER BY source_id
LIMIT ?;
