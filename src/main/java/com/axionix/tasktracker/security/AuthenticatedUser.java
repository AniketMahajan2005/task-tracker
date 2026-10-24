package com.axionix.tasktracker.security;

/** The logged-in user as seen by controllers (injected with @AuthenticationPrincipal). */
public record AuthenticatedUser(Long id) {
}
