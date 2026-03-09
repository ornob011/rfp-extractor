package com.dsi.rfp.adapter.security;

import com.dsi.rfp.config.StorageEncryptionProperties;
import com.dsi.rfp.domain.exception.DataIntegrityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileEncryptionServiceTest {

    private static final String TEST_KEYSET = "eyJwcmltYXJ5S2V5SWQiOjc1Nzk2MDA0Mywia2V5IjpbeyJrZXlEYXRhIjp7InR5cGVVcmwiOiJ0eXBlLmdvb2dsZWFwaXMuY29tL2dvb2dsZS5jcnlwdG8udGluay5BZXNHY21LZXkiLCJ2YWx1ZSI6IkdpQkRhTlZna0IrL3dMWiswY0NSanJFTnNieGJMaHQxS0hZMW1UT095U1I2VHc9PSIsImtleU1hdGVyaWFsVHlwZSI6IlNZTU1FVFJJQyJ9LCJzdGF0dXMiOiJFTkFCTEVEIiwia2V5SWQiOjc1Nzk2MDA0Mywib3V0cHV0UHJlZml4VHlwZSI6IlRJTksifV19Cg==";

    private FileEncryptionService service;

    @BeforeEach
    void setUp() {
        service = new FileEncryptionService(
            new StorageEncryptionProperties(TEST_KEYSET)
        );
        service.validateKey();
    }

    @Test
    void shouldEncryptAndDecrypt() {
        byte[] plaintext = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = service.encrypt(plaintext);
        byte[] decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldProduceDifferentCiphertextEachTime() {
        byte[] plaintext = "Same content".getBytes(StandardCharsets.UTF_8);

        byte[] encryptedOne = service.encrypt(plaintext);
        byte[] encryptedTwo = service.encrypt(plaintext);

        assertThat(encryptedOne).isNotEqualTo(encryptedTwo);
    }

    @Test
    void shouldDetectTamperedCiphertext() {
        byte[] plaintext = "Sensitive data".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = service.encrypt(plaintext);

        byte[] tampered = encrypted.clone();
        tampered[0] ^= 0xFF;

        assertThatThrownBy(() -> service.decrypt(tampered))
            .isInstanceOf(DataIntegrityException.class)
            .hasMessageContaining("tampered");
    }

    @Test
    void shouldRoundtripEncryptedBlob() {
        byte[] plaintext = "On-disk test".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = service.encrypt(plaintext);
        byte[] decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldFailWithMissingKeyset() {
        FileEncryptionService noKeyset = new FileEncryptionService(
            new StorageEncryptionProperties("")
        );

        assertThatThrownBy(noKeyset::validateKey)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("required");
    }

    @Test
    void shouldFailWithInvalidKeyset() {
        FileEncryptionService invalidKeyset = new FileEncryptionService(
            new StorageEncryptionProperties("tooshort")
        );

        assertThatThrownBy(invalidKeyset::validateKey)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("keyset is invalid");
    }
}
