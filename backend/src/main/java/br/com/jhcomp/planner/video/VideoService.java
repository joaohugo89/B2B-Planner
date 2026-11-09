package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.ai.AiAssistantService;
import br.com.jhcomp.planner.ai.AiDtos.ChatMessage;
import br.com.jhcomp.planner.video.VideoDtos.ProjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/** Projetos de vídeo e os arquivos que o usuário envia ou edita à mão. */
@Service
@Transactional
public class VideoService {

    private final VideoProjectRepository projects;
    private final VideoJobRepository jobs;
    private final VideoJobStore store;
    private final VideoStorage storage;
    private final AiAssistantService ai;

    public VideoService(VideoProjectRepository projects, VideoJobRepository jobs, VideoJobStore store,
                        VideoStorage storage, AiAssistantService ai) {
        this.projects = projects;
        this.jobs = jobs;
        this.store = store;
        this.storage = storage;
        this.ai = ai;
    }

    @Transactional(readOnly = true)
    public List<VideoProject> list(Long postId) {
        return postId == null ? projects.findAllByOrderByIdDesc() : projects.findByPostIdOrderByIdDesc(postId);
    }

    @Transactional(readOnly = true)
    public VideoProject get(Long id) {
        return store.project(id);
    }

    @Transactional(readOnly = true)
    public List<VideoJob> jobs(Long projectId) {
        return jobs.findTop15ByProjectIdOrderByIdDesc(projectId);
    }

    public VideoProject create(ProjectRequest request) {
        VideoProject project = new VideoProject();
        apply(project, request);
        return projects.save(project);
    }

    public VideoProject update(Long id, ProjectRequest request) {
        VideoProject project = get(id);
        apply(project, request);
        return project;
    }

    public void delete(Long id) {
        VideoProject project = idle(id);
        projects.delete(project);
        storage.deleteProject(id);
    }

    /** Vídeo-base ou áudio enviados pelo usuário. As demais peças só nascem de um job. */
    public VideoProject upload(Long id, VideoFile kind, MultipartFile file) {
        if (kind != VideoFile.BASE && kind != VideoFile.AUDIO) {
            throw new IllegalArgumentException("Só o vídeo-base e o áudio podem ser enviados.");
        }
        VideoProject project = idle(id);
        String name = storage.store(id, kind, file);
        project.replace(kind, name).forEach(orfao -> storage.delete(id, orfao));
        return project;
    }

    @Transactional(readOnly = true)
    public String subtitles(Long id) {
        VideoProject project = get(id);
        return project.has(VideoFile.SUBTITLES) ? storage.readText(id, project.file(VideoFile.SUBTITLES)) : "";
    }

    /** Legenda escrita ou corrigida à mão; texto vazio remove a legenda do vídeo. */
    public VideoProject saveSubtitles(Long id, String text) {
        VideoProject project = idle(id);
        String name = text == null || text.isBlank()
                ? null
                : storage.writeText(id, storage.newName(VideoFile.SUBTITLES, "srt"), text);
        project.replace(VideoFile.SUBTITLES, name).forEach(orfao -> storage.delete(id, orfao));
        return project;
    }

    /** Pede o roteiro ao assistente já configurado no painel — Claude, Groq ou um Ollama local. */
    public VideoProject generateScript(Long id, String topic) {
        VideoProject project = get(id);
        project.setScript(ai.chat(List.of(new ChatMessage("user", """
                Escreva o roteiro falado de um vídeo curto (Reels, 45 a 60 segundos) sobre: %s

                Entregue somente o texto que será dito em voz alta, em parágrafos curtos. Sem título,
                sem indicação de cena, sem emojis, sem hashtags e sem marcações como "Narrador:" — o
                texto vai direto para um sintetizador de voz. Comece com um gancho e termine com uma
                chamada para ação.
                """.formatted(topic.trim())))));
        return project;
    }

    @Transactional(readOnly = true)
    public Path file(Long id, VideoFile kind) {
        return storage.existing(id, get(id).file(kind));
    }

    /** Mexer nos arquivos com um motor lendo-os daria um resultado que ninguém pediu. */
    private VideoProject idle(Long id) {
        VideoProject project = get(id);
        if (store.hasActive(id)) {
            throw new IllegalArgumentException("Há uma etapa em andamento neste projeto. Espere ou cancele.");
        }
        return project;
    }

    private void apply(VideoProject project, ProjectRequest request) {
        project.setTitle(request.title().trim());
        project.setScript(request.script());
        project.setPostId(request.postId());
    }
}
