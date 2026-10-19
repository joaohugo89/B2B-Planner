package br.com.jhcomp.planner.post.web;

import br.com.jhcomp.planner.post.domain.Audience;
import br.com.jhcomp.planner.post.domain.PostStatus;
import br.com.jhcomp.planner.post.service.PostService;
import br.com.jhcomp.planner.post.web.dto.PostDtos.PlannerMetrics;
import br.com.jhcomp.planner.post.web.dto.PostDtos.PostRequest;
import br.com.jhcomp.planner.post.web.dto.PostDtos.PostResponse;
import br.com.jhcomp.planner.post.web.dto.PostDtos.SlidesRequest;
import br.com.jhcomp.planner.post.web.dto.PostDtos.StatusRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@Tag(name = "Feed Strategy", description = "Posts do Instagram Planner da JH.COMP")
public class PostController {

    private final PostService service;

    public PostController(PostService service) {
        this.service = service;
    }

    @GetMapping
    public List<PostResponse> list(@RequestParam(required = false) PostStatus status,
                                   @RequestParam(required = false) Audience audience) {
        return service.list(status, audience).stream().map(PostResponse::from).toList();
    }

    @GetMapping("/metrics")
    public PlannerMetrics metrics() {
        return service.metrics();
    }

    @GetMapping("/{id}")
    public PostResponse getById(@PathVariable Long id) {
        return PostResponse.from(service.getById(id));
    }

    @GetMapping("/code/{code}")
    public PostResponse getByCode(@PathVariable String code) {
        return PostResponse.from(service.getByCode(code));
    }

    @PostMapping
    public ResponseEntity<PostResponse> create(@Valid @RequestBody PostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    public PostResponse update(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        return PostResponse.from(service.update(id, request));
    }

    @PutMapping("/{id}/slides")
    public PostResponse replaceSlides(@PathVariable Long id, @RequestBody SlidesRequest request) {
        return PostResponse.from(service.replaceSlides(id, request.slides()));
    }

    @PatchMapping("/{id}/status")
    public PostResponse changeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return PostResponse.from(service.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
