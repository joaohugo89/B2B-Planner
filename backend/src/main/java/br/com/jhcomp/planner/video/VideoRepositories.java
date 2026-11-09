package br.com.jhcomp.planner.video;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface VideoProjectRepository extends JpaRepository<VideoProject, Long> {

    List<VideoProject> findAllByOrderByIdDesc();

    List<VideoProject> findByPostIdOrderByIdDesc(Long postId);
}

interface VideoJobRepository extends JpaRepository<VideoJob, Long> {

    List<VideoJob> findTop15ByProjectIdOrderByIdDesc(Long projectId);

    List<VideoJob> findByStateIn(Collection<VideoJob.State> states);

    boolean existsByProjectIdAndStateIn(Long projectId, Collection<VideoJob.State> states);
}
