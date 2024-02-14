-- setter 'oppdatert'-tidspunktet automatisk etter alle UPDATEs mot person-tabellen
CREATE  FUNCTION oppdater_oppdatert_tidspunkt()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.oppdatert = now() at time zone 'utc';
    RETURN NEW;
END;
$$ language 'plpgsql';


create table person(
    id bigserial primary key,
    fnr  varchar(32) unique,
    opprettet timestamp with time zone default (now() at time zone 'UTC') not null
);

create table hendelse(
    id bigserial primary key,
    meldingsreferanse_id uuid unique,
    data text not null
);

create table polsepakke(
    person_id bigint references person(id) on delete cascade on update cascade not null,
    yrkesaktivitetidentifikator varchar(32) not null,
    hendelse_id bigint references hendelse(id) on delete restrict not null,
    kilde_id uuid not null,
    data json not null,
    oppdatert timestamp with time zone default (now() at time zone 'UTC') not null
);
CREATE UNIQUE INDEX polsepakke_per_yrkesaktivitet ON polsepakke(person_id,yrkesaktivitetidentifikator);
CREATE INDEX polsepakke_person_id_fk ON polsepakke(person_id);
CREATE INDEX polsepakke_hendelse_id_fk ON polsepakke(hendelse_id);
CREATE INDEX polsepakke_kilde_id_idx ON polsepakke(kilde_id);

CREATE TRIGGER oppdatert_oppdatert_kolonne_trigger
    BEFORE UPDATE ON polsepakke
    FOR EACH ROW
EXECUTE PROCEDURE oppdater_oppdatert_tidspunkt();

create table polsepakke_historikk(
    historikk_id bigserial primary key,
    person_id bigint references person(id) on delete cascade on update cascade not null,
    yrkesaktivitetidentifikator varchar(32) not null,
    hendelse_id bigint references hendelse(id) on delete restrict not null,
    kilde_id uuid not null,
    data text not null,
    opprettet timestamp with time zone
);
CREATE INDEX polsepakke_historikk_person_id_fk ON polsepakke_historikk(person_id);
CREATE INDEX polsepakke_historikk_hendelse_id_fk ON polsepakke_historikk(hendelse_id);
CREATE INDEX polsepakke_historikk_kilde_id_idx ON polsepakke_historikk(kilde_id);