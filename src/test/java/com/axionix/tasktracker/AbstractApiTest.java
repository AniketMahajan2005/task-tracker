package com.axionix.tasktracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers. Tests go through the real HTTP layer (security filter, controllers,
 * services, JPA, Flyway-created schema) and only the database is swapped for in-memory H2.
 * Every test registers its own users with unique emails, so tests never depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractApiTest {

    protected static final String PASSWORD = "Password123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** A registered, logged-in user. */
    protected record TestUser(long id, String email, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    // ------------------------------------------------------------- building blocks

    /** Builds a JSON body; unlike Map.of it allows null values (needed to test PATCH with explicit null). */
    protected static Map<String, Object> body(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    protected static String futureDate(int daysFromNow) {
        return Instant.now().plus(daysFromNow, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    protected static String pastDate(int daysAgo) {
        return Instant.now().minus(daysAgo, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------- users

    protected ResultActions postRegister(String name, String email, String password) throws Exception {
        return mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body("name", name, "email", email, "password", password))));
    }

    protected ResultActions postLogin(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body("email", email, "password", password))));
    }

    protected TestUser newUser(String name) throws Exception {
        String email = uniqueEmail(name.toLowerCase());
        long id = json(postRegister(name, email, PASSWORD).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        String token = json(postLogin(email, PASSWORD).andExpect(status().isOk()).andReturn()).get("access_token").asText();
        return new TestUser(id, email, token);
    }

    // ------------------------------------------------------------- projects

    protected long createProject(TestUser owner, String name) throws Exception {
        MvcResult result = mvc.perform(post("/projects")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body("name", name, "description", "test project"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asLong();
    }

    protected ResultActions postAddMember(TestUser actor, long projectId, String email) throws Exception {
        return mvc.perform(post("/projects/{id}/members", projectId)
                .header("Authorization", actor.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body("email", email))));
    }

    protected void addMember(TestUser owner, long projectId, TestUser member) throws Exception {
        postAddMember(owner, projectId, member.email()).andExpect(status().isCreated());
    }

    protected ResultActions getProject(TestUser actor, long projectId) throws Exception {
        return mvc.perform(get("/projects/{id}", projectId).header("Authorization", actor.bearer()));
    }

    // ------------------------------------------------------------- tasks

    protected ResultActions postTask(TestUser actor, long projectId, Map<String, Object> payload) throws Exception {
        return mvc.perform(post("/projects/{id}/tasks", projectId)
                .header("Authorization", actor.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    /** Creates a task and returns the response JSON. Fails the test unless the API answers 201. */
    protected JsonNode createTask(TestUser actor, long projectId, Map<String, Object> payload) throws Exception {
        return json(postTask(actor, projectId, payload).andExpect(status().isCreated()).andReturn());
    }

    protected ResultActions patchTask(TestUser actor, long taskId, Map<String, Object> payload) throws Exception {
        return mvc.perform(patch("/tasks/{id}", taskId)
                .header("Authorization", actor.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    protected ResultActions deleteTask(TestUser actor, long taskId) throws Exception {
        return mvc.perform(delete("/tasks/{id}", taskId).header("Authorization", actor.bearer()));
    }

    protected ResultActions listTasks(TestUser actor, long projectId, String... paramNamesAndValues) throws Exception {
        var request = get("/projects/{id}/tasks", projectId).header("Authorization", actor.bearer());
        for (int i = 0; i < paramNamesAndValues.length; i += 2) {
            request = request.param(paramNamesAndValues[i], paramNamesAndValues[i + 1]);
        }
        return mvc.perform(request);
    }
}
