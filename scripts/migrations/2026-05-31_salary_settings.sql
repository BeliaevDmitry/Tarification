create table if not exists salary_settings (
    id bigint primary key,
    student_hour_rate numeric(12, 2) not null default 37,
    updated_at timestamp not null default current_timestamp
);

insert into salary_settings (id, student_hour_rate, updated_at)
values (1, 37, current_timestamp)
on conflict (id) do nothing;
