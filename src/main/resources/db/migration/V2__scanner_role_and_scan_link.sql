-- Rolle "user" heißt jetzt "scanner" und darf nur noch scannen
update app_users set role = 'scanner' where role = 'user';

-- Geheimer Code für den Scan-Link pro Veranstaltung: /s/{scan_code}
alter table events add column scan_code varchar(64);
update events set scan_code = upper(replace(gen_random_uuid()::text, '-', '')) where scan_code is null;
alter table events alter column scan_code set not null;
alter table events add constraint uq_events_scan_code unique (scan_code);
