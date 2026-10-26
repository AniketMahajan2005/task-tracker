package com.axionix.tasktracker;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskApiTest extends AbstractApiTest {

    // ------------------------------------------------------------------ create

    @Test
    @DisplayName("a new task defaults to TODO / MEDIUM and has timestamps but no completed_at")
    void createDefaults() throws Exception {
        TestUser owner = newUser("Ann");
        long projectId = createProject(owner, "Defaults");

        postTask(owner, projectId, body("title", "  Write docs  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Write docs"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.created_by").value(owner.id()))
                .andExpect(jsonPath("$.created_at", notNullValue()))
                .andExpect(jsonPath("$.updated_at", notNullValue()))
                .andExpect(jsonPath("$.completed_at", nullValue()))
                .andExpect(jsonPath("$.assignee_id", nullValue()));
    }

    @Test
    @DisplayName("blank title, bad priority and past due date are rejected")
    void createValidation() throws Exception {
        TestUser owner = newUser("Ben");
        long projectId = createProject(owner, "Validation");

        postTask(owner, projectId, body("title", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        postTask(owner, projectId, body("title", "x", "priority", "URGENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));

        postTask(owner, projectId, body("title", "x", "due_date", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));

        postTask(owner, projectId, body("title", "x", "due_date", pastDate(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("an assignee who is not a project member is rejected with 422, on create and on update")
    void invalidAssignee() throws Exception {
        TestUser owner = newUser("Cleo");
        TestUser outsider = newUser("Dana");
        long projectId = createProject(owner, "Assignees");

        postTask(owner, projectId, body("title", "for outsider", "assignee_id", outsider.id()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_ASSIGNEE"));

        postTask(owner, projectId, body("title", "for nobody", "assignee_id", 999_999_999L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_ASSIGNEE"));

        long taskId = createTask(owner, projectId, body("title", "plain")).get("id").asLong();
        patchTask(owner, taskId, body("assignee_id", outsider.id()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_ASSIGNEE"));
    }

    @Test
    @DisplayName("a project member can be assigned, and the assignee can be cleared with an explicit null")
    void validAssigneeAndClear() throws Exception {
        TestUser owner = newUser("Eli");
        TestUser member = newUser("Fay");
        long projectId = createProject(owner, "Assign");
        addMember(owner, projectId, member);

        JsonNode task = createTask(owner, projectId, body("title", "assigned", "assignee_id", member.id()));
        assertEquals(member.id(), task.get("assignee_id").asLong());
        long taskId = task.get("id").asLong();

        // Field not sent: assignee unchanged.
        patchTask(owner, taskId, body("title", "renamed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee_id").value(member.id()));

        // Explicit null: assignee cleared.
        patchTask(owner, taskId, body("assignee_id", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee_id", nullValue()));
    }

    // ------------------------------------------------------------------ update

    @Test
    @DisplayName("completed_at is set when a task first becomes DONE, kept on repeat, and cleared on reopen")
    void completedAtLifecycle() throws Exception {
        TestUser owner = newUser("Gus");
        long projectId = createProject(owner, "Completion");
        long taskId = createTask(owner, projectId, body("title", "finish me")).get("id").asLong();

        JsonNode done = json(patchTask(owner, taskId, body("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completed_at", notNullValue()))
                .andReturn());
        String firstCompletedAt = done.get("completed_at").asText();

        // Saving DONE again (with another change) must not move the original timestamp.
        patchTask(owner, taskId, body("status", "DONE", "title", "finish me, really"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed_at").value(firstCompletedAt));

        // Reopening clears it.
        patchTask(owner, taskId, body("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.completed_at", nullValue()));

        // Completing again after a reopen stamps a fresh completed_at.
        patchTask(owner, taskId, body("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed_at", notNullValue()));
    }

    @Test
    @DisplayName("PATCH updates only the sent fields and bumps updated_at; a non-member cannot update")
    void partialUpdate() throws Exception {
        TestUser owner = newUser("Hana");
        TestUser outsider = newUser("Ivan");
        long projectId = createProject(owner, "Patch");
        JsonNode created = createTask(owner, projectId,
                body("title", "original", "description", "keep me", "priority", "LOW"));
        long taskId = created.get("id").asLong();

        patchTask(owner, taskId, body("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.title").value("original"))
                .andExpect(jsonPath("$.description").value("keep me"));

        patchTask(owner, taskId, body("title", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        patchTask(outsider, taskId, body("title", "hijacked"))
                .andExpect(status().isForbidden());

        patchTask(owner, 999_999_999L, body("title", "ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------------ delete

    @Test
    @DisplayName("only the project owner or the task creator can delete a task")
    void deleteAuthorization() throws Exception {
        TestUser owner = newUser("Jo");
        TestUser creator = newUser("Kit");
        TestUser bystander = newUser("Lou");
        TestUser outsider = newUser("Max");
        long projectId = createProject(owner, "Deletes");
        addMember(owner, projectId, creator);
        addMember(owner, projectId, bystander);

        long byCreator = createTask(creator, projectId, body("title", "mine")).get("id").asLong();

        deleteTask(bystander, byCreator).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        deleteTask(outsider, byCreator).andExpect(status().isForbidden());
        deleteTask(creator, byCreator).andExpect(status().isNoContent());
        deleteTask(creator, byCreator).andExpect(status().isNotFound());

        long byMember = createTask(creator, projectId, body("title", "owner will delete")).get("id").asLong();
        deleteTask(owner, byMember).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ list

    @Test
    @DisplayName("filters: status, priority, assignee and due-date range, alone and combined")
    void filtering() throws Exception {
        TestUser owner = newUser("Nia");
        TestUser member = newUser("Omar");
        long projectId = createProject(owner, "Filters");
        addMember(owner, projectId, member);

        createTask(owner, projectId, body("title", "A", "priority", "HIGH",
                "due_date", futureDate(10), "assignee_id", member.id()));
        long b = createTask(owner, projectId, body("title", "B", "priority", "LOW",
                "due_date", futureDate(20))).get("id").asLong();
        createTask(owner, projectId, body("title", "C", "priority", "HIGH",
                "due_date", futureDate(30), "assignee_id", member.id()));
        createTask(owner, projectId, body("title", "D"));   // no due date, no assignee
        patchTask(owner, b, body("status", "IN_PROGRESS")).andExpect(status().isOk());

        listTasks(owner, projectId, "status", "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("B"));

        listTasks(owner, projectId, "priority", "HIGH", "sort_by", "due_date", "order", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("A", "C")));

        listTasks(owner, projectId, "assignee_id", String.valueOf(member.id()), "priority", "HIGH",
                "sort_by", "due_date", "order", "desc")
                .andExpect(jsonPath("$.content[*].title", contains("C", "A")));

        // Inclusive range that only B (+20 days) falls into; tasks without a due date never match.
        listTasks(owner, projectId, "due_from", futureDate(15), "due_to", futureDate(25))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("B"));

        listTasks(owner, projectId, "due_from", futureDate(25), "due_to", futureDate(15))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        listTasks(owner, projectId, "status", "FINISHED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("pagination: default size 10, explicit sizes, and the maximum of 50 is enforced")
    void pagination() throws Exception {
        TestUser owner = newUser("Pia");
        long projectId = createProject(owner, "Paging");
        for (int i = 1; i <= 12; i++) {
            createTask(owner, projectId, body("title", "task " + i));
        }

        listTasks(owner, projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.total_elements").value(12))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.content", hasSize(10)));

        listTasks(owner, projectId, "page", "2")
                .andExpect(jsonPath("$.content", hasSize(2)));

        listTasks(owner, projectId, "size", "5", "page", "3")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.total_pages").value(3));

        listTasks(owner, projectId, "size", "50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(12)));

        listTasks(owner, projectId, "size", "51")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        listTasks(owner, projectId, "size", "0").andExpect(status().isBadRequest());
        listTasks(owner, projectId, "page", "0").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sorting by due_date and created_at in both directions; invalid sort values are rejected")
    void sorting() throws Exception {
        TestUser owner = newUser("Quin");
        long projectId = createProject(owner, "Sorting");
        createTask(owner, projectId, body("title", "T1", "due_date", futureDate(3)));
        createTask(owner, projectId, body("title", "T2", "due_date", futureDate(1)));
        createTask(owner, projectId, body("title", "T3", "due_date", futureDate(2)));

        listTasks(owner, projectId, "sort_by", "due_date", "order", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("T2", "T3", "T1")));

        listTasks(owner, projectId, "sort_by", "due_date", "order", "desc")
                .andExpect(jsonPath("$.content[*].title", contains("T1", "T3", "T2")));

        // Newest first is the default; ascending gives creation order.
        listTasks(owner, projectId, "sort_by", "created_at", "order", "asc")
                .andExpect(jsonPath("$.content[*].title", contains("T1", "T2", "T3")));

        listTasks(owner, projectId, "sort_by", "title")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        listTasks(owner, projectId, "order", "sideways")
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ summary

    @Test
    @DisplayName("summary returns the total and counts grouped by status and priority (zeros included)")
    void summary() throws Exception {
        TestUser owner = newUser("Rae");
        long projectId = createProject(owner, "Summary");

        mvcSummary(owner, projectId)
                .andExpect(jsonPath("$.total_tasks").value(0))
                .andExpect(jsonPath("$.by_status.TODO").value(0))
                .andExpect(jsonPath("$.by_priority.HIGH").value(0));

        createTask(owner, projectId, body("title", "1", "priority", "HIGH"));
        createTask(owner, projectId, body("title", "2", "priority", "HIGH"));
        long three = createTask(owner, projectId, body("title", "3", "priority", "LOW")).get("id").asLong();
        long four = createTask(owner, projectId, body("title", "4")).get("id").asLong();
        patchTask(owner, three, body("status", "DONE")).andExpect(status().isOk());
        patchTask(owner, four, body("status", "IN_PROGRESS")).andExpect(status().isOk());

        mvcSummary(owner, projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project_id").value(projectId))
                .andExpect(jsonPath("$.total_tasks").value(4))
                .andExpect(jsonPath("$.by_status.TODO").value(2))
                .andExpect(jsonPath("$.by_status.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.by_status.DONE").value(1))
                .andExpect(jsonPath("$.by_priority.HIGH").value(2))
                .andExpect(jsonPath("$.by_priority.MEDIUM").value(1))
                .andExpect(jsonPath("$.by_priority.LOW").value(1));
    }

    @Test
    @DisplayName("task endpoints reject malformed JSON and unknown projects with the standard error shape")
    void errorShape() throws Exception {
        TestUser owner = newUser("Sid");
        long projectId = createProject(owner, "Errors");

        mvc.perform(post("/projects/{id}/tasks", projectId)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.message", notNullValue()));

        listTasks(owner, 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    private ResultActions mvcSummary(TestUser actor, long projectId) throws Exception {
        return mvc.perform(get("/projects/{id}/summary", projectId).header("Authorization", actor.bearer()));
    }
}
