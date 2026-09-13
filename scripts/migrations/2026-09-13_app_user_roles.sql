CREATE TABLE IF NOT EXISTS app_user_role (
    user_id BIGINT NOT NULL,
    role_name VARCHAR(32) NOT NULL,
    CONSTRAINT pk_app_user_role PRIMARY KEY (user_id, role_name),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

INSERT INTO app_user_role (user_id, role_name)
SELECT id, role
FROM app_user
WHERE role IS NOT NULL
ON CONFLICT (user_id, role_name) DO NOTHING;

-- Действующим классным руководителям добавляется воспитательная работа.
UPDATE app_user_tab_permission permission
SET can_view = TRUE,
    can_edit = TRUE,
    can_import = TRUE
FROM app_user account
WHERE permission.user_id = account.id
  AND account.role = 'CLASS_TEACHER'
  AND permission.tab_name = 'EDUCATIONAL_WORK';
