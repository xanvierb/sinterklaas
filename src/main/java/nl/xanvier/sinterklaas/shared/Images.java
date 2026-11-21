package nl.xanvier.sinterklaas.shared;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Images {
    private static final Logger log = LoggerFactory.getLogger(Images.class);
    private final Path root;
    private final long maxBytes;

    public Images(@Value("${sinterklaas.upload-dir}") Path root,
                  @Value("${spring.servlet.multipart.max-file-size}") DataSize maxSize) {
        this.root = root.toAbsolutePath().normalize();
        maxBytes = maxSize.toBytes();
    }

    public UUID replace(UUID previous, MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            return previous;
        }
        return replace(previous, decode(upload));
    }

    private UUID replace(UUID previous, BufferedImage image) {
        UUID next = UUID.randomUUID();
        try {
            Files.createDirectories(root);
            try (var output = Files.newOutputStream(path(next), java.nio.file.StandardOpenOption.CREATE_NEW)) {
                if (!ImageIO.write(image, "PNG", output)) {
                    throw new IOException("PNG encoder unavailable");
                }
            }
        } catch (IOException ex) {
            delete(next);
            throw new Problem("De foto kon niet worden opgeslagen.");
        }
        onCompletion(next, previous);
        return next;
    }

    public void remove(UUID id) {
        if (id != null) {
            onCompletion(null, id);
        }
    }

    private void onCompletion(UUID created, UUID previous) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED && previous != null) {
                    delete(previous);
                } else if (status == STATUS_ROLLED_BACK && created != null) {
                    delete(created);
                }
            }
        });
    }

    private BufferedImage decode(MultipartFile upload) {
        if (upload.getSize() > maxBytes) {
            throw new Problem("De foto is te groot.");
        }
        try (var input = new MemoryCacheImageInputStream(upload.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (format.equalsIgnoreCase("PNG") || format.equalsIgnoreCase("JPEG")) {
                        reader.setInput(input, true, true);
                        int width = reader.getWidth(0);
                        int height = reader.getHeight(0);
                        if (width > 0 && height > 0 && width <= 2400 && height <= 2400
                                && (long) width * height <= 4_000_000) {
                            return reader.read(0);
                        }
                        throw new Problem("Gebruik maximaal 2.400 pixels per zijde en hoogstens 4 megapixel.");
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new Problem("De foto kon niet worden gelezen.");
        }
        throw new Problem("Kies een geldige JPEG- of PNG-foto.");
    }

    public ResponseEntity<FileSystemResource> serve(UUID id) {
        if (id == null || !Files.isRegularFile(path(id))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(path(id)));
    }

    private Path path(UUID id) {
        return root.resolve(id + ".png");
    }

    private void delete(UUID id) {
        try {
            Files.deleteIfExists(path(id));
        } catch (IOException ex) {
            log.warn("Could not remove an unused image from storage", ex);
        }
    }
}
