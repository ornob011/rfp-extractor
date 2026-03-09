package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import com.dsi.rfp.adapter.persistence.repository.DocumentRepository;
import com.dsi.rfp.adapter.persistence.repository.UserRepository;
import com.dsi.rfp.config.JpaConfig;
import com.dsi.rfp.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindBySha256Checksum() {
        DocumentEntity doc =
            DocumentEntity.builder()
                          .originalFilename("test.pdf")
                          .contentType("application/pdf")
                          .fileSizeBytes(1024L)
                          .storagePath("/tmp/test.pdf")
                          .sha256Checksum("abc123hash")
                          .build();
        documentRepository.save(doc);

        Optional<DocumentEntity> found = documentRepository.findBySha256Checksum("abc123hash");

        assertThat(found).isPresent();
        assertThat(found.get().getOriginalFilename()).isEqualTo("test.pdf");
    }

    @Test
    void shouldReturnEmptyWhenChecksumNotFound() {
        Optional<DocumentEntity> found = documentRepository.findBySha256Checksum("nonexistent");

        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByUploadedBy() {
        UserEntity user =
            userRepository.save(
                UserEntity.builder()
                          .username("analyst1")
                          .passwordHash("hash")
                          .role(UserRole.ANALYST)
                          .enabled(true)
                          .build());

        documentRepository.save(
            DocumentEntity.builder()
                          .originalFilename("doc1.pdf")
                          .contentType("application/pdf")
                          .fileSizeBytes(2048L)
                          .storagePath("/tmp/doc1.pdf")
                          .sha256Checksum("checksum1")
                          .uploadedBy(user)
                          .build());

        List<DocumentEntity> docs = documentRepository.findByUploadedBy(user);

        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().getOriginalFilename()).isEqualTo("doc1.pdf");
    }
}
