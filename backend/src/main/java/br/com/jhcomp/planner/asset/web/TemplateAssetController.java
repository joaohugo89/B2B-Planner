package br.com.jhcomp.planner.asset.web;

import br.com.jhcomp.planner.asset.domain.AssetKind;
import br.com.jhcomp.planner.asset.domain.AssetRatio;
import br.com.jhcomp.planner.asset.domain.AssetTheme;
import br.com.jhcomp.planner.asset.service.AssetStorage;
import br.com.jhcomp.planner.asset.service.TemplateAssetService;
import br.com.jhcomp.planner.asset.web.dto.AssetDtos.AssetRequest;
import br.com.jhcomp.planner.asset.web.dto.AssetDtos.AssetResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
@RequestMapping("/api/assets")
@Tag(name = "Central de Assets", description = "Templates, backgrounds e kit de logos")
public class TemplateAssetController {

    private final TemplateAssetService service;
    private final AssetStorage storage;

    public TemplateAssetController(TemplateAssetService service, AssetStorage storage) {
        this.service = service;
        this.storage = storage;
    }

    @GetMapping
    public List<AssetResponse> list(@RequestParam(required = false) AssetKind kind,
                                    @RequestParam(required = false) AssetRatio ratio) {
        return service.list(kind, ratio).stream().map(AssetResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AssetResponse getById(@PathVariable Long id) {
        return AssetResponse.from(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AssetResponse.from(service.create(request)));
    }

    /** Upload direto pelo site: grava o arquivo e já registra o template. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetResponse> upload(@RequestPart("file") MultipartFile file,
                                                @RequestParam String code,
                                                @RequestParam String name,
                                                @RequestParam(required = false) AssetKind kind,
                                                @RequestParam(required = false) AssetRatio ratio,
                                                @RequestParam(required = false) AssetTheme theme,
                                                @RequestParam(required = false) String composition) {
        AssetRequest request = new AssetRequest(code, name, kind, ratio, theme, composition, null, null, null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AssetResponse.from(service.createFromUpload(request, file)));
    }

    /** Entrega o arquivo gravado (GET público, usado nas prévias e no download). */
    @GetMapping("/files/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable String fileName) {
        Resource resource = storage.load(fileName);
        String contentType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                // Um SVG enviado como template é HTML para o navegador: aberto direto na mesma
                // origem do painel, o script dentro dele rodaria com a sessão do usuário. O
                // sandbox corta script e navegação; em <img> a imagem continua aparecendo igual.
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nomeSeguro(fileName) + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    /** Tira aspas e quebras do nome antes de ele entrar no cabeçalho. */
    private static String nomeSeguro(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @PutMapping("/{id}")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return AssetResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
