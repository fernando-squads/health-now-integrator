WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_ator_papel AS source_id, s.co_unico_lotacao,
           s.co_prof, s.co_unidade_saude, s.co_equipe,
           c.co_cbo_2002 AS cbo_code, s.dt_desativacao_lotacao,
           a.st_ativo AS actor_active,
           FALSE AS _invalid,
           (p.co_seq_prof IS NULL OR u.co_seq_unidade_saude IS NULL OR c.co_cbo IS NULL
              OR a.co_seq_ator_papel IS NULL
              OR (s.co_equipe IS NOT NULL AND e.co_seq_equipe IS NULL)) AS _unmatched
    FROM public.tb_lotacao s CROSS JOIN parameters x
    LEFT JOIN public.tb_prof p ON p.co_seq_prof = s.co_prof
    LEFT JOIN public.tb_unidade_saude u ON u.co_seq_unidade_saude = s.co_unidade_saude
    LEFT JOIN public.tb_equipe e ON e.co_seq_equipe = s.co_equipe
    LEFT JOIN public.tb_cbo c ON c.co_cbo = s.co_cbo
    LEFT JOIN public.tb_ator_papel a ON a.co_seq_ator_papel = s.co_ator_papel
)
SELECT source_id, co_unico_lotacao, co_prof, co_unidade_saude, co_equipe,
       cbo_code, dt_desativacao_lotacao, actor_active, _invalid, _unmatched
FROM records WHERE source_id > ? ORDER BY source_id LIMIT ?;
