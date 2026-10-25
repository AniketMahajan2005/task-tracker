package com.axionix.tasktracker.service;

import com.axionix.tasktracker.entity.Project;
import com.axionix.tasktracker.exception.ApiException;
import com.axionix.tasktracker.repository.ProjectMemberRepository;
import com.axionix.tasktracker.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place where "who may touch this project?" is decided.
 * <ul>
 *   <li>unknown project: 404</li>
 *   <li>not a member: 403 (cannot view or modify)</li>
 *   <li>member but not owner, for owner-only actions: 403</li>
 * </ul>
 */
@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    public ProjectAccessService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Project requireMember(Long projectId, Long userId) {
        Project project = findProject(projectId);
        if (!memberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw ApiException.forbidden("You are not a member of this project");
        }
        return project;
    }

    @Transactional(readOnly = true)
    public Project requireOwner(Long projectId, Long userId) {
        Project project = requireMember(projectId, userId);
        if (!project.isOwnedBy(userId)) {
            throw ApiException.forbidden("Only the project owner can perform this action");
        }
        return project;
    }

    public boolean isMember(Long projectId, Long userId) {
        return memberRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project " + projectId + " not found"));
    }
}
