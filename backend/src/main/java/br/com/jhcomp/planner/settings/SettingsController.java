package br.com.jhcomp.planner.settings;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
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

    private final AppSettingRepository repository;

    public SettingsController(AppSettingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public HashtagsPayload getHashtags() {
        return new HashtagsPayload(
                repository.findById(GLOBAL_HASHTAGS).map(AppSetting::getValue).orElse(""));
    }

    @PutMapping
    @Transactional
    public HashtagsPayload setHashtags(@Valid @RequestBody HashtagsPayload payload) {
        String value = payload.hashtags() == null ? "" : payload.hashtags().trim();

        AppSetting setting = repository.findById(GLOBAL_HASHTAGS)
                .orElseGet(() -> new AppSetting(GLOBAL_HASHTAGS, value));
        setting.setValue(value);

        return new HashtagsPayload(repository.save(setting).getValue());
    }
}
