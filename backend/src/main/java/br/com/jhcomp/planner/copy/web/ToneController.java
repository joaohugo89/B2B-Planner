package br.com.jhcomp.planner.copy.web;

import br.com.jhcomp.planner.common.NotFoundException;
import br.com.jhcomp.planner.copy.domain.Tone;
import br.com.jhcomp.planner.copy.repository.CopyEntryRepository;
import br.com.jhcomp.planner.copy.repository.ToneRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tones")
@Tag(name = "Tons de Copy", description = "Cadastro dos tons usados nas legendas")
public class ToneController {

    public record ToneResponse(Long id, String code, String label, String description, long usageCount) {
    }

    public record ToneRequest(
            @NotBlank @Size(max = 24) String code,
            @NotBlank @Size(max = 60) String label,
            @Size(max = 240) String description) {
    }

    private final ToneRepository tones;
    private final CopyEntryRepository copies;

    public ToneController(ToneRepository tones, CopyEntryRepository copies) {
        this.tones = tones;
        this.copies = copies;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ToneResponse> list() {
        return tones.findAllByOrderByLabelAsc().stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<ToneResponse> create(@Valid @RequestBody ToneRequest request) {
        Tone tone = new Tone(request.code(), request.label());
        tone.setDescription(request.description());

        tones.findByCode(tone.getCode()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um tom com o código " + tone.getCode());
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tones.save(tone)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ToneResponse update(@PathVariable Long id, @Valid @RequestBody ToneRequest request) {
        Tone tone = tones.findById(id).orElseThrow(() -> new NotFoundException("Tom", id));
        String previousCode = tone.getCode();

        tone.setCode(request.code());
        tone.setLabel(request.label());
        tone.setDescription(request.description());

        // Mantém as legendas apontando para o tom mesmo quando o código muda.
        if (!previousCode.equals(tone.getCode())) {
            copies.findByToneOrderByPostCodeAsc(previousCode)
                    .forEach(entry -> entry.setTone(tone.getCode()));
        }

        return toResponse(tones.save(tone));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Tone tone = tones.findById(id).orElseThrow(() -> new NotFoundException("Tom", id));

        long inUse = copies.findByToneOrderByPostCodeAsc(tone.getCode()).size();
        if (inUse > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este tom está em " + inUse + " legenda(s). Troque o tom delas antes de apagar.");
        }

        tones.delete(tone);
        return ResponseEntity.noContent().build();
    }

    private ToneResponse toResponse(Tone tone) {
        long usage = copies.findByToneOrderByPostCodeAsc(tone.getCode()).size();
        return new ToneResponse(tone.getId(), tone.getCode(), tone.getLabel(), tone.getDescription(), usage);
    }
}
