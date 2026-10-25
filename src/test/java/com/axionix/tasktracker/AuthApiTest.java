package com.axionix.tasktracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends AbstractApiTest {

    @Test
    @DisplayName("register returns the new user and never exposes the password or its hash")
    void registerHidesPassword() throws Exception {
        String email = uniqueEmail("alice");

        postRegister("Alice", email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("duplicate email is rejected with 409, ignoring letter case")
    void duplicateEmailIgnoringCase() throws Exception {
        String lower = uniqueEmail("dup");
        postRegister("First", lower, PASSWORD).andExpect(status().isCreated());

        postRegister("Second", lower.toUpperCase(), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("invalid email and short password are rejected with field-level details")
    void registerValidation() throws Exception {
        postRegister("Bob", "not-an-email", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details", hasSize(2)));

        postRegister("   ", uniqueEmail("blank"), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("successful login returns a bearer token (email is case-insensitive)")
    void loginSucceeds() throws Exception {
        String email = uniqueEmail("carol");
        postRegister("Carol", email, PASSWORD).andExpect(status().isCreated());

        postLogin(email.toUpperCase(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", not(emptyString())))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.password_hash").doesNotExist());
    }

    @Test
    @DisplayName("failed login: wrong password and unknown email give the same 401")
    void loginFails() throws Exception {
        String email = uniqueEmail("dave");
        postRegister("Dave", email, PASSWORD).andExpect(status().isCreated());

        postLogin(email, "WrongPassword1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        postLogin(uniqueEmail("nobody"), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("protected endpoint without a token returns 401 MISSING_TOKEN")
    void missingToken() throws Exception {
        mvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("MISSING_TOKEN"));
    }

    @Test
    @DisplayName("protected endpoint with a garbage token returns 401 INVALID_TOKEN")
    void invalidToken() throws Exception {
        mvc.perform(get("/projects").header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }
}
