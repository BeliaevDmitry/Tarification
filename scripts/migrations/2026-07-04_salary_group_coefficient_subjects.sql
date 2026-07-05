create table if not exists salary_group_coefficient_subject (
    id bigserial primary key,
    subject_id bigint,
    subject_name varchar(255) not null,
    created_at timestamp not null default now()
);

alter table salary_group_coefficient_subject
    add column if not exists subject_id bigint;

update salary_group_coefficient_subject sgcs
set subject_id = s.id
from subject_catalog_entry s
where sgcs.subject_id is null
  and lower(trim(sgcs.subject_name)) = lower(trim(s.subject_name));

alter table salary_group_coefficient_subject
    drop constraint if exists uk_salary_group_coefficient_subject_name;

alter table salary_group_coefficient_subject
    drop constraint if exists uk_salary_group_coefficient_subject_id;

alter table salary_group_coefficient_subject
    add constraint uk_salary_group_coefficient_subject_id unique (subject_id);

alter table salary_group_coefficient_subject
    drop constraint if exists fk_salary_group_coefficient_subject_subject;

alter table salary_group_coefficient_subject
    add constraint fk_salary_group_coefficient_subject_subject
    foreign key (subject_id) references subject_catalog_entry(id);
