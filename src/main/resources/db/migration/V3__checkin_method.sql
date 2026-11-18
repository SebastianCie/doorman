-- Wie wurde eingecheckt: QR = per Scan, MANUAL = Personalien geprüft (ohne QR-Code) bzw. durch Admin gesetzt
alter table checkins add column method varchar(10) not null default 'QR';
update checkins
   set method = 'MANUAL',
       checked_in_by = replace(checked_in_by, ' (manuell)', '')
 where checked_in_by like '% (manuell)';
