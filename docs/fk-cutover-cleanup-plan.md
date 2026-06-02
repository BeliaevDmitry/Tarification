# FK cutover cleanup plan

This audit is the first, read-only PR in the FK cutover. It documents the state implied by the already-applied SQL migrations and the current application code before removing any legacy relation fallback.

## Scope and assumptions

- Existing SQL migrations and the production backfill are treated as already applied.
- This PR does not change application logic, run DDL, or run UPDATE/DELETE backfill.
- The migration files are the schema source of truth for this audit.

## FK columns that already exist according to SQL migrations

| Area | Table | FK column | Referenced table | Migration evidence |
| --- | --- | --- | --- | --- |
| Building groups | `school_building` | `building_group_id` | `building_group(id)` | `2026-05-27_full_fk_building_groups.sql` creates `building_group`, adds `school_building.building_group_id`, makes it `NOT NULL`, adds `fk_school_building_group`, and indexes it. |
| Building groups | `classroom_leadership_entry` | `building_group_id` | `building_group(id)` | The same migration adds `building_group_id`, `fk_classroom_leadership_group`, `NOT NULL`, index, and sync trigger. |
| Building groups | `curriculum_plan_entry` | `building_group_id` | `building_group(id)` | The same migration adds `building_group_id`, `fk_curriculum_plan_group`, `NOT NULL`, index, and sync trigger. |
| Building groups | `manual_load_entry` | `building_group_id` | `building_group(id)` | The same migration adds `building_group_id`, `fk_manual_load_group`, `NOT NULL`, index, and sync trigger. |
| Building groups | `meta_group` | `building_group_id` | `building_group(id)` | The same migration adds `building_group_id`, `fk_meta_group_group`, `NOT NULL`, index, and sync trigger. |
| Building groups | `teacher_directory_entry` | `building_group_id` | `building_group(id)` | The same migration adds `building_group_id`, `fk_teacher_directory_group`, `NOT NULL`, index, and sync trigger. |
| Subjects | `curriculum_plan_entry` | `subject_id` | `subject_catalog_entry(id)` | `2026-05-27_subject_teacher_full_fk.sql` adds `subject_id`, `fk_curriculum_subject`, index, `NOT NULL`, and sync/rename triggers. The older deploy SQL has the same intent under differently named constraints. |
| Subjects | `manual_load_entry` | `subject_id` | `subject_catalog_entry(id)` | `2026-05-27_subject_teacher_full_fk.sql` adds `subject_id`, `fk_manual_subject`, index, `NOT NULL`, and sync/rename triggers. |
| Teachers | `classroom_leadership_entry` | `teacher_id` | `teacher_directory_entry(id)` | `2026-05-27_subject_teacher_full_fk.sql` adds `teacher_id`, `fk_classroom_teacher`, index, `NOT NULL`, and sync/rename triggers. |
| Teachers | `manual_load_entry` | `teacher_id` | `teacher_directory_entry(id)` | `2026-05-27_subject_teacher_full_fk.sql` adds `teacher_id`, `fk_manual_teacher`, index, `NOT NULL`, and sync/rename triggers. |
| Classes | `curriculum_plan_entry` | `class_id` | `classroom_leadership_entry(id)` | `2026-05-28_class_full_fk.sql` adds `class_id`, `fk_curriculum_class`, index, and class sync/rename triggers. |
| Classes | `manual_load_entry` | `class_id` | `classroom_leadership_entry(id)` | `2026-05-28_class_full_fk.sql` adds `class_id`, `fk_manual_class`, index, and class sync/rename triggers. |
| Meta-groups | `curriculum_plan_entry` | `meta_group_id` | `meta_group(id)` | `2026-05-28_meta_group_full_fk.sql` adds `meta_group_id`, `fk_curriculum_meta_group`, index, check constraint, and sync/rename triggers; `2026-06-01_meta_group_curriculum_fk_fix.sql` corrects curriculum semantics. |
| Meta-groups | `manual_load_entry` | `meta_group_id` | `meta_group(id)` | `2026-05-28_meta_group_full_fk.sql` adds `meta_group_id`, `fk_manual_meta_group`, index, check constraint, and sync/rename triggers. |
| Subject areas | `subject_catalog_entry` | `subject_area_id` | `subject_area(id)` | `2026-05-30_subject_area_fk.sql` adds `subject_area_id`, makes it `NOT NULL`, adds `fk_subject_catalog_area`, index, and sync/rename triggers. |

## Constraints and triggers that currently enforce or synchronize FK state

### Building groups

- `uk_building_group_code` uniquely identifies a logical building group by `building_group.code`.
- `fk_school_building_group`, `fk_classroom_leadership_group`, `fk_curriculum_plan_group`, `fk_manual_load_group`, `fk_meta_group_group`, and `fk_teacher_directory_group` point `building_group_id` to `building_group(id)`.
- `trg_sync_building_group_fk()` synchronizes legacy `numberSchoolBuilding` with `building_group_id` and is installed as `trg_*_sync_building_group` on `classroom_leadership_entry`, `curriculum_plan_entry`, `manual_load_entry`, `meta_group`, and `teacher_directory_entry`.
- `uk_school_building_code` is explicitly dropped so one logical group code can have multiple `school_building.address` rows.

### Subjects and teachers

- `fk_curriculum_subject`/`fk_manual_subject` enforce `subject_id` references; both `subject_id` columns are set `NOT NULL`.
- `trg_sync_subject_fk()` synchronizes `subject_id` and legacy `subjectName` on curriculum/manual rows.
- `trg_propagate_subject_rename()` pushes `subject_catalog_entry.subjectName` changes into curriculum/manual legacy text.
- `fk_classroom_teacher`/`fk_manual_teacher` enforce `teacher_id` references; both `teacher_id` columns are set `NOT NULL`.
- `trg_sync_teacher_fk()` synchronizes `teacher_id` and legacy `fioTeacher` on classroom/manual rows.
- `trg_propagate_teacher_rename()` pushes `teacher_directory_entry.fioTeacher` changes into classroom/manual legacy text.

### Classes and meta-groups

- `uk_classroom_leadership_class_building` is the class uniqueness constraint: `academic_year + number_school_building + class_name`.
- `fk_curriculum_class` and `fk_manual_class` enforce class references.
- `chk_curriculum_class_id_for_regular_class` requires `class_id` for curriculum rows whose `class_name` is not `МГ:%`.
- `chk_manual_class_id_required` requires `class_id` for manual-load rows whose `class_name` is not `МГ:%`.
- `trg_sync_class_fk()` synchronizes `class_id` with legacy `class_name`/`number_school_building`; `trg_propagate_class_rename()` pushes class renames/moves to dependent legacy fields.
- `uk_meta_group_scope` is the metagroup uniqueness constraint: `number_school_building + parallel + name + class_type`.
- `fk_curriculum_meta_group` and `fk_manual_meta_group` enforce metagroup references.
- `chk_curriculum_meta_group_id_for_meta` requires `meta_group_id` for curriculum rows whose `class_name` is `МГ:%`.
- `chk_manual_meta_group_id_for_meta` requires `meta_group_id` for manual-load rows whose `class_name` is `МГ:%`.
- `2026-06-01_meta_group_curriculum_fk_fix.sql` clarifies that ordinary curriculum rows may be members of a metagroup (`meta_group = true`) but must keep `class_id` and must not use `meta_group_id` unless the row itself is an explicit `МГ:` row.
- `trg_sync_meta_group_fk()` synchronizes `meta_group_id` with legacy metagroup strings; `trg_propagate_meta_group_rename()` pushes metagroup renames/moves to dependent legacy fields.

### Subject areas

- `fk_subject_catalog_area` enforces `subject_catalog_entry.subject_area_id -> subject_area(id)` and `subject_area_id` is `NOT NULL`.
- `chk_subject_area_base_name` constrains `subject_area.name` to the 11 fixed base areas.
- `trg_sync_subject_area_fk()` synchronizes `subject_area_id` and legacy `subjectAreaName`.
- `trg_propagate_subject_area_rename()` pushes subject-area name changes to subject legacy text.

## `school_building_id` status and address logic

No migration in `scripts/migrations` or `deploy/sql` adds a `school_building_id` column, FK, index, or trigger for `classroom_leadership_entry`, `curriculum_plan_entry`, `manual_load_entry`, or `meta_group`. The JPA entities likewise expose `building_group_id` relations but do not expose a `school_building_id` relation for classes, curriculum, manual load, or metagroups.

Current address-level logic is therefore not fully represented by FK. The schema has a FK to the logical building group (`building_group_id`) and keeps the concrete площадка/address in legacy text (`classroom_leadership_entry.campus_address`, matched to `school_building.address` in repository queries). Because `2026-05-27_school_building_group_fk.sql` intentionally allows multiple `school_building` rows per logical code, `building_group_id` alone is not enough to represent a specific address. The only possible missing schema step for address-safe cutover is a real `school_building_id` FK on the class/location-bearing model, with dependent rows resolving the concrete site through their class/metagroup FK rather than through `campusAddress` strings.

## Legacy fields still used by the application as relation keys

| Legacy field | Current key-like usage |
| --- | --- |
| `numberSchoolBuilding` / `number_school_building` | Building-group sync trigger input; class uniqueness; class lookup/backfill; metagroup lookup/backfill; current class save/update requests; classroom repository find/delete methods; curriculum/manual filters; manual load address queries; building deletion/cleanup by code. |
| `campusAddress` / `campus_address` | Current class location snapshot and the only concrete площадка/address discriminator; manual-load repository address queries join manual rows to class rows by `class_id` but still filter class `campusAddress` text against the requested address. |
| `className` / `class_name` | Current class uniqueness and FK sync fallback; curriculum/manual repository lookups and count fallbacks; class rename propagation; metagroup routing by `МГ:` prefix; PA/VSOKO and import/export display workflows. |
| `fioTeacher` / `fio_teacher` | Teacher FK sync fallback; classroom/manual display; teacher dismissal/restore still finds manual load by FIO; teacher uniqueness remains `uk_teacher_directory_fio`; service memo and exports use FIO. |
| `subjectName` / `subject_name` | Subject FK sync fallback; curriculum/manual repository lookups; subject rename propagation; import/export and PA/VSOKO display workflows. |
| `subjectAreaName` / `subject_area_name` | Subject-area FK sync fallback; subject-area rename propagation; subject import/export display and validation; repository bulk rename by area text. |

## Services and repositories containing temporary sync or string fallback

- `ClassroomLeadershipRepository.updateBuildingGroupById()` runs native SQL to set `classroom_leadership_entry.building_group_id` by building code.
- `ClassroomLeadershipServiceImpl.syncClassroomBuildingGroups()` calls that native SQL after saving classes.
- `ClassroomLeadershipServiceImpl.propagateClassRename()` calls repository bulk updates by old/new class names and building strings.
- `ClassroomLeadershipServiceImpl.syncCurriculumBuildingByClass()` and `syncManualLoadBuildingByClass()` manually rewrite dependent rows after class moves.
- `CurriculumPlanEntryRepository.countClassTails()` and `ManualLoadEntryRepository.countClassTails()` count dependencies by `class_id OR (number_school_building + class_name)`, preserving a string fallback.
- `CurriculumPlanEntryRepository.renameClassEverywhere()` and `ManualLoadEntryRepository.renameClassEverywhere()` bulk-update dependent rows by class name string.
- `CurriculumPlanEntryRepository.renameSubjectEverywhere()` and `ManualLoadEntryRepository.renameSubjectEverywhere()` bulk-update dependent rows by subject name string.
- `SubjectCatalogRepository.renameSubjectAreaEverywhere()` bulk-updates subject catalog rows by subject-area name string.
- `ManualLoadEntryRepository.findByFioTeacherIgnoreCase()` and `TeacherDirectoryServiceImpl.markForDismissal()`/`restore()` still use `fioTeacher` to find affected manual-load rows.
- `ManualLoadEntryRepository.findAllByAcademicYearAndBuildingAddress()` and `deleteByAcademicYearAndBuildingAddress()` still filter the concrete site by `ClassroomLeadershipEntry.campusAddress` text.

## Recommended PR sequence after this audit

1. Add a true `school_building_id` FK only if the next PR confirms address-safe class location cannot be modeled with the existing schema. Based on this audit, this is the only missing relation for distinguishing a logical building group from a concrete address.
2. Cut class location updates over to FK relations and remove the native building-group sync workaround.
3. Remove class/meta-group string fallback in curriculum/manual load and keep the regular-row-vs-metagroup-row rule via constraints.
4. Move teacher workflows to `teacher_id`, keeping `fioTeacher` for display/search/export only.
5. Move subject and subject-area operations to `subject_id`/`subject_area_id`, keeping names only as display/snapshot text.
6. Remove remaining triggers whose only purpose is temporary string/ID synchronization once application code no longer depends on the string side.
