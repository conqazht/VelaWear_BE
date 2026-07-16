# Role Feature Context

Manages RBAC roles stored in `roles`.

- Seeded system roles are created by Flyway in `V2__seed_rbac.sql`.
- Deleting a role is a hard delete and relies on database foreign-key restrictions/cascades.
- Production `USER` uses principal-bound customer routes such as `/me`, self cart,
  self wishlist, self coupon, checkout and verified review creation. Do not re-grant
  generic cart/order/address/payment/review/wishlist mappings removed by V22.
- Generic routes remain for operators. Access is determined by each exact
  `ADMIN`, `MANAGER` or `STAFF` permission mapping; operator roles are not
  interchangeable and do not all receive every generic route.
