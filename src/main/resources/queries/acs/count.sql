WITH parameters AS (SELECT CAST(? AS date) AS cutoff), records AS (
    SELECT s.co_seq_cidadao_nucleo_familiar AS source_id,
           s.co_cidadao,
           s.nu_ine,
           s.nu_cnes,
           s.nu_cbo2002,
           s.dt_ultima_atualizacao,
           s.st_mudou_se,
           CASE WHEN a.matches = 1 THEN a.professional_id END AS professional_id,
           CASE WHEN a.matches = 1 THEN a.team_id END AS team_id,
           CASE WHEN a.matches = 1 THEN a.health_unit_id END AS health_unit_id,
           a.matches AS relationship_match_count,
           (FALSE) AS _invalid,
           (a.matches <> 1) AS _unmatched,
           TRUE AS _eligible
    FROM public.tb_cidadao_nucleo_familiar s
    CROSS JOIN parameters p
    LEFT JOIN LATERAL (
        SELECT count(*) AS matches, min(pr.co_seq_prof) AS professional_id,
               min(e.co_seq_equipe) AS team_id, min(u.co_seq_unidade_saude) AS health_unit_id
        FROM public.tb_prof pr
        JOIN public.tb_lotacao l ON l.co_prof=pr.co_seq_prof
        JOIN public.tb_cbo cbo ON cbo.co_cbo=l.co_cbo AND cbo.co_cbo_2002='515105'
        JOIN public.tb_equipe e ON e.co_seq_equipe=l.co_equipe AND e.nu_ine=s.nu_ine
        JOIN public.tb_unidade_saude u ON u.co_seq_unidade_saude=l.co_unidade_saude
            AND u.nu_cnes=s.nu_cnes AND e.co_unidade_saude=u.co_seq_unidade_saude
        WHERE pr.nu_cns=s.nu_cns_profissional
    ) a ON TRUE
    WHERE s.nu_cbo2002='515105'
)
SELECT count(*) AS source_count,
       count(*) FILTER (WHERE NOT _eligible) AS skipped_count
FROM records;
