package com.axionix.tasktracker.dto;

import com.axionix.tasktracker.entity.User;

/** Public view of a user. Deliberately has no password or hash field. */
public record UserResponse(Long id, String name, String email) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
