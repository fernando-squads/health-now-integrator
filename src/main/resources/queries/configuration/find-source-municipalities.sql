-- Preserve missing links so validation cannot silently ignore an unmapped unit.
SELECT DISTINCT l.co_ibge AS ibge_code, l.no_localidade AS name
FROM public.tb_unidade_saude u
LEFT JOIN public.tb_localidade l ON l.co_localidade = u.co_localidade_endereco
ORDER BY ibge_code, name;
