package br.com.jhcomp.planner.asset.service;

import br.com.jhcomp.planner.common.NotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** Guarda os arquivos de template no disco da máquina e os devolve para download. */
public class DiskAssetStorage implements AssetStorage {

    private final Path root;

    public DiskAssetStorage(AssetStorageProperties properties) {
        this.root = Paths.get(properties.getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Não foi possível criar a pasta de assets: " + root, ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        String stored = AssetNames.storedNameFor(file);
        Path target = root.resolve(stored).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de arquivo inválido.");
        }

        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gravar o arquivo enviado.", ex);
        }

        return stored;
    }

    @Override
    public Resource load(String fileName) {
        Path target = root.resolve(StringUtils.cleanPath(fileName)).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new NotFoundException("Arquivo", fileName);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        Path target = root.resolve(StringUtils.cleanPath(fileName)).normalize();
        if (target.startsWith(root)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // Arquivo órfão não impede a remoção do registro.
            }
        }
    }

    @Override
    public String publicUrl(String storedName) {
        return AssetNames.API_PATH + storedName;
    }
}
