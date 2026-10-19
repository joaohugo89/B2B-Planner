package br.com.jhcomp.planner.copy.web;

import br.com.jhcomp.planner.copy.service.CopyEntryService;
import br.com.jhcomp.planner.copy.web.dto.CopyDtos.CopyRequest;
import br.com.jhcomp.planner.copy.web.dto.CopyDtos.CopyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/copies")
@Tag(name = "Guia de Copy", description = "Legendas, hashtags e estratégias de retenção")
public class CopyEntryController {

    private final CopyEntryService service;

    public CopyEntryController(CopyEntryService service) {
        this.service = service;
    }

    @GetMapping
    public List<CopyResponse> list(@RequestParam(required = false) String tone,
                                   @RequestParam(required = false) String postCode) {
        return service.list(tone, postCode).stream().map(CopyResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CopyResponse getById(@PathVariable Long id) {
        return CopyResponse.from(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<CopyResponse> create(@Valid @RequestBody CopyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CopyResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    public CopyResponse update(@PathVariable Long id, @Valid @RequestBody CopyRequest request) {
        return CopyResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
