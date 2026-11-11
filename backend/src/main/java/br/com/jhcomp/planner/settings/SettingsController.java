package br.com.jhcomp.planner.settings;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/hashtags")
@Tag(name = "Configurações", description = "Ajustes da marca, como as hashtags fixas")
public class SettingsController {

    /** Hashtags que entram em todo post, além das específicas de cada legenda. */
    public static final String GLOBAL_HASHTAGS = "global_hashtags";

    public record HashtagsPayload(@Size(max = 2000) String hashtags) {
    }

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public HashtagsPayload getHashtags() {
        return new HashtagsPayload(settings.valueOr(GLOBAL_HASHTAGS, ""));
    }

    /** Vazio apaga a configuração, o que dá no mesmo que "nenhuma hashtag fixa". */
    @PutMapping
    public HashtagsPayload setHashtags(@Valid @RequestBody HashtagsPayload payload) {
        String value = payload.hashtags() == null ? "" : payload.hashtags().trim();
        settings.save(GLOBAL_HASHTAGS, value);
        return new HashtagsPayload(value);
    }
}
