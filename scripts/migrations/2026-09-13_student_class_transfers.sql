CREATE TABLE IF NOT EXISTS student_class_transfer_request (
    id BIGSERIAL PRIMARY KEY,
    academic_year VARCHAR(20) NOT NULL,
    student_id BIGINT NOT NULL,
    student_name_snapshot VARCHAR(500) NOT NULL,
    from_class_name VARCHAR(100) NOT NULL,
    target_class_name VARCHAR(100) NOT NULL,
    request_date DATE NOT NULL,
    reason TEXT NOT NULL,
    promised_date DATE,
    promise_note TEXT,
    status VARCHAR(40) NOT NULL,
    completed_date DATE,
    comment_text TEXT,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_student_transfer_student
        FOREIGN KEY (student_id) REFERENCES student_profile (id)
);

CREATE INDEX IF NOT EXISTS idx_student_transfer_year_status
    ON student_class_transfer_request (academic_year, status);
CREATE INDEX IF NOT EXISTS idx_student_transfer_student
    ON student_class_transfer_request (student_id);

CREATE TABLE IF NOT EXISTS student_class_transfer_history (
    id BIGSERIAL PRIMARY KEY,
    transfer_request_id BIGINT NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    details TEXT NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_student_transfer_history_request
        FOREIGN KEY (transfer_request_id) REFERENCES student_class_transfer_request (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_student_transfer_history_request
    ON student_class_transfer_history (transfer_request_id, changed_at);

-- Сохраняем доступ действующих пользователей раздела «Контингент» к новой функции.
INSERT INTO app_user_tab_permission (user_id, tab_name, can_view, can_edit, can_import, can_export)
SELECT user_id, 'CONTINGENT_CLASS_TRANSFERS', can_view, can_edit, FALSE, can_export
FROM app_user_tab_permission
WHERE tab_name = 'CONTINGENT_STATS'
ON CONFLICT (user_id, tab_name) DO NOTHING;

UPDATE app_user_tab_permission permission
SET can_view = TRUE,
    can_edit = account.can_edit
FROM app_user account
WHERE permission.user_id = account.id
  AND permission.tab_name = 'CONTINGENT_CLASS_TRANSFERS'
  AND (account.role = 'SECRETARY' OR EXISTS (
      SELECT 1 FROM app_user_role role
      WHERE role.user_id = account.id AND role.role_name = 'SECRETARY'
  ));
