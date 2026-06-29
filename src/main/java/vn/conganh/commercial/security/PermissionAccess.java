package vn.conganh.commercial.security;

import java.io.Serializable;

public record PermissionAccess(
        String apiPath,
        String method
) implements Serializable {
}
