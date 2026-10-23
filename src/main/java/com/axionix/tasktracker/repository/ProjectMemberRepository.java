package com.axionix.tasktracker.repository;

import com.axionix.tasktracker.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    /** Members with their user loaded in the same query (avoids N+1). */
    @Query("""
            select m from ProjectMember m
            join fetch m.user
            where m.project.id = :projectId
            order by m.joinedAt asc, m.id asc
            """)
    List<ProjectMember> findByProjectIdWithUser(@Param("projectId") Long projectId);
}
