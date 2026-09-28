package com.paylane.security;

import com.paylane.user.Role;

/** The authenticated principal placed in the SecurityContext. */
public record AuthUser(Long id, String email, Role role) {
}
