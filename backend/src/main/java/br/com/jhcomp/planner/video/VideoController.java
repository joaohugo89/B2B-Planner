package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.video.VideoDtos.JobRequest;
import br.com.jhcomp.planner.video.VideoDtos.JobResponse;
import br.com.jhcomp.planner.video.VideoDtos.ProjectRequest;
import br.com.jhcomp.planner.video.VideoDtos.ProjectResponse;
import br.com.jhcomp.planner.video.VideoDtos.ScriptRequest;
import br.com.jhcomp.planner.video.VideoDtos.SubtitlesPayload;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Video Studio. Tudo aqui exige login — inclusive os arquivos: ao contrário das artes do
 * feed, vídeo de rosto e voz não tem endereço público.
 */
@RestController
@RequestMapping("/api/video")
@Tag(name = "Video Studio", description = "Projetos de vídeo e as etapas de produção")
public class VideoController {

    private final VideoService service;
    private final VideoPipeline pipeline;

    public VideoController(VideoService service, VideoPipeline pipeline) {
        this.service = service;
        this.pipeline = pipeline;
    }

    @GetMapping("/engines")
    public Map<VideoJob.Step, Boolean> engines() {
        return pipeline.engines();
    }

    @GetMapping("/projects")
    public List<ProjectResponse> list(@RequestParam(required = false) Long postId) {
        // A lista não carrega os jobs: quem precisa deles abre o projeto.
        return service.list(postId).stream()
                .map(project -> ProjectResponse.from(project, List.of(), pipeline::progress)).toList();
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(service.create(request), List.of(), pipeline::progress));
    }

    @GetMapping("/projects/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return response(service.get(id));
    }

    @PutMapping("/projects/{id}")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return response(service.update(id, request));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{id}/script")
    public ProjectResponse script(@PathVariable Long id, @Valid @RequestBody ScriptRequest request) {
        return response(service.generateScript(id, request.topic()));
    }

    @PostMapping(value = "/projects/{id}/files/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectResponse upload(@PathVariable Long id, @PathVariable VideoFile kind,
                                  @RequestPart("file") MultipartFile file) {
        return response(service.upload(id, kind, file));
    }

    @GetMapping("/projects/{id}/files/{kind}")
    public ResponseEntity<Resource> file(@PathVariable Long id, @PathVariable VideoFile kind) {
        Path path = service.file(id, kind);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                // O nome é gerado pelo backend (prefixo + UUID), então pode ir direto no cabeçalho.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }

    @GetMapping("/projects/{id}/subtitles")
    public SubtitlesPayload subtitles(@PathVariable Long id) {
        return new SubtitlesPayload(service.subtitles(id));
    }

    @PutMapping("/projects/{id}/subtitles")
    public ProjectResponse saveSubtitles(@PathVariable Long id, @RequestBody SubtitlesPayload payload) {
        return response(service.saveSubtitles(id, payload.text()));
    }

    @PostMapping("/projects/{id}/jobs")
    public ResponseEntity<JobResponse> submit(@PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(JobResponse.from(pipeline.submit(id, request.step()), null));
    }

    /** Roda todas as etapas configuradas em sequência; devolve o job da primeira. */
    @PostMapping("/projects/{id}/produce")
    public ResponseEntity<JobResponse> produce(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(JobResponse.from(pipeline.produce(id), null));
    }

    @GetMapping("/avatar")
    public Map<String, Boolean> avatar() {
        return Map.of("saved", service.hasAvatar());
    }

    /** Guarda o vídeo-base deste projeto como avatar: os próximos projetos já nascem com ele. */
    @PostMapping("/projects/{id}/avatar")
    public ResponseEntity<Void> saveAvatar(@PathVariable Long id) {
        service.saveAvatar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long jobId) {
        pipeline.cancel(jobId);
        return ResponseEntity.noContent().build();
    }

    private ProjectResponse response(VideoProject project) {
        return ProjectResponse.from(project, service.jobs(project.getId()), pipeline::progress);
    }
}
