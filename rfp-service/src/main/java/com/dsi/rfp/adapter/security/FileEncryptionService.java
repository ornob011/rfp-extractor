package com.dsi.rfp.adapter.security;

import com.dsi.rfp.config.StorageEncryptionProperties;
import com.dsi.rfp.domain.exception.DataIntegrityException;
import com.google.crypto.tink.*;
import com.google.crypto.tink.aead.AeadConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Slf4j
@Service
public class FileEncryptionService {

    private static final byte[] EMPTY_ASSOCIATED_DATA = new byte[0];

    private final StorageEncryptionProperties encryptionProperties;

    private Aead aead;

    public FileEncryptionService(StorageEncryptionProperties encryptionProperties) {
        this.encryptionProperties = encryptionProperties;
    }

    @PostConstruct
    void validateKey() {
        String keyset = encryptionProperties.keyset();

        if (keyset == null || keyset.isBlank()) {
            throw new IllegalStateException(
                "app.storage.encryption.keyset is required"
            );
        }

        try {
            AeadConfig.register();
            KeysetHandle keysetHandle = CleartextKeysetHandle.read(
                JsonKeysetReader.withString(decodeKeyset(keyset))
            );

            aead = keysetHandle.getPrimitive(
                RegistryConfiguration.get(),
                Aead.class
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException(
                "app.storage.encryption.keyset is invalid",
                exception
            );
        }

        log.info("event=encryption.key.loaded component=FileEncryptionService");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            return aead.encrypt(
                plaintext,
                EMPTY_ASSOCIATED_DATA
            );
        } catch (GeneralSecurityException exception) {
            throw new DataIntegrityException("Encryption failed", exception);
        }
    }

    public byte[] decrypt(byte[] encrypted) {
        try {
            return aead.decrypt(
                encrypted,
                EMPTY_ASSOCIATED_DATA
            );
        } catch (GeneralSecurityException exception) {
            throw new DataIntegrityException(
                "Data integrity check failed: ciphertext has been tampered with",
                exception
            );
        }
    }

    private String decodeKeyset(String keyset) {
        byte[] decoded = Base64.getDecoder().decode(keyset);
        return new String(
            decoded,
            StandardCharsets.UTF_8
        );
    }
}
