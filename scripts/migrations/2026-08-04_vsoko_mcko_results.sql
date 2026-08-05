-- ВСОКО / МЦКО: накопительные результаты, журнал файлов и закрепление педагогов.
create table if not exists vsoko_mcko_import_batch (
    id bigserial primary key,
    academic_year varchar(20),
    uploaded_by varchar(255),
    uploaded_at timestamp not null default now(),
    files_total integer not null default 0,
    files_processed integer not null default 0,
    files_failed integer not null default 0,
    rows_imported integer not null default 0
);

create table if not exists vsoko_mcko_import_file (
    id bigserial primary key,
    batch_id bigint not null references vsoko_mcko_import_batch(id) on delete cascade,
    file_name varchar(1000) not null,
    content_type varchar(255),
    file_size bigint not null default 0,
    file_kind varchar(80),
    status varchar(30) not null default 'PROCESSING',
    reason varchar(4000),
    total_rows integer not null default 0,
    imported_rows integer not null default 0,
    skipped_rows integer not null default 0,
    processed_at timestamp
);

create index if not exists idx_vsoko_mcko_import_file_batch on vsoko_mcko_import_file(batch_id);
create index if not exists idx_vsoko_mcko_import_file_status on vsoko_mcko_import_file(status);

create table if not exists vsoko_mcko_teacher_class_assignment (
    id bigserial primary key,
    academic_year varchar(20) not null,
    class_name varchar(100) not null,
    subject_name varchar(500) not null,
    teacher_id bigint not null references teacher_directory_entry(id),
    teacher_fio_snapshot varchar(500) not null,
    updated_at timestamp not null default now(),
    constraint uk_vsoko_mcko_teacher_assignment unique (academic_year, class_name, subject_name)
);

create index if not exists idx_vsoko_mcko_assignment_teacher on vsoko_mcko_teacher_class_assignment(teacher_id);
create index if not exists idx_vsoko_mcko_assignment_year on vsoko_mcko_teacher_class_assignment(academic_year);

create table if not exists vsoko_mcko_result (
    id bigserial primary key,
    student_id bigint references student_profile(id),
    student_fio_snapshot varchar(500),
    student_code varchar(100),
    student_link_status varchar(40) not null default 'NOT_FOUND',
    student_link_message varchar(1000),
    class_name varchar(100),
    subject_name varchar(500) not null,
    diagnostic_date date,
    academic_year varchar(20) not null,
    school_name varchar(500),
    class_level varchar(100),
    city_level varchar(100),
    parallel_no integer,
    class_letter varchar(20),
    variant_name varchar(100),
    score double precision,
    percent_value double precision,
    mark integer,
    student_number integer,
    task_scores_json text,
    result_type varchar(40) not null default 'STANDARD',
    mastery_level varchar(100),
    section_1_percent double precision,
    section_2_percent double precision,
    section_3_percent double precision,
    teacher_id bigint references teacher_directory_entry(id),
    teacher_fio_snapshot varchar(500),
    source_file_id bigint references vsoko_mcko_import_file(id),
    source_row integer,
    fingerprint varchar(64) not null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now(),
    constraint uk_vsoko_mcko_result_fingerprint unique (fingerprint)
);

create index if not exists idx_vsoko_mcko_result_student on vsoko_mcko_result(student_id);
create index if not exists idx_vsoko_mcko_result_year_class on vsoko_mcko_result(academic_year, class_name);
create index if not exists idx_vsoko_mcko_result_subject on vsoko_mcko_result(subject_name);
create index if not exists idx_vsoko_mcko_result_teacher on vsoko_mcko_result(teacher_id);
create index if not exists idx_vsoko_mcko_result_link on vsoko_mcko_result(student_link_status);

alter table if exists pa_report_student_result add column if not exists student_id bigint;
alter table if exists pa_report_student_result add column if not exists student_link_status varchar(50);
alter table if exists pa_report_student_result add column if not exists student_link_message varchar(1000);
create index if not exists idx_pa_student_result_student on pa_report_student_result(student_id);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_pa_student_result_student') then
        alter table pa_report_student_result
            add constraint fk_pa_student_result_student foreign key (student_id) references student_profile(id);
    end if;
end $$;

alter table if exists oge_work_result add column if not exists student_id bigint;
create index if not exists idx_oge_work_result_student on oge_work_result(student_id);

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_oge_work_result_student') then
        alter table oge_work_result
            add constraint fk_oge_work_result_student foreign key (student_id) references student_profile(id);
    end if;
end $$;
