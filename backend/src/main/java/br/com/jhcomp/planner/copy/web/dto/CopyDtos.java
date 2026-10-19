package br.com.jhcomp.planner.copy.web.dto;

import br.com.jhcomp.planner.copy.domain.CopyEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CopyDtos {

    private CopyDtos() {
    }

    public record CopyResponse(
            Long id,
            String postCode,
            String headline,
            String tone,
            String body,
            String hashtags,
            String cta,
            String strategy,
            String bestTime,
            int charCount) {

        public static CopyResponse from(CopyEntry entry) {
            return new CopyResponse(
                    entry.getId(),
                    entry.getPostCode(),
                    entry.getHeadline(),
                    entry.getTone(),
                    entry.getBody(),
                    entry.getHashtags(),
                    entry.getCta(),
                    entry.getStrategy(),
                    entry.getBestTime(),
                    entry.getCharCount());
        }
    }

    public record CopyRequest(
            @Size(max = 16) String postCode,
            @NotBlank @Size(max = 200) String headline,
            String tone,
            @NotBlank String body,
            String hashtags,
            @Size(max = 240) String cta,
            @Size(max = 160) String strategy,
            @Size(max = 60) String bestTime) {
    }
}
