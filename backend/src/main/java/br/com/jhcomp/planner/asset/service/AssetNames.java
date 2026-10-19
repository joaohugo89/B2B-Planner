package br.com.jhcomp.planner.asset.service;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Validação do arquivo enviado e geração do nome final, igual nos dois storages. */
final class AssetNames {

    /** Caminho servido pelo backend quando o arquivo não tem URL pública própria. */
    static final String API_PATH = "/api/assets/files/";

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp", "svg", "zip", "afdesign", "json");

    private AssetNames() {
    }

    /** Devolve o nome único a gravar, recusando arquivo vazio ou extensão fora da lista. */
    static String storedNameFor(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie um arquivo.");
        }

        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "asset" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(original);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Extensão não suportada. Use: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        return UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
    }
}
