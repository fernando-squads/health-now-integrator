WITH parameters AS (SELECT CAST(? AS date) AS cutoff)
SELECT count(*) AS source_count, 0::bigint AS skipped_count
FROM public.tb_lotacao s CROSS JOIN parameters p;
