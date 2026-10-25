package com.axionix.tasktracker.service;

import com.axionix.tasktracker.dto.CreateProjectRequest;
import com.axionix.tasktracker.dto.ProjectDetailResponse;
import com.axionix.tasktracker.dto.ProjectResponse;
import com.axionix.tasktracker.dto.UserResponse;
import com.axionix.tasktracker.entity.Project;
import com.axionix.tasktracker.entity.ProjectMember;
import com.axionix.tasktracker.entity.User;
import com.axionix.tasktracker.exception.ApiException;
import com.axionix.tasktracker.exception.ErrorCode;
import com.axionix.tasktracker.repository.ProjectMemberRepository;
import com.axionix.tasktracker.repository.ProjectRepository;
import com.axionix.tasktracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService access;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                          UserRepository userRepository, ProjectAccessService access) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    /** The creator becomes the owner and (in the same transaction) the first member. */
    @Transactional
    public ProjectResponse create(Long userId, CreateProjectRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        Project project = projectRepository.save(
                new Project(request.name().trim(), blankToNull(request.description()), owner));
        memberRepository.save(new ProjectMember(project, owner));
        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listForUser(Long userId) {
        return projectRepository.findAllForMember(userId).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse get(Long userId, Long projectId) {
        Project project = access.requireMember(projectId, userId);
        List<UserResponse> members = memberRepository.findByProjectIdWithUser(projectId).stream()
                .map(m -> UserResponse.from(m.getUser()))
                .toList();
        return ProjectDetailResponse.from(project, members);
    }

    @Transactional
    public UserResponse addMember(Long userId, Long projectId, String email) {
        Project project = access.requireOwner(projectId, userId);
        User newMember = userRepository.findByEmail(AuthService.normalizeEmail(email))
                .orElseThrow(() -> ApiException.notFound("No registered user with email " + email.trim()));
        if (access.isMember(projectId, newMember.getId())) {
            throw new ApiException(ErrorCode.ALREADY_MEMBER, "User is already a member of this project");
        }
        memberRepository.save(new ProjectMember(project, newMember));
        return UserResponse.from(newMember);
    }

    /** Tasks and memberships are removed by the database (ON DELETE CASCADE). */
    @Transactional
    public void delete(Long userId, Long projectId) {
        Project project = access.requireOwner(projectId, userId);
        projectRepository.delete(project);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
