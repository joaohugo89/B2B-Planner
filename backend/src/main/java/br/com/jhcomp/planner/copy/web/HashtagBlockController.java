package br.com.jhcomp.planner.copy.web;

import br.com.jhcomp.planner.common.NotFoundException;
import br.com.jhcomp.planner.copy.domain.HashtagBlock;
import br.com.jhcomp.planner.copy.repository.HashtagBlockRepository;
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

/** Cadastro dos blocos de hashtags por categoria usados no Guia de Copy. */
@RestController
@RequestMapping("/api/hashtag-blocks")
@Tag(name = "Blocos de hashtags", description = "Conjuntos de hashtags por categoria")
public class HashtagBlockController {

    public record BlockResponse(Long id, String name, String hashtags, int tagCount) {

        public static BlockResponse from(HashtagBlock block) {
            return new BlockResponse(block.getId(), block.getName(), block.getHashtags(), block.tags().size());
        }
    }

    public record BlockRequest(
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 2000) String hashtags) {
    }

    private final HashtagBlockRepository blocks;

    public HashtagBlockController(HashtagBlockRepository blocks) {
        this.blocks = blocks;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<BlockResponse> list() {
        return blocks.findAllByOrderByNameAsc().stream().map(BlockResponse::from).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<BlockResponse> create(@Valid @RequestBody BlockRequest request) {
        HashtagBlock block = new HashtagBlock(request.name(), request.hashtags());
        requireTags(block);
        requireFreeName(block.getName(), null);

        return ResponseEntity.status(HttpStatus.CREATED).body(BlockResponse.from(blocks.save(block)));
    }

    @PutMapping("/{id}")
    @Transactional
    public BlockResponse update(@PathVariable Long id, @Valid @RequestBody BlockRequest request) {
        HashtagBlock block = blocks.findById(id).orElseThrow(() -> new NotFoundException("Bloco de hashtags", id));

        block.setName(request.name());
        block.setHashtags(request.hashtags());
        requireTags(block);
        requireFreeName(block.getName(), id);

        return BlockResponse.from(blocks.save(block));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        HashtagBlock block = blocks.findById(id).orElseThrow(() -> new NotFoundException("Bloco de hashtags", id));
        blocks.delete(block);
        return ResponseEntity.noContent().build();
    }

    /** Depois da normalização pode não sobrar nada — ex.: o usuário mandou só "###". */
    private void requireTags(HashtagBlock block) {
        if (block.getHashtags().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos uma hashtag para o bloco.");
        }
    }

    private void requireFreeName(String name, Long ownId) {
        blocks.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Já existe um bloco chamado " + existing.getName() + ".");
                });
    }
}
