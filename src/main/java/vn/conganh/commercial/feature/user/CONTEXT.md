# User Feature Context

Manages application users stored in `users`.

- Passwords are stored only as encoded hashes in the `password` column.
- `gender` is mapped with `UserGender` enum values: `MALE`, `FEMALE`, `OTHER`.
- Email is normalized on create by trimming and lower-casing before duplicate checks and persistence.
- Delete is implemented as a soft delete by setting only `deleted_at`; active reads filter `deleted_at IS NULL`.
- Controllers return DTOs wrapped by `ApiResponse`, never entities.
- `PUT /api/v1/users/me` resolve active user from the authenticated JWT subject in
  `UserService`; the client cannot select `userId`.
- `UpdateMyProfileRequest` contains only `fullName`, `birthDate`, and `gender`.
  Customer self-service cannot mutate `avatar`; the generic operator
  `UpdateUserRequest` keeps `avatar` only for compatibility in the BE-001 expand
  phase.
- Customer order/address `/me` routes must enforce ownership in service/repository
  with owner-qualified queries. Do not load an unscoped entity and authorize only
  in the controller.
- Foreign and missing order ID/code/history or address ID use the same `404`
  response path to avoid ownership enumeration.
- Rollout order is mandatory: **BE-001 → FE-001 → BE-002**. Generic customer
  permissions remain temporarily in BE-001 and are revoked only after FE-001 has
  migrated every caller.
