package br.com.jhcomp.planner.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class VideoDtos {

    private VideoDtos() {
    }

    public record ProjectRequest(@NotBlank @Size(max = 180) String title, String script, Long postId) {
    }

    public record ScriptRequest(@NotBlank @Size(max = 600) String topic) {
    }

    public record SubtitlesPayload(String text) {
    }

    public record JobRequest(@NotNull VideoJob.Step step) {
    }

    public record JobResponse(Long id, VideoJob.Step step, VideoJob.State state, String error, String log,
                              Instant createdAt, Instant startedAt, Instant finishedAt) {
        public static JobResponse from(VideoJob job) {
            return new JobResponse(job.getId(), job.getStep(), job.getState(), job.getError(), job.getLog(),
                    job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
        }
    }

    /** Os arquivos saem como "tem ou não tem": o nome no disco não interessa à tela. */
    public record ProjectResponse(Long id, String title, String script, Long postId,
                                  boolean hasBase, boolean hasAudio, boolean hasLipsync,
                                  boolean hasSubtitles, boolean hasFinal,
                                  Instant updatedAt, List<JobResponse> jobs) {
        public static ProjectResponse from(VideoProject project, List<VideoJob> jobs) {
            return new ProjectResponse(project.getId(), project.getTitle(), project.getScript(),
                    project.getPostId(),
                    project.has(VideoFile.BASE), project.has(VideoFile.AUDIO), project.has(VideoFile.LIPSYNC),
                    project.has(VideoFile.SUBTITLES), project.has(VideoFile.FINAL),
                    project.getUpdatedAt(), jobs.stream().map(JobResponse::from).toList());
        }
    }
}
