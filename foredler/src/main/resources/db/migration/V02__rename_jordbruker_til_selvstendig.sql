UPDATE polsepakke
SET yrkesaktivitetidentifikator = 'SELVSTENDIG'
WHERE yrkesaktivitetidentifikator = 'JORDBRUKER';

UPDATE polsepakke_historikk
SET yrkesaktivitetidentifikator = 'SELVSTENDIG'
WHERE yrkesaktivitetidentifikator = 'JORDBRUKER';