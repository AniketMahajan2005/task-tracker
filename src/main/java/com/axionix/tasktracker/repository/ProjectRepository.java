package com.axionix.tasktracker.repository;

import com.axionix.tasktracker.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** All projects the user is a member of (the owner is always a member). */
    @Query("""
            select p from Project p
            where p.id in (select m.project.id from ProjectMember m where m.user.id = :userId)
            order by p.createdAt desc, p.id desc
            """)
    List<Project> findAllForMember(@Param("userId") Long userId);
}
