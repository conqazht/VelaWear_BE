# User Feature Context

Manages application users stored in `users`.

- Passwords are stored only as BCrypt hashes in `password_hash`.
- Delete is implemented as a soft delete by setting `status = DELETED` and `deleted_at`.
- Controllers return DTOs wrapped by `ApiResponse`, never entities.
