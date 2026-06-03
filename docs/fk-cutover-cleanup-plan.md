# FK cutover cleanup plan

This document tracks the FK cutover across the incremental PRs. PR 1 documented the already-applied FK migrations, PR 2 completed the independent physical-site relation for classes, and PR 3 moves curriculum/manual-load class operations to `class_id` / `meta_group_id` as the only working relation source.

## Scope and assumptions

- Existing SQL migrations and the production backfill are treated as already applied.
- PR 3 is code-only for production data: no new production database migration is required because the production audit confirmed `class_id` and `meta_group_id` are already populated for the relevant rows.
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
| Physical class site | `classroom_leadership_entry` | `school_building_id` | `school_building(id)` | `2026-06-02_classroom_school_building_fk.sql` adds the independent physical-site FK, backfilled by normalized `campus_address` ↔ `school_building.address` without comparing `building_group_id`. |

## Constraints and triggers that currently enforce or synchronize FK state

### Building groups

- `uk_building_group_code` uniquely identifies a logical building group by `building_group.code`.
- `fk_school_building_group`, `fk_classroom_leadership_group`, `fk_curriculum_plan_group`, `fk_manual_load_group`, `fk_meta_group_group`, and `fk_teacher_directory_group` point `building_group_id` to `building_group(id)`.
- `fk_classroom_school_building` points `classroom_leadership_entry.school_building_id` to `school_building(id)` and is intentionally independent from `classroom_leadership_entry.building_group_id`.
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

## Independent classroom ownership and physical site model

Correct business model after the PR 1 review:

- `BuildingGroup` is the class organizational СП / ownership. `classroom_leadership_entry.building_group_id` and legacy `numberSchoolBuilding` describe this organizational СП.
- `SchoolBuilding` is the physical building/site/address. A class owned by `СП1` may physically be placed in a site whose `school_building.code`/group is `СП2` or `СП3`.
- `ClassroomLeadershipEntry` must therefore contain two independent relations: organizational СП through `building_group_id`, and physical placement through `school_building_id`. These relations must not be forced to match.

PR 1 found that no then-existing migration in `scripts/migrations` or `deploy/sql` added a `school_building_id` column, FK, index, or trigger for `classroom_leadership_entry`, `curriculum_plan_entry`, `manual_load_entry`, or `meta_group`. The pre-PR2 JPA entities likewise exposed `building_group_id` relations but did not expose a `school_building_id` relation for classes, curriculum, manual load, or metagroups.

That made `school_building_id` on `classroom_leadership_entry` the only missing FK for physical class placement. PR 2 adds it, resolved from the physical address (`campusAddress` ↔ `school_building.address`) without filtering by the class `building_group_id`; changing the physical site must not change the class СП, and changing the class СП must not automatically choose a physical site.

## Legacy fields still used by the application as relation keys

| Legacy field | Current key-like usage |
| --- | --- |
| `numberSchoolBuilding` / `number_school_building` | Still used as legacy/snapshot/display/export text and as class/metagroup uniqueness/backfill input in old migrations. PR 3 removes it from curriculum/manual-load dependency counts and deletes for ordinary classes; the known 32 curriculum snapshot mismatches are informational and do not block FK-only operations. |
| `campusAddress` / `campus_address` | Current class location snapshot. The working class physical-site relation is now `classroom_leadership_entry.school_building_id`; matching to `school_building.address` remains independent from the class organizational `building_group_id`. |
| `className` / `class_name` | Still used as legacy/snapshot/display/export text and for parsing imported `МГ:` rows before resolving a `MetaGroup`. PR 3 removes it from ordinary class dependency counts/deletes and from metagroup update/delete lookup after a `meta_group_id` exists. |
| `fioTeacher` / `fio_teacher` | Teacher FK sync fallback; classroom/manual display; teacher dismissal/restore still finds manual load by FIO; teacher uniqueness remains `uk_teacher_directory_fio`; service memo and exports use FIO. |
| `subjectName` / `subject_name` | Subject FK sync fallback; curriculum/manual repository lookups; subject rename propagation; import/export and PA/VSOKO display workflows. |
| `subjectAreaName` / `subject_area_name` | Subject-area FK sync fallback; subject-area rename propagation; subject import/export display and validation; repository bulk rename by area text. |

## Services and repositories containing temporary sync or string fallback

- `ClassroomLeadershipRepository.updateBuildingGroupById()` runs native SQL to set `classroom_leadership_entry.building_group_id` by building code.
- `ClassroomLeadershipServiceImpl.syncClassroomBuildingGroups()` calls that native SQL after saving classes.
- PR 3 removes `ClassroomLeadershipServiceImpl.propagateClassRename()`: dependent curriculum/manual-load rows remain linked through `class_id`, so class rename/SP/site edits no longer need mass legacy-text rewrites for relation integrity.
- PR 3 removes `ClassroomLeadershipServiceImpl.syncCurriculumBuildingByClass()` and `syncManualLoadBuildingByClass()`: dependent rows are no longer found by `numberSchoolBuilding + className`, so moving a class СП or physical site does not require rewriting curriculum/manual-load snapshots.
- PR 3 changes `CurriculumPlanEntryRepository.countClassTails()` and `ManualLoadEntryRepository.countClassTails()` to count only `academic_year + class_id`.
- PR 3 removes `CurriculumPlanEntryRepository.renameClassEverywhere()` and `ManualLoadEntryRepository.renameClassEverywhere()` because they only preserved legacy string relation lookup.
- `CurriculumPlanEntryRepository.renameSubjectEverywhere()` and `ManualLoadEntryRepository.renameSubjectEverywhere()` bulk-update dependent rows by subject name string.
- `SubjectCatalogRepository.renameSubjectAreaEverywhere()` bulk-updates subject catalog rows by subject-area name string.
- `ManualLoadEntryRepository.findByFioTeacherIgnoreCase()` and `TeacherDirectoryServiceImpl.markForDismissal()`/`restore()` still use `fioTeacher` to find affected manual-load rows.
- `ManualLoadEntryRepository.findAllByAcademicYearAndBuildingAddress()` and `deleteByAcademicYearAndBuildingAddress()` still filter the concrete site by `ClassroomLeadershipEntry.campusAddress` text.

## Recommended PR sequence after this audit

1. Add a true `school_building_id` FK on `classroom_leadership_entry` for physical placement, independent from the class `building_group_id` СП ownership.
2. Cut class location updates over to the physical-site FK while keeping the temporary native building-group sync workaround only for organizational СП until `building_group_id` itself becomes a writable JPA relation.
3. PR 3 removes class/meta-group string fallback in curriculum/manual load. Ordinary rows use `class_id != null, meta_group_id = null`; explicit `МГ:` rows use `class_id = null, meta_group_id != null`. Existing migrations already provide FK constraints and class/meta-group check constraints, so no new production migration is required for this step.
4. Move teacher workflows to `teacher_id`, keeping `fioTeacher` for display/search/export only.
5. Move subject and subject-area operations to `subject_id`/`subject_area_id`, keeping names only as display/snapshot text.
6. Remove remaining triggers whose only purpose is temporary string/ID synchronization once application code no longer depends on the string side.

## PR 2 update

PR 2 adds `scripts/migrations/2026-06-02_classroom_school_building_fk.sql`, which backfills `classroom_leadership_entry.school_building_id` by normalized physical address only. The migration intentionally does not compare or update `classroom_leadership_entry.building_group_id`, because СП ownership and physical site are independent business facts. Legacy `numberSchoolBuilding` and `campusAddress` remain as compatibility/display snapshots during the transition.


## PR 3 update

PR 3 translates working curriculum/manual-load operations to FK-only class and metagroup relations:

- ordinary class dependency summaries and deletes now resolve the class once and then operate only by `class_id`;
- manual-load template/stat/health generation excludes ordinary curriculum member rows whose subject is moved into a metagroup (`meta_group = true` but no explicit `meta_group_id`) and uses the explicit `МГ:` curriculum row as the load source;
- metagroup update/delete operations use `meta_group_id`;
- the old `class_id OR (number_school_building + class_name)` fallback is removed from repository count queries;
- class rename/SP/site edits no longer call bulk legacy sync methods for curriculum/manual-load rows;
- explicit metagroup manual-load rows carry `meta_group_id` and `class_id = null`, so validation resolves the curriculum rule by FK instead of by ordinary class text;
- the new editable manual-load Excel export includes `CLASS_ID` and `META_GROUP_ID`, while older editable files without those columns are temporarily accepted through a safe deprecated fallback that must resolve exactly one `class_id` or `meta_group_id` before saving.

Legacy `numberSchoolBuilding` and `className` values remain in dependent tables for display, imports, exports, and historical snapshots. The production audit result `curriculum number_school_building does not match building_group.code | 32` is therefore documented as an informational legacy snapshot mismatch, not a blocker for FK-only operations, because `curriculum class_id target does not match legacy class fields | 0` confirmed the FK relation itself is correct. The next planned PR is the teacher workflow cutover to `teacher_id`; this PR intentionally does not change teacher dismissal/restoration, vacancy handling, `subject_id`, or `subject_area_id`.
