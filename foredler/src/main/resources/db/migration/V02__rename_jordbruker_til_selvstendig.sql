DELETE
FROM polsepakke
WHERE person_id IN (SELECT person_id FROM polsepakke WHERE yrkesaktivitetidentifikator = 'JORDBRUKER')
  AND person_id IN (SELECT person_id FROM polsepakke WHERE yrkesaktivitetidentifikator = 'SELVSTENDIG')
  AND yrkesaktivitetidentifikator = 'JORDBRUKER';

UPDATE polsepakke
SET yrkesaktivitetidentifikator = 'SELVSTENDIG'
WHERE yrkesaktivitetidentifikator = 'JORDBRUKER';

UPDATE polsepakke_historikk
SET yrkesaktivitetidentifikator = 'SELVSTENDIG'
WHERE yrkesaktivitetidentifikator = 'JORDBRUKER';