package br.com.jhcomp.planner.video;

import br.com.jhcomp.planner.common.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Os arquivos de vídeo, uma pasta por projeto.
 *
 * <p>Separado do AssetStorage das artes por dois motivos: os motores precisam de arquivo
 * local (com S3, cada etapa baixaria e subiria gigabytes), e vídeo de rosto e voz não pode
 * ter URL pública. O nome gravado é sempre gerado aqui — o do usuário só empresta a
 * extensão, e ela tem de estar na lista do tipo.</p>
 */
@Component
public class VideoStorage {

    private final Path root;

    public VideoStorage(VideoProperties properties) {
        this.root = Paths.get(properties.getDir()).toAbsolutePath().normalize();
    }

    /** A pasta do projeto, criada se preciso. É o diretório de trabalho dos motores. */
    public Path dir(Long projectId) {
        Path dir = root.resolve(String.valueOf(projectId));
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new UncheckedIOException("Não foi possível criar a pasta do projeto: " + dir, ex);
        }
        return dir;
    }

    public String newName(VideoFile kind, String extension) {
        return kind.prefixo() + "-" + UUID.randomUUID() + "." + extension;
    }

    public String store(Long projectId, VideoFile kind, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Envie um arquivo.");
        }
        String extension = StringUtils.getFilenameExtension(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (!kind.extensoes().contains(ext)) {
            throw new IllegalArgumentException(
                    "Formato não aceito. Use: " + String.join(", ", kind.extensoes().stream().sorted().toList()));
        }
        String name = newName(kind, ext);
        try (var input = file.getInputStream()) {
            Files.copy(input, resolve(projectId, name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gravar o arquivo enviado.", ex);
        }
        return name;
    }

    public String writeText(Long projectId, String name, String text) {
        try {
            Files.writeString(resolve(projectId, name), text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gravar " + name, ex);
        }
        return name;
    }

    public String readText(Long projectId, String name) {
        try {
            return Files.readString(existing(projectId, name), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler " + name, ex);
        }
    }

    public Path existing(Long projectId, String name) {
        Path target = name == null ? null : resolve(projectId, name);
        if (target == null || !Files.isRegularFile(target)) {
            throw new NotFoundException("Arquivo", name);
        }
        return target;
    }

    /** Nunca devolve um caminho fora da pasta do projeto, venha o nome de onde vier. */
    Path resolve(Long projectId, String name) {
        Path dir = dir(projectId);
        Path target = dir.resolve(name).normalize();
        if (!target.startsWith(dir) || target.equals(dir)) {
            throw new IllegalArgumentException("Nome de arquivo inválido.");
        }
        return target;
    }

    /**
     * O avatar: um vídeo-base guardado fora dos projetos, para não ser enviado de novo a cada
     * vídeo. Há um só — o sistema é de uma pessoa —, e salvar outro substitui o anterior.
     */
    public void saveAvatar(Long projectId, String baseName) {
        Path origem = existing(projectId, baseName);
        Path pasta = avatarDir();
        try {
            Files.createDirectories(pasta);
            Optional<Path> anterior = avatar();
            if (anterior.isPresent()) {
                Files.delete(anterior.get());
            }
            Files.copy(origem, pasta.resolve("base." + StringUtils.getFilenameExtension(baseName)));
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao salvar o avatar.", ex);
        }
    }

    public Optional<Path> avatar() {
        if (!Files.isDirectory(avatarDir())) {
            return Optional.empty();
        }
        try (Stream<Path> arquivos = Files.list(avatarDir())) {
            return arquivos.filter(Files::isRegularFile).findFirst();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao ler a pasta do avatar.", ex);
        }
    }

    /** Copia o avatar para o projeto como vídeo-base; devolve o nome gravado, ou null sem avatar. */
    public String copyAvatarTo(Long projectId) {
        Optional<Path> avatar = avatar();
        if (avatar.isEmpty()) {
            return null;
        }
        String name = newName(VideoFile.BASE,
                StringUtils.getFilenameExtension(avatar.get().getFileName().toString()));
        try {
            Files.copy(avatar.get(), resolve(projectId, name));
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao copiar o avatar para o projeto.", ex);
        }
        return name;
    }

    private Path avatarDir() {
        return root.resolve("avatar");
    }

    public void delete(Long projectId, String name) {
        if (name == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(projectId, name));
        } catch (IOException ignored) {
            // Arquivo órfão ocupa espaço, mas não pode impedir a operação que o substituiu.
        }
    }

    public void deleteProject(Long projectId) {
        try {
            FileSystemUtils.deleteRecursively(root.resolve(String.valueOf(projectId)));
        } catch (IOException ignored) {
            // Mesmo raciocínio de delete(): o registro some de qualquer forma.
        }
    }
}
