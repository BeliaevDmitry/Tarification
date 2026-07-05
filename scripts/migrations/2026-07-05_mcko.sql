create table if not exists mcko_import_batch (
    id bigserial primary key,
    file_name varchar(255),
    uploaded_at timestamp not null default now(),
    total_rows integer not null default 0,
    imported_rows integer not null default 0,
    skipped_rows integer not null default 0
);

create table if not exists mcko_certificate (
    id bigserial primary key,
    teacher_id bigint references teacher_directory_entry(id),
    teacher_fio_snapshot varchar(500) not null,
    mcko_subject varchar(500) not null,
    exam_type varchar(500) not null,
    diagnostic_date date not null,
    expires_at date not null,
    level varchar(100) not null,
    published boolean not null default false,
    source varchar(20) not null default 'MANUAL',
    comment text,
    scan_file_name varchar(500),
    scan_content_type varchar(255),
    scan_content bytea,
    import_batch_id bigint references mcko_import_batch(id),
    created_at timestamp not null default now()
);

create index if not exists idx_mcko_certificate_teacher on mcko_certificate(teacher_id);
create index if not exists idx_mcko_certificate_subject on mcko_certificate(mcko_subject);
create index if not exists idx_mcko_certificate_date on mcko_certificate(diagnostic_date);

create table if not exists mcko_subject_mapping (
    id bigserial primary key,
    mcko_subject varchar(500) not null,
    subject_id bigint not null references subject_catalog_entry(id),
    subject_name varchar(500) not null,
    created_at timestamp not null default now(),
    constraint uk_mcko_subject_mapping unique (mcko_subject, subject_id)
);
