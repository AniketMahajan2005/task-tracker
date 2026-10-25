package com.axionix.tasktracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectApiTest extends AbstractApiTest {

    @Test
    @DisplayName("the creator becomes the owner and a member of the new project")
    void creatorBecomesOwnerAndMember() throws Exception {
        TestUser owner = newUser("Olivia");
        long projectId = createProject(owner, "Website");

        getProject(owner, projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Website"))
                .andExpect(jsonPath("$.owner_id").value(owner.id()))
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].id").value(owner.id()));
    }

    @Test
    @DisplayName("blank project name is rejected")
    void blankProjectName() throws Exception {
        TestUser owner = newUser("Blanca");

        mvc.perform(post("/projects")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a non-member can neither view nor modify a project (403)")
    void nonMemberIsForbidden() throws Exception {
        TestUser owner = newUser("Owen");
        TestUser outsider = newUser("Oscar");
        long projectId = createProject(owner, "Private");

        getProject(outsider, projectId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        postTask(outsider, projectId, body("title", "sneaky"))
                .andExpect(status().isForbidden());

        listTasks(outsider, projectId)
                .andExpect(status().isForbidden());

        mvc.perform(get("/projects/{id}/summary", projectId).header("Authorization", outsider.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("only the owner can add members; unknown email is 404, repeat add is 409")
    void onlyOwnerAddsMembers() throws Exception {
        TestUser owner = newUser("Pat");
        TestUser member = newUser("Quinn");
        TestUser other = newUser("Riley");
        long projectId = createProject(owner, "Team");

        postAddMember(owner, projectId, member.email().toUpperCase())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(member.id()));

        // A plain member may not add anyone.
        postAddMember(member, projectId, other.email())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        postAddMember(owner, projectId, uniqueEmail("ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        postAddMember(owner, projectId, member.email())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_MEMBER"));

        // The new member can now see the project.
        getProject(member, projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members", hasSize(2)));
    }

    @Test
    @DisplayName("project list only contains projects the user is a member of")
    void listOnlyOwnProjects() throws Exception {
        TestUser a = newUser("Sam");
        TestUser b = newUser("Tina");
        long aProject = createProject(a, "A-only");
        long sharedProject = createProject(a, "Shared");
        long bProject = createProject(b, "B-only");
        addMember(a, sharedProject, b);

        mvc.perform(get("/projects").header("Authorization", b.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) bProject)))
                .andExpect(jsonPath("$[*].id", hasItem((int) sharedProject)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) aProject))));
    }

    @Test
    @DisplayName("only the owner can delete a project, and its tasks go with it")
    void onlyOwnerDeletesProject() throws Exception {
        TestUser owner = newUser("Uma");
        TestUser member = newUser("Victor");
        long projectId = createProject(owner, "Doomed");
        addMember(owner, projectId, member);
        long taskId = createTask(owner, projectId, body("title", "will vanish")).get("id").asLong();

        mvc.perform(delete("/projects/{id}", projectId).header("Authorization", member.bearer()))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/projects/{id}", projectId).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());

        getProject(owner, projectId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        patchTask(owner, taskId, body("title", "still there?"))
                .andExpect(status().isNotFound());
    }
}
