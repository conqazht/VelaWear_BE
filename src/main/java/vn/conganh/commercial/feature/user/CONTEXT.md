# User Feature Context

Manages application users stored in `users`.

- Passwords are stored only as encoded hashes in the `password` column.
- `gender` is mapped with `UserGender` enum values: `MALE`, `FEMALE`, `OTHER`.
- Email is normalized on create by trimming and lower-casing before duplicate checks and persistence.
- Delete is implemented as a soft delete by setting only `deleted_at`; active reads filter `deleted_at IS NULL`.
- Controllers return DTOs wrapped by `ApiResponse`, never entities.
