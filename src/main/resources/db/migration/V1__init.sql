create table app_users (
    id            bigserial primary key,
    username      varchar(100) not null unique,
    password_hash varchar(255) not null,
    role          varchar(20)  not null,
    created_at    timestamptz  not null default now()
);

create table events (
    id          bigserial primary key,
    name        varchar(200) not null,
    event_date  date,
    description text,
    created_at  timestamptz  not null default now()
);

-- Check-in-Punkte einer Veranstaltung, z. B. "Einlass" oder "Hinfahrt" / "Rückfahrt"
create table checkpoints (
    id         bigserial primary key,
    event_id   bigint       not null references events (id) on delete cascade,
    name       varchar(100) not null,
    sort_order int          not null default 0
);
create index idx_checkpoints_event on checkpoints (event_id);

create table participants (
    id         bigserial primary key,
    event_id   bigint      not null references events (id) on delete cascade,
    first_name varchar(100),
    last_name  varchar(100),
    email      varchar(255),
    note       text,
    token      varchar(64) not null unique,
    sent_at    timestamptz, -- QR-Code verschickt (manuell abgehakt)
    created_at timestamptz not null default now()
);
create index idx_participants_event on participants (event_id);

create table checkins (
    id             bigserial primary key,
    participant_id bigint      not null references participants (id) on delete cascade,
    checkpoint_id  bigint      not null references checkpoints (id) on delete cascade,
    checked_in_at  timestamptz not null default now(),
    checked_in_by  varchar(100),
    constraint uq_checkin unique (participant_id, checkpoint_id)
);
create index idx_checkins_checkpoint on checkins (checkpoint_id);
