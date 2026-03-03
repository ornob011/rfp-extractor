# Sprint 1 — Foundation & LLM Infrastructure

## 0) Sprint Intent

- Stand up a fully compiling, runnable multi-module Maven project (rfp-core + rfp-service) with Spring Boot 3.5.11 and
  Java 21, confirming that the build pipeline is green before any business logic is written.
- Establish a configurable, provider-agnostic LLM abstraction backed by Spring AI and wrapped with a Resilience4j
  reliability layer (retry, circuit breaker, rate limiter, timeout) so that every subsequent sprint can call
  `LlmAdapter` without knowing which provider is active.
- Deliver a Python FastAPI OCR sidecar skeleton (health endpoint only), a React 18 + Tailwind v4 + Vite frontend
  skeleton (upload page only), and a Docker Compose stack tying all five services together.
- Provide the prompt file infrastructure and the first prompt placeholder so that Sprint 4 can drop in real content with
  zero structural work.
- **Establish the complete, durable PostgreSQL schema** (all JPA entities, all enums, all repositories) so that every
  subsequent sprint can write analysis state to PostgreSQL without any entity-mapping work. This is foundational
  infrastructure — nothing deferred to later sprints. Redis is removed from the stack entirely; PostgreSQL is the
  single source of truth for all job state.

**Non-goals:**

- PDF parsing, text extraction, or any OCR logic (Sprint 2/6).
- Section segmentation, entity extraction, or any agent graph (Sprints 3/4).
- Authentication, authorization, or JWT filters (Sprint 11). Spring Security is NOT wired in Sprint 1 — `UserEntity`
  is a plain JPA entity with no security integration until Sprint 11.
- Rule packs or artifact generation (Sprints 8/10).
- Redis — removed from the stack. No `RedisJobStateRepository`, no `RedisConfig`, no `spring-boot-starter-data-redis`.

---

## 1) Entry Criteria

- Java 21 JDK installed on developer machines and CI (`java -version` reports 21).
- Maven 3.9+ installed (`mvn -version`).
- Node 20+ installed (`node -v`).
- Python 3.11+ installed with `pip`.
- Docker Desktop (or Docker Engine + Compose plugin) installed.
- OpenRouter API key obtained and stored as environment variable `OPENROUTER_API_KEY`.
- Git repository initialized at project root (`rfp-extractor/`).
- No prior sprint artifacts — this is the first sprint.
- A `.env` file at project root populated from `.env.example` with at minimum `OPENROUTER_API_KEY`,
  `POSTGRES_PASSWORD=rfppass`, `POSTGRES_DB=rfpdb`, `POSTGRES_USER=rfpuser`.

---

## 2) Deliverables

- `rfp-extractor/pom.xml` — parent POM, Java 21, Spring Boot 3.5.11 BOM, all dependency versions pinned.
- `rfp-extractor/rfp-core/pom.xml` + source skeleton — zero framework deps, compilable.
- `rfp-extractor/rfp-service/pom.xml` + Spring Boot app that starts on port 8080.
- `LlmProviderProperties`, `LlmProviderConfig`, `LlmAdapter`, `LlmResilienceConfig` fully implemented.
- `GET /api/v1/health` returning provider name, model, and OCR reachability.
- Python FastAPI at `rfp-python-ocr/` with `GET /health` responding `{"status":"ok","version":"1.0.0"}`.
- React frontend at `rfp-frontend/` with upload page skeleton compiled by Vite.
- `docker-compose.yml` with health checks on all **four** services (postgres, rfp-python-ocr, rfp-service,
  rfp-frontend).
  Redis is NOT in the stack.
- `prompts/` directory with `README.md` and `prompts/entity-general-v1.md` placeholder.
- CI (`mvn test`) passes with zero test failures (at least 10 unit tests covering LlmAdapter and LlmResilienceConfig).

**New in this plan — DB Schema deliverables (D-27 through D-43):**

Enums (`rfp-core/.../domain/model/`):

| #    | Deliverable         | Values                                                                                                                                                        |
|------|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| D-27 | `AnalysisStatus`    | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `PARTIAL`                                                                                                         |
| D-28 | `ExecutionStatus`   | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`                                                                                                      |
| D-29 | `TerminationReason` | `SUCCESS`, `ERROR`, `TIMEOUT`, `MAX_REPAIRS_EXCEEDED`, `USER_CANCELLED`                                                                                       |
| D-30 | `AgentStepType`     | `VALIDATE`, `CLASSIFY_PAGES`, `EXTRACT_TEXT`, `SEGMENT_SECTIONS`, `EXTRACT_TABLES`, `EXTRACT_ENTITIES`, `SCORE_CONFIDENCE`, `REPAIR`, `RUN_RULES`, `FINALIZE` |
| D-31 | `StepOutcome`       | `SUCCESS`, `SKIPPED`, `FAILED`                                                                                                                                |

JPA Entities (`rfp-service/.../adapter/persistence/entity/`):

| #    | Deliverable            | Table               |
|------|------------------------|---------------------|
| D-32 | `DocumentEntity`       | `documents`         |
| D-33 | `AnalysisJobEntity`    | `analysis_jobs`     |
| D-34 | `AnalysisResultEntity` | `analysis_results`  |
| D-35 | `AgentExecutionEntity` | `agent_executions`  |
| D-36 | `AgentStepEntity`      | `agent_steps`       |
| D-37 | `UserAuditEntity`      | `user_audit_events` |

Spring Data JPA Repositories (`rfp-service/.../adapter/persistence/`):

| #    | Repository                 | Key query methods                                                   |
|------|----------------------------|---------------------------------------------------------------------|
| D-38 | `DocumentRepository`       | `findBySha256Checksum`, `findByUploadedBy`                          |
| D-39 | `AnalysisJobRepository`    | `findByDocumentId`, `findBySubmittedByAndStatus`, `findAllByStatus` |
| D-40 | `AnalysisResultRepository` | `findByAnalysisJobId`                                               |
| D-41 | `AgentExecutionRepository` | `findByAnalysisJobId`                                               |
| D-42 | `AgentStepRepository`      | `findByExecutionIdOrderBySequence`                                  |
| D-43 | `UserAuditRepository`      | `findByUserIdOrderByCreatedAtDesc`, `findAllOrderByCreatedAtDesc`   |

---

## 3) Work Breakdown

### Epic 1 — Maven Multi-Module Project Structure

#### Story 1.1 — Parent POM and Module Layout

**Description:**
Create the Maven parent POM at `rfp-extractor/pom.xml` that governs all dependency versions through
`<dependencyManagement>` and all plugin versions through `<pluginManagement>`. Create the two child modules `rfp-core`
and `rfp-service`. `rfp-core` must compile with zero Spring or framework dependencies; it contains only pure Java domain
classes and port interfaces. `rfp-service` declares `rfp-core` as a dependency and carries all framework dependencies.

**Acceptance Criteria:**

```gherkin
Given the project root directory rfp-extractor/
When `mvn clean compile -pl rfp-core` is run
Then the build succeeds with exit code 0 and no compilation errors

Given the parent POM defines spring-boot-starter-parent as parent
When `mvn dependency:tree -pl rfp-service` is run
Then transitive dependencies include spring-boot-starter-web, spring-ai-openai-spring-boot-starter, resilience4j-spring-boot3, langchain4j-spring-boot-starter

Given rfp-core/pom.xml
When inspecting its dependencies
Then there are zero dependencies with groupId org.springframework or dev.langchain4j or io.github.langgraph4j
```

**Interfaces/Contracts:**
N/A — build artifact only.

**Implementation Plan:**

File: `rfp-extractor/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
<modelVersion>4.0.0</modelVersion>
<groupId>com.dsi</groupId>
<artifactId>rfp-extractor</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>

<modules>
<module>rfp-core</module>
<module>rfp-service</module>
</modules>

<properties>
<java.version>21</java.version>
<maven.compiler.source>21</maven.compiler.source>
<maven.compiler.target>21</maven.compiler.target>
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
<spring-boot.version>3.5.11</spring-boot.version>
<spring-ai.version>1.0.0</spring-ai.version>
<langchain4j.version>0.36.2</langchain4j.version>
<langgraph4j.version>1.8.4</langgraph4j.version>
<resilience4j.version>2.2.0</resilience4j.version>
<lombok.version>1.18.34</lombok.version>
<pdfbox.version>3.0.3</pdfbox.version>
<poi.version>5.3.0</poi.version>
<networknt.version>1.4.1</networknt.version>
<jmespath.version>0.6.0</jmespath.version>
<freemarker.version>2.3.33</freemarker.version>
<commons-text.version>1.12.0</commons-text.version>
<jackson.version>2.17.2</jackson.version>
</properties>

<parent>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-parent</artifactId>
<version>3.5.11</version>
<relativePath/>
</parent>

<dependencyManagement>
<dependencies>
    <!-- Spring AI BOM -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-bom</artifactId>
        <version>${spring-ai.version}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
    <!-- LangChain4J BOM -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-bom</artifactId>
        <version>${langchain4j.version}</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
    <!-- rfp-core internal -->
    <dependency>
        <groupId>com.dsi</groupId>
        <artifactId>rfp-core</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- Resilience4j -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>${resilience4j.version}</version>
    </dependency>
    <!-- PDFBox -->
    <dependency>
        <groupId>org.apache.pdfbox</groupId>
        <artifactId>pdfbox</artifactId>
        <version>${pdfbox.version}</version>
    </dependency>
    <!-- POI OOXML -->
    <dependency>
        <groupId>org.apache.poi</groupId>
        <artifactId>poi-ooxml</artifactId>
        <version>${poi.version}</version>
    </dependency>
    <!-- Networknt JSON Schema Validator -->
    <dependency>
        <groupId>com.networknt</groupId>
        <artifactId>json-schema-validator</artifactId>
        <version>${networknt.version}</version>
    </dependency>
    <!-- JMESPath -->
    <dependency>
        <groupId>io.burt</groupId>
        <artifactId>jmespath-jackson</artifactId>
        <version>${jmespath.version}</version>
    </dependency>
    <!-- Freemarker -->
    <dependency>
        <groupId>org.freemarker</groupId>
        <artifactId>freemarker</artifactId>
        <version>${freemarker.version}</version>
    </dependency>
    <!-- Commons Text -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-text</artifactId>
        <version>${commons-text.version}</version>
    </dependency>
    <!-- LangGraph4J -->
    <dependency>
        <groupId>org.bsc.langgraph4j</groupId>
        <artifactId>langgraph4j-core</artifactId>
        <version>${langgraph4j.version}</version>
    </dependency>
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <version>${lombok.version}</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
</dependencyManagement>

<build>
<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>${spring-boot.version}</version>
            <configuration>
                <excludes>
                    <exclude>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                    </exclude>
                </excludes>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <source>21</source>
                <target>21</target>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.3.1</version>
        </plugin>
    </plugins>
</pluginManagement>
</build>
    </project>
```

File: `rfp-extractor/rfp-core/pom.xml`

- groupId: `com.dsi`, artifactId: `rfp-core`, packaging: `jar`
- Parent: `rfp-extractor` parent POM (relativePath: `../pom.xml`)
- Dependencies: only `lombok` (provided scope), `jackson-databind` (for model serialization annotations),
  `jackson-datatype-jsr310` (for Java time types).
- No Spring, no LangChain4J, no framework dependencies.

File: `rfp-extractor/rfp-service/pom.xml`

- groupId: `com.dsi`, artifactId: `rfp-service`, packaging: `jar`
- Depends on `rfp-core`
- Full dependency list:
    - `spring-boot-starter-web`
    - `spring-boot-starter-data-jpa`
    - `spring-boot-starter-security`
    - `spring-boot-starter-oauth2-resource-server`
    - `spring-ai-openai-spring-boot-starter`
    - `spring-ai-ollama-spring-boot-starter`
    - `langchain4j-spring-boot-starter`
    - `langchain4j-open-ai`
    - `langchain4j-ollama`
    - `langchain4j-document-parser-apache-pdfbox`
    - `langgraph4j-core`
    - `pdfbox`
    - `jackson-databind`
    - `jackson-dataformat-yaml`
    - `jmespath-jackson`
    - `json-schema-validator`
    - `poi-ooxml`
    - `freemarker`
    - `resilience4j-spring-boot3`
    - `lombok` (provided)
    - `postgresql` (runtime scope)
    - `commons-text`
    - `spring-boot-starter-test` (test scope)

Package structure for rfp-core:

```
rfp-core/src/main/java/com/dsi/rfp/
├── domain/
│   ├── model/       (empty in Sprint 1 — placeholder package-info.java)
│   └── port/        (empty in Sprint 1 — placeholder package-info.java)
```

Package structure for rfp-service:

```
rfp-service/src/main/java/com/dsi/rfp/
├── RfpApplication.java
├── config/
├── adapter/
│   ├── api/
│   └── llm/
└── application/
    └── service/
```

`RfpApplication.java`:

```java
package com.dsi.rfp;

@SpringBootApplication
public class RfpApplication {
    public static void main(String[] args) {
        SpringApplication.run(RfpApplication.class, args);
    }
}
```

**Dependencies:** None (first story).
**Risks:**

- Spring AI BOM version incompatibility with Spring Boot 3.5.11. Mitigation: pin Spring AI to 1.0.0 (GA); check Spring
  AI compatibility matrix before starting.
- LangGraph4J artifact not in Maven Central. Mitigation: verify group `org.bsc.langgraph4j` is in Maven Central; if
  absent, add JitPack repository to parent POM.

**Test Plan:**

- No unit tests for POM files; verified by `mvn test` succeeding.
- Manual check: `mvn dependency:analyze -pl rfp-core` shows zero unused declared / used undeclared violations.

**Observability:** N/A for build artifact.

**Estimation:** 5 SP

---

#### Story 1.2 — Application Properties & Configuration Skeleton

**Description:**
Create `application.properties` (and `application-docker.properties`) in `rfp-service/src/main/resources/`. All LLM,
storage, async, and upload configuration keys are declared here with sensible defaults. Create
`LlmProviderProperties.java` bound via `@ConfigurationProperties("app.llm")`.

**Acceptance Criteria:**

```gherkin
Given application.properties has app.llm.provider=openrouter
When RfpApplication starts
Then LlmProviderProperties.getProvider() returns "openrouter" with no NullPointerException

Given a missing app.llm.openrouter.api-key
When RfpApplication starts with provider=openrouter
Then startup fails with a descriptive error message (validated in LlmProviderConfig)

Given application-docker.properties exists
When the Spring profile "docker" is active
Then datasource URL resolves to jdbc:postgresql://postgres:5432/rfpdb
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/config/LlmProviderProperties.java`

```java
package com.dsi.rfp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.llm")
public class LlmProviderProperties {
    private String provider = "openrouter";          // "openrouter" | "ollama"
    private OpenRouterProps openrouter = new OpenRouterProps();
    private OllamaProps ollama = new OllamaProps();
    private double temperature = 0.0;
    private int maxTokens = 4096;
    private int chunkSizeTokens = 3500;
    private int timeoutSeconds = 30;
    private int rateLimitPerMinute = 60;

    @Data
    public static class OpenRouterProps {
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String apiKey;                        // required if provider=openrouter
        private String model = "google/gemini-2.0-flash-001";
        private final String modelJudge = "google/gemini-2.5-pro-preview-06-05";
    }

    @Data
    public static class OllamaProps {
        private String baseUrl = "http://localhost:11434";
        private final String model = "llama3.1:8b";
        private final String modelJudge = "llama3.1:70b";
    }
}
```

File: `rfp-service/src/main/resources/application.properties` — key entries:

```properties
# Server
server.port=8080
spring.application.name=rfp-service
# LLM Provider
app.llm.provider=openrouter
app.llm.openrouter.base-url=https://openrouter.ai/api/v1
app.llm.openrouter.api-key=${OPENROUTER_API_KEY:}
app.llm.openrouter.model=google/gemini-2.0-flash-001
app.llm.openrouter.model-judge=google/gemini-2.5-pro-preview-06-05
app.llm.ollama.base-url=http://localhost:11434
app.llm.ollama.model=llama3.1:8b
app.llm.ollama.model-judge=llama3.1:70b
app.llm.temperature=0.0
app.llm.max-tokens=4096
app.llm.chunk-size-tokens=3500
app.llm.timeout-seconds=30
app.llm.rate-limit-per-minute=60
# Async
app.async.core-pool-size=2
app.async.max-pool-size=4
app.async.queue-capacity=20
# Upload
app.upload.max-size-mb=100
# Storage
app.storage.base-path=/tmp/rfp-storage
# OCR Sidecar
app.ocr.sidecar-url=http://localhost:8000
# Database (local defaults)
spring.datasource.url=jdbc:postgresql://localhost:5432/rfpdb
spring.datasource.username=${POSTGRES_USER:rfpuser}
spring.datasource.password=${POSTGRES_PASSWORD:rfppass}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
# Resilience4j (overridden in LlmResilienceConfig programmatically, but defaults here for reference)
resilience4j.retry.instances.llm-retry.max-attempts=3
resilience4j.circuitbreaker.instances.llm-cb.sliding-window-size=10
resilience4j.ratelimiter.instances.llm-rl.limit-for-period=60
```

File: `rfp-service/src/main/resources/application-docker.properties`:

```properties
spring.datasource.url=jdbc:postgresql://postgres:5432/rfpdb
app.ocr.sidecar-url=http://rfp-python-ocr:8000
app.storage.base-path=/app/rfp-storage
```

**Implementation Plan:**

1. Create directory `rfp-service/src/main/resources/`.
2. Write `application.properties` with all keys above.
3. Write `application-docker.properties`.
4. Write `LlmProviderProperties.java` as shown.
5. Add `@EnableConfigurationProperties(LlmProviderProperties.class)` to `RfpApplication.java` or use `@Component` on the
   properties class.

**Dependencies:** Story 1.1 (module structure exists).
**Risks:** Property name mismatch (camelCase vs kebab-case). Mitigation: Spring Boot relaxed binding handles both; use
consistent kebab-case in .properties files and camelCase field names in @Data class.

**Test Plan:**
Class: `LlmProviderPropertiesTest` in `rfp-service/src/test/java/com/dsi/rfp/config/`

- Use `@SpringBootTest` only with `@TestPropertySource` — no, actually: use a plain `@ConfigurationPropertiesTest` (
  Spring Boot test slice) or manually construct the Properties class.
- Per code rules: no Spring context in unit tests. Use manual binding:

```java
// LlmProviderPropertiesTest.java
class LlmProviderPropertiesTest {
    @Test
    void shouldReturnDefaultProviderWhenNotConfigured() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getProvider()).isEqualTo("openrouter");
    }

    @Test
    void shouldReturnDefaultTemperatureWhenNotConfigured() {
        LlmProviderProperties props = new LlmProviderProperties();
        assertThat(props.getTemperature()).isEqualTo(0.0);
    }

    @Test
    void shouldReturnConfiguredModelWhenSet() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.getOpenrouter().setModel("google/gemini-2.0-flash-001");
        assertThat(props.getOpenrouter().getModel()).isEqualTo("google/gemini-2.0-flash-001");
    }
}
```

**Observability:** N/A — configuration class.
**Estimation:** 3 SP

---

### Epic 2 — LLM Provider Abstraction & Resilience

#### Story 2.1 — LlmProviderConfig: Register Spring AI and LangChain4J Beans

**Description:**
Create `LlmProviderConfig.java` in `rfp-service/src/main/java/com/dsi/rfp/config/`. This `@Configuration` class reads
`LlmProviderProperties` and conditionally registers two primary beans: a Spring AI `ChatClient` (used by `LlmAdapter`
for structured extraction) and a LangChain4J `ChatLanguageModel` (used by entity sub-extractors in Sprint 4). Use
`@ConditionalOnProperty` to switch between OpenRouter and Ollama implementations. For OpenRouter, construct a Spring AI
OpenAI-compatible client pointing at the OpenRouter base URL. For Ollama, use the Spring AI Ollama client.

**Acceptance Criteria:**

```gherkin
Given app.llm.provider=openrouter and OPENROUTER_API_KEY is set
When the Spring context loads
Then a ChatClient bean named "chatClient" is registered
And a ChatLanguageModel bean named "chatLanguageModel" is registered
And both beans point to the OpenRouter base URL

Given app.llm.provider=ollama
When the Spring context loads
Then ChatClient bean points to http://localhost:11434
And ChatLanguageModel bean points to http://localhost:11434

Given app.llm.provider=openrouter and app.llm.openrouter.api-key is blank
When LlmProviderConfig.validateOpenRouterConfig() runs during @PostConstruct
Then it throws IllegalStateException with message "OpenRouter API key must not be blank when provider=openrouter"
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/config/LlmProviderConfig.java`

```java
package com.dsi.rfp.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class LlmProviderConfig {

    private final LlmProviderProperties props;

    @PostConstruct
    void validateConfiguration() {
        // validation logic described below
    }

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "openrouter", matchIfMissing = true)
    public ChatClient openRouterChatClient() { ...}

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
    public ChatClient ollamaChatClient() { ...}

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "openrouter", matchIfMissing = true)
    public ChatLanguageModel openRouterChatLanguageModel() { ...}

    @Bean
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
    public ChatLanguageModel ollamaChatLanguageModel() { ...}

    private void validateOpenRouterConfig() {
        if (!StringUtils.hasText(props.getOpenrouter().getApiKey())) {
            throw new IllegalStateException(
                "OpenRouter API key must not be blank when provider=openrouter. " +
                    "Set app.llm.openrouter.api-key or OPENROUTER_API_KEY env var.");
        }
    }
}
```

**Detailed implementation for each bean:**

`openRouterChatClient()`:

- Construct `OpenAiApi` with baseUrl = `props.getOpenrouter().getBaseUrl()`, apiKey =
  `props.getOpenrouter().getApiKey()`.
- Construct `SpringAiOpenAiChatModel` from that API, with options: model = `props.getOpenrouter().getModel()`,
  temperature = `props.getTemperature()`, maxTokens = `props.getMaxTokens()`.
- Return `ChatClient.builder(chatModel).build()`.

`ollamaChatClient()`:

- Construct `OllamaApi` with baseUrl = `props.getOllama().getBaseUrl()`.
- Construct `SpringAiOllamaChatModel` with options: model = `props.getOllama().getModel()`.
- Return `ChatClient.builder(chatModel).build()`.

`openRouterChatLanguageModel()`:

- Return
  `OpenAiChatModel.builder().baseUrl(props.getOpenrouter().getBaseUrl()).apiKey(props.getOpenrouter().getApiKey()).modelName(props.getOpenrouter().getModel()).temperature(props.getTemperature()).maxTokens(props.getMaxTokens()).timeout(Duration.ofSeconds(props.getTimeoutSeconds())).build()`.

`ollamaChatLanguageModel()`:

- Return
  `OllamaChatModel.builder().baseUrl(props.getOllama().getBaseUrl()).modelName(props.getOllama().getModel()).build()`.

`validateConfiguration()` implementation:

```java

@PostConstruct
void validateConfiguration() {
    log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO LLM provider configured: provider={}, model={}",
        props.getProvider(),
        "openrouter".equals(props.getProvider())
            ? props.getOpenrouter().getModel()
            : props.getOllama().getModel());
    if ("openrouter".equals(props.getProvider())) {
        validateOpenRouterConfig();
    }
}
```

**Dependencies:** Story 1.2 (LlmProviderProperties exists).
**Risks:** Spring AI API changes between milestone versions. Mitigation: pin to Spring AI 1.0.0 GA; do not use snapshot
versions.

**Test Plan:**
Class: `LlmProviderConfigTest`

- All tests: instantiate `LlmProviderConfig` directly with a manually constructed `LlmProviderProperties`. No Spring
  context.
- Mock nothing (pure logic test).

```java
class LlmProviderConfigTest {
    @Test
    void shouldThrowWhenOpenRouterApiKeyIsBlankAndProviderIsOpenrouter() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.setProvider("openrouter");
        props.getOpenrouter().setApiKey("");
        LlmProviderConfig config = new LlmProviderConfig(props);
        assertThatThrownBy(config::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OpenRouter API key must not be blank");
    }

    @Test
    void shouldNotThrowWhenOllamaProviderAndNoApiKey() {
        LlmProviderProperties props = new LlmProviderProperties();
        props.setProvider("ollama");
        LlmProviderConfig config = new LlmProviderConfig(props);
        assertThatNoException().isThrownBy(config::validateConfiguration);
    }
}
```

**Observability:**

- Log at INFO on startup: `"LLM provider configured: provider={}, model={}"`

**Estimation:** 5 SP

---

#### Story 2.2 — LlmResilienceConfig: Retry, Circuit Breaker, Rate Limiter, Timeout

**Description:**
Create `LlmResilienceConfig.java` in `rfp-service/src/main/java/com/dsi/rfp/config/`. This `@Configuration` class
programmatically creates all four Resilience4j components with the exact parameters specified: retry (3 attempts, 1s
initial delay, 2x multiplier, 0.3 jitter), circuit breaker (50% failure rate threshold / sliding window 10 / 30s wait in
open state), rate limiter (60 per minute / 10s acquire timeout), timeout (30s). Uses Resilience4j Registry beans. Each
component is named with constants to avoid typos.

**Acceptance Criteria:**

```gherkin
Given LlmResilienceConfig is instantiated
When getRetry() is called
Then the returned Retry has maxAttempts=3 and exponential backoff starting at 1s

Given the circuit breaker is in CLOSED state
When 6 out of 10 consecutive calls fail (60% > 50% threshold)
Then the circuit breaker transitions to OPEN state

Given the rate limiter is configured for 60/min
When 61 calls are made within one second
Then the 61st call throws RequestNotPermitted after 10s timeout

Given a decorated LLM call takes longer than 30s
When the timeout fires
Then TimeoutException is thrown and the circuit breaker counts it as a failure
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/config/LlmResilienceConfig.java`

```java
package com.dsi.rfp.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class LlmResilienceConfig {

    public static final String LLM_RETRY = "llm-retry";
    public static final String LLM_CB = "llm-cb";
    public static final String LLM_RL = "llm-rl";
    public static final String LLM_TIMEOUT = "llm-timeout";

    private final LlmProviderProperties props;

    @Bean
    public Retry llmRetry(RetryRegistry retryRegistry) { ...}

    @Bean
    public CircuitBreaker llmCircuitBreaker(CircuitBreakerRegistry cbRegistry) { ...}

    @Bean
    public RateLimiter llmRateLimiter(RateLimiterRegistry rlRegistry) { ...}

    @Bean
    public TimeLimiter llmTimeLimiter(TimeLimiterRegistry tlRegistry) { ...}
}
```

**Detailed configuration values:**

`llmRetry()`:

```java
RetryConfig config = RetryConfig.custom()
                                .maxAttempts(3)
                                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                                    Duration.ofSeconds(1),   // initial interval
                                    2.0,                     // multiplier
                                    0.3                      // randomization factor (jitter)
                                ))
                                .retryOnException(e -> !(e instanceof LlmResponseParseException))
                                .build();
return retryRegistry.

retry(LLM_RETRY, config);
```

Note: `LlmResponseParseException` should NOT trigger a retry — it means the LLM responded but the response was
unparseable. Only `LlmUnavailableException` and transient IO errors should retry.

`llmCircuitBreaker()`:

```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                                                  .failureRateThreshold(50.0f)
                                                  .slidingWindowType(SlidingWindowType.COUNT_BASED)
                                                  .slidingWindowSize(10)
                                                  .waitDurationInOpenState(Duration.ofSeconds(30))
                                                  .permittedNumberOfCallsInHalfOpenState(3)
                                                  .recordExceptions(LlmUnavailableException.class, java.io.IOException.class,
                                                      java.util.concurrent.TimeoutException.class)
                                                  .build();
return cbRegistry.

circuitBreaker(LLM_CB, config);
```

`llmRateLimiter()`:

```java
RateLimiterConfig config = RateLimiterConfig.custom()
                                            .limitForPeriod(props.getRateLimitPerMinute())
                                            .limitRefreshPeriod(Duration.ofMinutes(1))
                                            .timeoutDuration(Duration.ofSeconds(10))
                                            .build();
return rlRegistry.

rateLimiter(LLM_RL, config);
```

`llmTimeLimiter()`:

```java
TimeLimiterConfig config = TimeLimiterConfig.custom()
                                            .timeoutDuration(Duration.ofSeconds(props.getTimeoutSeconds()))
                                            .cancelRunningFuture(true)
                                            .build();
return tlRegistry.

timeLimiter(LLM_TIMEOUT, config);
```

Also add to `rfp-service/src/main/java/com/dsi/rfp/config/` package:

File: `AsyncConfig.java`:

```java

@Slf4j
@Configuration
@EnableAsync
@RequiredArgsConstructor
public class AsyncConfig implements AsyncConfigurer {
    @Value("${app.async.core-pool-size:2}")
    private int corePoolSize;
    @Value("${app.async.max-pool-size:4}")
    private int maxPoolSize;
    @Value("${app.async.queue-capacity:20}")
    private int queueCapacity;

    // Sprint 4 adds extractionExecutor (core=4, max=8, queue=20) for ExtractionOrchestrationService.
    @Bean(name = "rfpTaskExecutor")
    public Executor rfpTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("rfp-worker-");
        executor.initialize();
        return executor;
    }

    /**
     * Global handler for unhandled exceptions thrown by @Async void methods.
     * Per the exception policy, @Async void methods must NOT use catch (Exception e).
     * Instead, any uncaught exception propagates here, where the job state is marked FAILED.
     * The Spring container invokes this after the thread unwinds — the JobStatePort bean
     * must be injected here, NOT obtained from static context.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error(
                "event=async.uncaught component=AsyncConfig status=FAIL method={} errorCode=ASYNC_FAILURE traceId=NA spanId=NA jobId=NA durationMs=NA error={}",
                method.getName(), ex.getMessage(), ex);
        // NOTE: Sprint 2 replaces this with JobStateAsyncExceptionHandler that marks the
        // AnalysisJobEntity as FAILED via AnalysisJobRepository (PostgreSQL). The handler is
        // injected as a Spring bean so it can access the repository without static context.
        // See Sprint 2 Epic 4 for full impl.
    }
}
```

**Dependencies:** Story 1.2 (properties exist), Story 2.1 (exceptions exist after Story 2.3).
**Risks:** Resilience4j Registry beans may not be auto-configured by Spring Boot autoconfiguration if
`resilience4j-spring-boot3` is on classpath. Mitigation: confirm autoconfiguration registers `RetryRegistry`,
`CircuitBreakerRegistry`, etc. as beans; if not, instantiate them manually with `RetryRegistry.ofDefaults()`.

**Test Plan:**
Class: `LlmResilienceConfigTest`

```java
class LlmResilienceConfigTest {
    private LlmResilienceConfig config;

    @BeforeEach
    void setUp() {
        LlmProviderProperties props = new LlmProviderProperties();
        config = new LlmResilienceConfig(props);
    }

    @Test
    void shouldConfigureRetryWithThreeAttempts() {
        Retry retry = config.llmRetry(RetryRegistry.ofDefaults());
        assertThat(retry.getRetryConfig().getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void shouldConfigureCircuitBreakerWithFiftyPercentThreshold() {
        CircuitBreaker cb = config.llmCircuitBreaker(CircuitBreakerRegistry.ofDefaults());
        assertThat(cb.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
    }

    @Test
    void shouldConfigureRateLimiterWithSixtyPerMinute() {
        RateLimiter rl = config.llmRateLimiter(RateLimiterRegistry.ofDefaults());
        assertThat(rl.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(60);
    }

    @Test
    void shouldConfigureTimeLimiterWithThirtySeconds() {
        TimeLimiter tl = config.llmTimeLimiter(TimeLimiterRegistry.ofDefaults());
        assertThat(tl.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(30));
    }
}
```

**Observability:** Deferred to wishlist (unit-test-only baseline; no Actuator).

**Estimation:** 5 SP

---

#### Story 2.3 — Custom LLM Exceptions

**Description:**
Create two custom exception classes used throughout the LLM layer. These are declared in `rfp-core` (no framework
dependency) so domain and application layers can reference them without depending on the adapter layer.

**Acceptance Criteria:**

```gherkin
Given LlmUnavailableException is thrown
When caught by Resilience4j retry
Then the retry attempts up to 3 times

Given LlmResponseParseException is thrown
When caught by Resilience4j retry
Then the retry does NOT trigger (configured via retryOnException predicate)
```

**Interfaces/Contracts:**

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/LlmUnavailableException.java`

```java
package com.dsi.rfp.domain.exception;

public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message) {
        super(message);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

File: `rfp-core/src/main/java/com/dsi/rfp/domain/exception/LlmResponseParseException.java`

```java
package com.dsi.rfp.domain.exception;

public class LlmResponseParseException extends RuntimeException {
    private final String rawResponse;

    public LlmResponseParseException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }

    public LlmResponseParseException(String message, String rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = rawResponse;
    }

    public String getRawResponse() {
        return rawResponse;
    }
}
```

**Dependencies:** Story 1.1.
**Estimation:** 1 SP

---

#### Story 2.4 — LlmAdapter: Wrapped LLM Client with Resilience

**Description:**
Create `LlmAdapter.java` in `rfp-service/src/main/java/com/dsi/rfp/adapter/llm/`. This class wraps the Spring AI
`ChatClient` and applies the full Resilience4j chain: `TimeLimiter → CircuitBreaker → RateLimiter → Retry`. It exposes
two methods: `extractStructured()` for structured JSON extraction (returns `Optional<T>`) and `judgeSnippet()` for
quality judgment (returns `Optional<String>`). All LLM calls are logged with provider, model, token estimates, latency,
and success/fail status.

**Acceptance Criteria:**

```gherkin
Given a valid LLM provider is configured
When extractStructured(systemPrompt, userContent, MyDto.class) is called
Then the LLM response is parsed to MyDto and returned as Optional.of(myDto)

Given the LLM returns malformed JSON
When extractStructured() tries to parse the response
Then LlmResponseParseException is thrown with the raw response (truncated to 200 chars) in the WARN log
And Resilience4j does NOT retry (retryOnException predicate excludes LlmResponseParseException)

Given the LLM is unreachable (connection refused)
When extractStructured() is called
Then Retry attempts 3 times (with backoff) before throwing LlmUnavailableException
And the circuit breaker registers the failure

Given 70 calls are made within 1 minute
When the rate limiter's 60-call limit is hit
Then call 61 blocks for up to 10s, then RequestNotPermitted is thrown
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/llm/LlmAdapter.java`

```java
package com.dsi.rfp.adapter.llm;

import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.exception.LlmResponseParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmAdapter {

    private final ChatClient chatClient;
    private final Retry llmRetry;
    private final CircuitBreaker llmCircuitBreaker;
    private final RateLimiter llmRateLimiter;
    private final TimeLimiter llmTimeLimiter;
    private final ObjectMapper objectMapper;
    private final LlmProviderProperties props;

    /**
     * Calls the extraction model (e.g. gemini-2.0-flash-001) with a system + user prompt.
     * Parses the JSON response into responseType.
     * Applies: TimeLimiter → CircuitBreaker → RateLimiter → Retry.
     *
     * @param systemPrompt the system prompt (from prompt file)
     * @param userContent  the user content (section text / chunk)
     * @param responseType the target class for JSON deserialization
     * @return Optional.of(parsed result) if LLM returns valid JSON
     * @throws LlmResponseParseException if the LLM response cannot be parsed into responseType
     *         (see parseResponse() — per exception policy, parse failures throw, not return empty)
     */
    public <T> Optional<T> extractStructured(
        String systemPrompt,
        String userContent,
        Class<T> responseType) { ...}

    /**
     * Calls the judge model (e.g. gemini-2.5-pro-preview-06-05) for quality assessment.
     *
     * @param prompt  the full judge prompt (system + injected snippet)
     * @param snippet the text being judged
     * @return Optional.of(judgment response) or Optional.empty() on failure
     *
     * NOTE [D]: Sprint 8 changes this signature to return {@code LlmJudgmentResult}
     * (a structured object with {@code finding}, {@code confidence}, {@code evidence},
     * and {@code status} fields) instead of {@code Optional<String>}.
     * Sprint 8 must update this method and all callers as part of its deliverables.
     */
    public Optional<String> judgeSnippet(String prompt, String snippet) { ...}

    private String callLlmRaw(String systemPrompt, String userContent) { ...}

    private String callJudgeLlmRaw(String fullPrompt) { ...}

    private <T> Optional<T> parseResponse(String rawResponse, Class<T> responseType) { ...}

    private void logLlmCall(String operation, String model, long startMs,
                            boolean success, String failReason) { ...}
}
```

**Detailed method implementations:**

`extractStructured()` — full implementation:

```java
public <T> Optional<T> extractStructured(String systemPrompt,
                                         String userContent,
                                         Class<T> responseType) {
    long startMs = System.currentTimeMillis();
    String model = props.getOpenrouter().getModel(); // or ollama model
    // Apply resilience chain: Retry(CircuitBreaker(RateLimiter(TimeLimiter(call))))
    Supplier<CompletableFuture<String>> futureSupplier =
        () -> CompletableFuture.supplyAsync(
            () -> callLlmRaw(systemPrompt, userContent),
            Executors.newVirtualThreadPerTaskExecutor()
        );

    Supplier<CompletableFuture<String>> rlDecorated =
        RateLimiter.decorateSupplier(llmRateLimiter, futureSupplier);
    Supplier<CompletableFuture<String>> cbDecorated =
        CircuitBreaker.decorateSupplier(llmCircuitBreaker, rlDecorated);
    Supplier<String> timedSupplier =
        () -> llmTimeLimiter.executeFutureSupplier(cbDecorated);
    String rawResponse = Retry.decorateSupplier(llmRetry, timedSupplier).get();
    Optional<T> result = parseResponse(rawResponse, responseType);
    logLlmCall("extract", model, startMs, true, null);
    return result;
}
```

`callLlmRaw()` implementation:

```java
private String callLlmRaw(String systemPrompt, String userContent) {
    return chatClient.prompt()
                     .system(systemPrompt)
                     .user(userContent)
                     .call()
                     .content();
}
```

`parseResponse()` implementation:

```java
private <T> Optional<T> parseResponse(String rawResponse, Class<T> responseType) {
    if (Objects.isNull(rawResponse) || !StringUtils.hasText(rawResponse)) {
        log.warn("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=WARN LLM returned empty response for type={}", responseType.getSimpleName());
        return Optional.empty();
    }
    // Strip markdown code fences if present
    String cleaned = rawResponse.strip();
    if (cleaned.startsWith("```json")) {
        cleaned = cleaned.substring(7);
    }
    if (cleaned.startsWith("```")) {
        cleaned = cleaned.substring(3);
    }
    if (cleaned.endsWith("```")) {
        cleaned = cleaned.substring(0, cleaned.length() - 3);
    }
    cleaned = cleaned.strip();

    try {
        T result = objectMapper.readValue(cleaned, responseType);
        return Optional.of(result);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        String preview = cleaned.substring(0, Math.min(200, cleaned.length()));
        log.warn("event=llm.parse.fail component=LlmAdapter jobId=NA durationMs=NA errorCode=LLM_PARSE_FAIL traceId={} spanId={} status=WARN type={} preview={}",
            MDC.get("traceId"), MDC.get("spanId"), responseType.getSimpleName(), preview);
        throw new LlmResponseParseException(
            "LLM response could not be parsed as " + responseType.getSimpleName(),
            cleaned,
            e);
    }
}
```

`logLlmCall()` implementation:

```java
private void logLlmCall(String operation, String model,
                        long startMs, boolean success, String failReason) {
    long latencyMs = System.currentTimeMillis() - startMs;
    if (success) {
        log.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO LLM_CALL op={} model={} provider={} latencyMs={} status=SUCCESS",
            operation, model, props.getProvider(), latencyMs);
    } else {
        log.warn("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=WARN LLM_CALL op={} model={} provider={} latencyMs={} status=FAIL reason={}",
            operation, model, props.getProvider(), latencyMs, failReason);
    }
    // Optional future metrics hook (deferred to wishlist; unit-test-only baseline)
    // Metrics.counter("llm.calls",
    //     "operation", operation, "model", model, "status", success ? "success" : "fail")
    //     .increment();
    // Metrics.timer("llm.latency", "operation", operation).record(latencyMs, TimeUnit.MILLISECONDS);
}
```

Note on tokens: OpenRouter does not return token counts synchronously in all clients. Log `tokensIn=estimated` based on
`userContent.length() / 4` (rough approximation) until proper usage tracking is added in Sprint 4.

**Dependencies:** Stories 2.1, 2.2, 2.3.

**Test Plan:**
Class: `LlmAdapterTest` in `rfp-service/src/test/java/com/dsi/rfp/adapter/llm/`

Mock: `ChatClient`, `ChatClient.ChatClientRequest`, `ChatClient.CallResponseSpec`. Use Mockito for all Spring AI
classes.

```java

@ExtendWith(MockitoExtension.class)
class LlmAdapterTest {
    @Mock
    ChatClient chatClient;
    @Mock
    ChatClient.ChatClientRequest requestSpec;
    @Mock
    ChatClient.CallResponseSpec callSpec;
    @InjectMocks
    LlmAdapter llmAdapter; // inject via constructor, not field

    @Test
    void shouldReturnParsedDtoWhenLlmReturnsValidJson() { ...}

    @Test
    void shouldThrowLlmResponseParseExceptionWhenLlmReturnsInvalidJson() { ...}

    @Test
    void shouldReturnEmptyWhenLlmReturnsBlankResponse() { ...}  // blank response → Optional.empty (no parse attempted)

    @Test
    void shouldStripMarkdownCodeFencesBeforeParsing() { ...}

    @Test
    void shouldThrowLlmUnavailableExceptionWhenChatClientThrows() { ...}

    @Test
    void shouldLogSuccessWhenExtractionSucceeds() { ...}

    @Test
    void shouldLogFailureWhenExtractionFails() { ...}
}
```

Since Resilience4j wrappers require real registry objects, construct them in `@BeforeEach` with default configs for
tests:

```java

@BeforeEach
void setUp() {
    Retry retry = RetryRegistry.ofDefaults().retry("test");
    CircuitBreaker cb = CircuitBreakerRegistry.ofDefaults().circuitBreaker("test");
    RateLimiter rl = RateLimiterRegistry.ofDefaults().rateLimiter("test");
    TimeLimiter tl = TimeLimiterRegistry.ofDefaults().timeLimiter("test");
    LlmProviderProperties props = new LlmProviderProperties();
    ObjectMapper om = new ObjectMapper();
    llmAdapter = new LlmAdapter(chatClient, retry, cb, rl, tl, om, props);
}
```

**Observability:**

- Log format: `LLM_CALL op={} model={} provider={} latencyMs={} status=SUCCESS|FAIL reason={}`
- Deferred to wishlist (unit-test-only baseline; no Actuator).

**Estimation:** 8 SP

---

### Epic 3 — Python OCR Sidecar Skeleton

#### Story 3.1 — FastAPI Skeleton with Health Endpoint

**Description:**
Create the `rfp-python-ocr/` directory with a minimal FastAPI application. Only the `/health` endpoint is implemented.
The OCR logic is a stub that raises `NotImplementedError`. This gives the Docker Compose networking something to
health-check against in Sprint 1.

**Acceptance Criteria:**

```gherkin
Given the FastAPI sidecar is running on port 8000
When GET /health is called
Then 200 OK is returned with body {"status": "ok", "version": "1.0.0"}

Given the OcrService.extract_page() method is called
When any page number is passed
Then NotImplementedError is raised with message "OCR not yet implemented, coming Sprint 6"
```

**Interfaces/Contracts:**

File: `rfp-python-ocr/main.py`:

```python
from contextlib import asynccontextmanager
from fastapi import FastAPI
from pydantic import BaseModel
from ocr_service import OcrService
import logging

logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO OCR sidecar starting up")
    app.state.ocr_service = OcrService()
    yield
    logger.info("event=sample component=sample jobId=NA durationMs=NA errorCode=NA traceId=NA spanId=NA status=INFO OCR sidecar shutting down")

app = FastAPI(title="RFP OCR Sidecar", version="1.0.0", lifespan=lifespan)

class HealthResponse(BaseModel):
    status: str
    version: str

@app.get("/health", response_model=HealthResponse)
async def health():
    return HealthResponse(status="ok", version="1.0.0")
```

File: `rfp-python-ocr/ocr_service.py`:

```python
from typing import Optional

class PageOcrResult:
    def __init__(self, page_number: int, text: str, confidence: float):
        self.page_number = page_number
        self.text = text
        self.confidence = confidence

class OcrService:
    def extract_page(self, page_image_bytes: bytes, page_number: int) -> PageOcrResult:
        raise NotImplementedError("OCR not yet implemented, coming Sprint 6")

    def extract_pages_batch(self, pdf_bytes: bytes) -> list[PageOcrResult]:
        raise NotImplementedError("OCR not yet implemented, coming Sprint 6")
```

File: `rfp-python-ocr/requirements.txt`:

```
fastapi==0.115.5
uvicorn[standard]==0.32.1
pydantic==2.10.3
Pillow==11.0.0
pdf2image==1.17.0
pytesseract==0.3.13
# easyocr==1.7.2  # Uncomment in Sprint 6 — requires GPU/CPU heavy install
```

File: `rfp-python-ocr/Dockerfile`:

```dockerfile
FROM python:3.11-slim
WORKDIR /app
RUN apt-get update && apt-get install -y tesseract-ocr poppler-utils && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

**Dependencies:** None.
**Estimation:** 3 SP

---

### Epic 4 — React Frontend Skeleton

#### Story 4.1 — Vite + React 18 + TypeScript + Tailwind v4 Setup

**Description:**
Scaffold the React frontend in `rfp-extractor/rfp-frontend/` using Vite with the React TypeScript template. Configure
Tailwind CSS v4. Create the routing structure, API client, TypeScript types, and page skeletons.

**Acceptance Criteria:**

```gherkin
Given the frontend directory
When `npm run dev` is executed
Then the Vite dev server starts on port 5173 with no TypeScript errors

Given the UploadPage
When a user selects a PDF file and clicks Submit
Then a POST request is sent to VITE_API_BASE_URL/api/v1/rfp/submit with the file

Given the App router
When the URL is /job/some-uuid
Then the JobStatusPage component renders

Given TypeScript strict mode is enabled
When `npm run build` is executed
Then the build completes with zero type errors
```

**Interfaces/Contracts:**

File: `rfp-frontend/src/api/rfpClient.ts`:

```typescript
import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

export const rfpClient = axios.create({
    baseURL: BASE_URL,
    timeout: 30_000,
    headers: {'Content-Type': 'application/json'},
});

export type JobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PARTIAL';

export async function submitRfp(file: File): Promise<{ jobId: string; status: JobStatus }> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await rfpClient.post('/api/v1/rfp/submit', formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    });
    return response.data;
}

export async function getJobStatus(jobId: string): Promise<JobStatusResponse> {
    const response = await rfpClient.get(`/api/v1/rfp/status/${jobId}`);
    return response.data;
}

export async function getRfpResult(jobId: string): Promise<RfpResultResponse> {
    const response = await rfpClient.get(`/api/v1/rfp/result/${jobId}`);
    return response.data;
}
```

File: `rfp-frontend/src/types/rfp.ts`:

```typescript
export interface DocMeta {
    title: string | null;
    procurementRef: string | null;
    issueDate: string | null;
    rfpType: string | null;
    sourceLanguage: string | null;
    extractionModel: string | null;
    extractionTimestamp: string | null;
}

export interface Section {
    id: string;
    title: string;
    level: number;
    pageStart: number;
    pageEnd: number;
    children: Section[];
    confidence: {
        score: number;
        method: string;
    };
}

export interface JobStatusResponse {
    jobId: string;
    status: JobStatus;
    progress: number;
    submittedAt: string;
    completedAt?: string;
    errorMessage?: string;
}

export interface RfpResultResponse {
    jobId: string;
    docMeta: DocMeta;
    sections: Section[];
}
```

File: `rfp-frontend/src/pages/UploadPage.tsx`:

```tsx
import React, {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {submitRfp} from '../api/rfpClient';

export function UploadPage() {
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const navigate = useNavigate();

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFile(e.target.files?.[0] ?? null);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!file) return;
        setUploading(true);
        setError(null);
        const {jobId} = await submitRfp(file);
        navigate(`/job/${jobId}`);
        setUploading(false);
    };

    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
            <form onSubmit={handleSubmit} className="bg-white p-8 rounded-xl shadow-md w-full max-w-md">
                <h1 className="text-2xl font-bold mb-6 text-gray-800">Upload RFP Document</h1>
                <input
                    type="file"
                    accept=".pdf,.docx"
                    onChange={handleFileChange}
                    className="mb-4 block w-full text-sm text-gray-500"
                    data-testid="file-input"
                />
                {error && <p className="text-red-500 text-sm mb-4">{error}</p>}
                <button
                    type="submit"
                    disabled={!file || uploading}
                    className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg disabled:opacity-50"
                    data-testid="submit-button"
                >
                    {uploading ? 'Uploading...' : 'Extract RFP Data'}
                </button>
            </form>
        </div>
    );
}
```

File: `rfp-frontend/src/App.tsx`:

```tsx
import {BrowserRouter, Routes, Route} from 'react-router-dom';
import {UploadPage} from './pages/UploadPage';
import {JobStatusPage} from './pages/JobStatusPage';
import {ResultPage} from './pages/ResultPage';

export function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<UploadPage/>}/>
                <Route path="/job/:jobId" element={<JobStatusPage/>}/>
                <Route path="/result/:jobId" element={<ResultPage/>}/>
            </Routes>
        </BrowserRouter>
    );
}
```

File: `rfp-frontend/src/pages/JobStatusPage.tsx` — stub:

```tsx
import {useParams} from 'react-router-dom';

export function JobStatusPage() {
    const {jobId} = useParams<{ jobId: string }>();
    return (
        <div className="p-8">
            <h1 className="text-xl font-bold">Job: {jobId}</h1>
            <p className="text-gray-500 mt-2">Status polling — implemented in Sprint 2.</p>
        </div>
    );
}
```

File: `rfp-frontend/src/pages/ResultPage.tsx` — stub:

```tsx
import {useParams} from 'react-router-dom';

export function ResultPage() {
    const {jobId} = useParams<{ jobId: string }>();
    return (
        <div className="p-8">
            <h1 className="text-xl font-bold">Result: {jobId}</h1>
            <p className="text-gray-500 mt-2">Results view — implemented in Sprint 3+.</p>
        </div>
    );
}
```

File: `rfp-frontend/.env.example`:

```
VITE_API_BASE_URL=http://localhost:8080
```

File: `rfp-frontend/vite.config.ts`:

```typescript
import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
    plugins: [react(), tailwindcss()],
    server: {
        port: 5173,
        proxy: {
            '/api': 'http://localhost:8080',
        },
    },
});
```

File: `rfp-frontend/tsconfig.json` — ensure `strict: true` is set.

**package.json dependencies to install:**

```bash
npm create vite@latest rfp-frontend -- --template react-ts
cd rfp-frontend
npm install axios react-router-dom @tanstack/react-query
npm install -D @tailwindcss/vite tailwindcss @types/node
```

**Dependencies:** None (frontend is independent).
**Estimation:** 5 SP

---

### Epic 5 — Health Endpoint

#### Story 5.1 — GET /api/v1/health with LLM and OCR Probes

**Description:**
Implement the health check endpoint that returns provider info, model name, and OCR sidecar reachability. The endpoint
application-level health for the UI.

**Acceptance Criteria:**

```gherkin
Given the service is running with provider=openrouter
When GET /api/v1/health is called
Then 200 OK is returned with body:
{
  "status": "UP",
  "provider": "openrouter",
  "model": "google/gemini-2.0-flash-001",
  "ocrSidecar": "reachable"
}

Given the OCR sidecar is not running
When GET /api/v1/health is called
Then 200 OK is returned with ocrSidecar="unreachable" (not 5xx — degraded state is OK)

Given provider=openrouter but LLM call fails
When GET /api/v1/health is called
Then status="DEGRADED" and ocrSidecar field still present
```

**Interfaces/Contracts:**

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/HealthController.java`:

```java
package com.dsi.rfp.adapter.api;

import com.dsi.rfp.application.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public ResponseEntity<HealthResponse> check() {
        return ResponseEntity.ok(healthService.check());
    }
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/adapter/api/HealthResponse.java`:

```java
package com.dsi.rfp.adapter.api;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthResponse {
    private String status;         // "UP" | "DEGRADED"
    private String provider;
    private String model;
    private String ocrSidecar;    // "reachable" | "unreachable"
}
```

File: `rfp-service/src/main/java/com/dsi/rfp/application/service/HealthService.java`:

```java
package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.api.HealthResponse;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.config.LlmProviderProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final LlmAdapter llmAdapter;
    private final LlmProviderProperties props;
    private final RestClient restClient;

    @Value("${app.ocr.sidecar-url}")
    private String ocrSidecarUrl;

    public HealthResponse check() {
        String ocrStatus = pingOcrSidecar();
        String overallStatus = "UP";  // degraded detection added in Sprint 2
        String model = "openrouter".equals(props.getProvider())
            ? props.getOpenrouter().getModel()
            : props.getOllama().getModel();
        return HealthResponse.builder()
                             .status(overallStatus)
                             .provider(props.getProvider())
                             .model(model)
                             .ocrSidecar(ocrStatus)
                             .build();
    }

    private String pingOcrSidecar() {
        restClient.get()
                  .uri(ocrSidecarUrl + "/health")
                  .retrieve()
                  .body(String.class);
        return "reachable";
    }
}
```

Add a `RestClient` bean to a simple `WebClientConfig.java` or inline in `LlmProviderConfig.java`:

```java

@Bean
public RestClient restClient() {
    return RestClient.builder()
                     .defaultHeader("Accept", "application/json")
                     .build();
}
```

**Dependencies:** Stories 2.1, 2.4 (LlmAdapter exists).
**Risks:** RestClient timeout — keep a fixed 2s connect timeout and let failures propagate to `GlobalExceptionHandler`.
Spring 6 `RestClient`
supports `.httpClientOptions()` for timeout.

**Test Plan:**
Class: `HealthServiceTest`

```java

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {
    @Mock
    LlmAdapter llmAdapter;
    @Mock
    RestClient restClient;
    // ... mock the RestClient chain

    @Test
    void shouldReturnReachableWhenOcrSidecarResponds() { ...}

    @Test
    void shouldReturnUnreachableWhenOcrSidecarThrows() { ...}

    @Test
    void shouldReturnProviderNameFromProperties() { ...}

    @Test
    void shouldReturnExtractionModelName() { ...}
}
```

**Observability:**

- Log WARN: `"OCR sidecar unreachable at {}: {}"`
- Log INFO: `"Health check: provider={} model={} ocr={}"`

**Estimation:** 5 SP

---

### Epic 6 — Docker Compose

#### Story 6.1 — docker-compose.yml with Full Stack

**Description:**
Write `rfp-extractor/docker-compose.yml` defining all four services (postgres, rfp-python-ocr, rfp-service,
rfp-frontend) with health checks, environment variable injection from `.env`, and proper service dependencies.
Redis is NOT in the stack — job state is in PostgreSQL.

**Acceptance Criteria:**

```gherkin
Given docker-compose.yml exists and .env is populated
When `docker compose up --build` is run
Then all four services start within 120s

Given the postgres service
When the health check runs
Then it uses pg_isready and reports healthy before rfp-service starts

Given the rfp-service
When it starts
Then it depends_on: [postgres, rfp-python-ocr] with condition: service_healthy
And no Redis service is defined in docker-compose.yml
```

**Interfaces/Contracts:**

File: `rfp-extractor/docker-compose.yml`:

```yaml
version : '3.9'

services:
    postgres      :
        image      : postgres:16-alpine
        environment:
            POSTGRES_DB      : ${POSTGRES_DB:-rfpdb}
            POSTGRES_USER    : ${POSTGRES_USER:-rfpuser}
            POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-rfppass}
        ports      :
            - "5432:5432"
        volumes    :
            - postgres_data:/var/lib/postgresql/data
        healthcheck:
            test        : [ "CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-rfpuser} -d ${POSTGRES_DB:-rfpdb}" ]
            interval    : 10s
            timeout     : 5s
            retries     : 5
            start_period: 20s

    rfp-python-ocr:
        build      :
            context   : ./rfp-python-ocr
            dockerfile: Dockerfile
        ports      :
            - "8000:8000"
        environment:
            PYTHONUNBUFFERED: "1"
        healthcheck:
            test        : [ "CMD", "curl", "-f", "http://localhost:8000/health" ]
            interval    : 15s
            timeout     : 5s
            retries     : 3
            start_period: 10s

    rfp-service   :
        build      :
            context   : .
            dockerfile: rfp-service/Dockerfile
        ports      :
            - "8080:8080"
        environment:
            SPRING_PROFILES_ACTIVE: docker
            OPENROUTER_API_KEY    : ${OPENROUTER_API_KEY}
            POSTGRES_USER         : ${POSTGRES_USER:-rfpuser}
            POSTGRES_PASSWORD     : ${POSTGRES_PASSWORD:-rfppass}
            POSTGRES_DB           : ${POSTGRES_DB:-rfpdb}
        depends_on :
            postgres      :
                condition: service_healthy
            rfp-python-ocr:
                condition: service_healthy
        healthcheck:
            test        : [ "CMD", "curl", "-f", "http://localhost:8080/api/v1/health" ]
            interval    : 30s
            timeout     : 10s
            retries     : 3
            start_period: 60s
        volumes    :
            - rfp_storage:/app/rfp-storage

    rfp-frontend  :
        build      :
            context   : ./rfp-frontend
            dockerfile: Dockerfile
        ports      :
            - "3000:80"
        depends_on :
            rfp-service:
                condition: service_healthy
        healthcheck:
            test    : [ "CMD", "curl", "-f", "http://localhost:80" ]
            interval: 30s
            timeout : 5s
            retries : 3

volumes :
    postgres_data:
    rfp_storage  :
```

Also create `rfp-service/Dockerfile`:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY rfp-core/pom.xml rfp-core/
COPY rfp-service/pom.xml rfp-service/
RUN mvn dependency:go-offline -pl rfp-core,rfp-service -am -q
COPY rfp-core/src rfp-core/src
COPY rfp-service/src rfp-service/src
RUN mvn clean package -pl rfp-core,rfp-service -am -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/rfp-service/target/*.jar app.jar
RUN mkdir -p /app/rfp-storage
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Also create `rfp-frontend/Dockerfile`:

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

Create `rfp-frontend/nginx.conf`:

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;
    }
    location /api/ {
        proxy_pass http://rfp-service:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

Also create `.env.example` at project root:

```
OPENROUTER_API_KEY=sk-or-v1-your-key-here
POSTGRES_DB=rfpdb
POSTGRES_USER=rfpuser
POSTGRES_PASSWORD=rfppass
```

**Dependencies:** Stories 3.1, 4.1, 5.1.
**Estimation:** 3 SP

---

### Epic 7 — Prompt Infrastructure

#### Story 7.1 — Prompt Directory and First Prompt File

**Description:**
Create the `prompts/` directory at project root with a format README and the first prompt file `entity-general-v1.md` as
a placeholder (real content in Sprint 4).

**Acceptance Criteria:**

```gherkin
Given the prompts/ directory
When entity-general-v1.md is read
Then it contains valid YAML frontmatter with id, version, model, max_tokens, temperature fields
And the body contains a system prompt section and a user template section
```

File: `rfp-extractor/prompts/entity-general-v1.md`:

```markdown
---
id: entity-general
version: 1
model: google/gemini-2.0-flash-001
max_tokens: 4096
temperature: 0.0
---

# System Prompt

You are an expert procurement document analyst. Extract general procurement metadata from the following RFP section
text.
Return ONLY valid JSON — no markdown, no explanation, no code fences.

## Required JSON fields:

- client_name (string | null)
- submission_deadline (ISO 8601 datetime string | null — append "BST assumed" note if no timezone)
- issue_date (ISO 8601 date string | null)
- method_of_selection (string | null)
- procurement_method (string | null)
- project_duration (string | null — e.g. "18 months")
- pre_bid_meeting (object | null — {date: string, venue: string})
- contact (object | null — {name: string, email: string, phone: string, address: string})

## Rules:

- If a field is not found, set it to null. Do NOT omit fields.
- For dates: parse Bangla numeral dates (e.g. ০১/০৩/২০২৫) to Gregorian.
- For deadlines: if only a date is given, assume 17:00 BST.

# User Template

DOCUMENT CONTEXT: {{doc_context}}

SECTION TEXT:
{{section_text}}

Extract the general procurement metadata from the section text above.
```

File: `rfp-extractor/prompts/README.md`:

```markdown
# Prompt File Format

All prompt files follow this convention:

## Filename: {name}-v{version}.md

## YAML Frontmatter (required):

- id: unique identifier matching the filename base
- version: integer version number
- model: the LLM model this prompt is tuned for
- max_tokens: maximum response tokens
- temperature: LLM temperature (0.0 = deterministic)

## Body sections:

1. # System Prompt — the system message sent to the LLM
2. # User Template — the user message with {{variable}} placeholders

## Variables:

- {{doc_context}} — "DOCUMENT: {title} | REF: {procurementRef} | TYPE: {rfpType}"
- {{section_text}} — the chunked section text
- {{additional_context}} — optional supplementary text

## Versioning:

- Bump version when prompt content changes materially.
- Keep old versions for reproducibility.
- Track which extraction was produced by which version via extraction_model field in RFP JSON.
```

**Estimation:** 2 SP

---

### Epic 8 — PostgreSQL DB Schema (Foundational, One-Time)

All JPA entities, all enums, all repositories. Nothing deferred to later sprints. `UserEntity` is a plain domain entity
here — Spring Security integration (password hashing, `UserDetailsService`, JWT) is added in Sprint 11.

#### Story 8.1 — BaseEntity and JpaConfig

**Description:**
Create the `BaseEntity` `@MappedSuperclass` that all JPA entities extend, providing UUID PK, `createdAt`, and
`updatedAt` audit fields. Add `JpaConfig` to enable JPA auditing.

**Interfaces/Contracts:**

File: `rfp-service/.../adapter/persistence/entity/BaseEntity.java`

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Data
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

File: `rfp-service/.../config/JpaConfig.java`

```java
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
```

**Acceptance Criteria:**

```gherkin
Given any entity extends BaseEntity
When the entity is persisted
Then id is auto-generated as a UUID, createdAt and updatedAt are set to current time
```

**Estimation:** 1 SP

---

#### Story 8.2 — Domain Enums (rfp-core)

**Description:**
Add the 5 new enums to `rfp-core` domain layer (`rfp-core/.../domain/model/`). These must have zero framework
dependencies.

**Interfaces/Contracts:**

```java
// AnalysisStatus.java
public enum AnalysisStatus {
    QUEUED, RUNNING, COMPLETED, FAILED, PARTIAL
}

// ExecutionStatus.java
public enum ExecutionStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
}

// TerminationReason.java
public enum TerminationReason {
    SUCCESS, ERROR, TIMEOUT, MAX_REPAIRS_EXCEEDED, USER_CANCELLED
}

// AgentStepType.java
public enum AgentStepType {
    VALIDATE, CLASSIFY_PAGES, EXTRACT_TEXT, SEGMENT_SECTIONS,
    EXTRACT_TABLES, EXTRACT_ENTITIES, SCORE_CONFIDENCE,
    REPAIR, RUN_RULES, FINALIZE
}

// StepOutcome.java
public enum StepOutcome {
    SUCCESS, SKIPPED, FAILED
}
```

**Acceptance Criteria:**

```gherkin
Given rfp-core/pom.xml
When inspecting enum class files
Then there are zero Spring or JPA imports
```

**Estimation:** 1 SP

---

#### Story 8.3 — UserEntity (Plain JPA, No Spring Security)

**Description:**
Create `UserEntity` as a plain JPA entity. No `UserDetails`, no `BCryptPasswordEncoder`, no Spring Security imports.
The password field is named `passwordHash` — it stores a bcrypt hash, but hashing logic is in Sprint 11.
Sprint 11 adds `JpaUserDetailsService` on top of this existing entity with zero new columns.

**Interfaces/Contracts:**

File: `rfp-service/.../adapter/persistence/entity/UserEntity.java`

```java
@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;           // UserRole enum from rfp-core (D-04, added Sprint 11)
                                     // Sprint 1 placeholder: use String role or pre-add UserRole enum

    @Column(nullable = false)
    private boolean enabled;
}
```

> **Note on `UserRole`:** Sprint 11 adds the `UserRole` enum (ANALYST/ADMIN/AUDITOR) to `rfp-core`. Sprint 1 must
> either (a) pre-add `UserRole` now as a placeholder enum, or (b) use `String role` and migrate in Sprint 11.
> **Decision: pre-add `UserRole` with values `ANALYST`, `ADMIN`, `AUDITOR` in Sprint 1** to avoid migration pain.
> `UserRole.java` location: `rfp-core/.../domain/model/UserRole.java`.

**Acceptance Criteria:**

```gherkin
Given the application starts
When Hibernate DDL auto runs
Then the "users" table is created with columns: id, username, password_hash, role, enabled, created_at, updated_at

Given no Spring Security on classpath wiring
When UserEntity.java is inspected
Then there are zero imports from org.springframework.security
```

**Estimation:** 2 SP

---

#### Story 8.4 — DocumentEntity and AnalysisJobEntity

**Description:**
Create the two core pipeline entities. `DocumentEntity` stores uploaded file metadata with a SHA-256 dedup guard.
`AnalysisJobEntity.id` is the system-wide `jobId` UUID used throughout the pipeline.

**Interfaces/Contracts:**

File: `rfp-service/.../adapter/persistence/entity/DocumentEntity.java`

```java
@Entity
@Table(name = "documents")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity extends BaseEntity {

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType;        // "application/pdf"

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false)
    private String storagePath;        // disk path under basePath

    @Column(nullable = false, unique = true)
    private String sha256Checksum;     // SHA-256 hex — dedup guard

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id")
    private UserEntity uploadedBy;     // FK wired Sprint 1 — nullable (pre-auth uploads)
}
```

File: `rfp-service/.../adapter/persistence/entity/AnalysisJobEntity.java`

```java
@Entity
@Table(name = "analysis_jobs")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisJobEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id")
    private UserEntity submittedBy;    // FK wired Sprint 1 — nullable (pre-auth submissions)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    private Instant startedAt;
    private Instant completedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private int progressPercent;       // 0–100, updated by each graph node
}
```

**Acceptance Criteria:**

```gherkin
Given a DocumentEntity is saved
When the same SHA-256 is submitted again
Then a DataIntegrityViolationException is thrown (unique constraint on sha256_checksum)

Given an AnalysisJobEntity is saved with status=QUEUED
When queried by id
Then the document FK is resolved and status is QUEUED
```

**Estimation:** 3 SP

---

#### Story 8.5 — AnalysisResultEntity, AgentExecutionEntity, AgentStepEntity, UserAuditEntity

**Description:**
Create the remaining four entities: structured extraction output, agent run tracking, per-node step tracking, and
user audit events.

**Interfaces/Contracts:**

File: `rfp-service/.../adapter/persistence/entity/AnalysisResultEntity.java`

```java
@Entity
@Table(name = "analysis_results")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJobEntity analysisJob;

    @Column(columnDefinition = "JSONB", nullable = false)
    private String resultJson;         // full RfpDocument serialised as JSON

    private String modelId;            // e.g. "google/gemma-3-27b-it"
    private String modelVersion;       // pinned model version logged at startup

    private int totalTokensUsed;

    @Column(precision = 5, scale = 4)
    private double overallConfidence;  // 0.0–1.0
}
```

File: `rfp-service/.../adapter/persistence/entity/AgentExecutionEntity.java`

```java
@Entity
@Table(name = "agent_executions")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentExecutionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_job_id", nullable = false)
    private AnalysisJobEntity analysisJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionStatus status;

    @Enumerated(EnumType.STRING)
    private TerminationReason terminationReason;

    private Instant startedAt;
    private Instant completedAt;
    private int totalSteps;
    private int completedSteps;
    private int repairIterations;      // total REPAIR node invocations in this run
}
```

File: `rfp-service/.../adapter/persistence/entity/AgentStepEntity.java`

```java
@Entity
@Table(name = "agent_steps",
       indexes = @Index(columnList = "execution_id, sequence"))
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStepEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private AgentExecutionEntity execution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStepType stepType;

    @Column(nullable = false)
    private int sequence;              // 0-based insertion order

    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    private StepOutcome outcome;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
```

File: `rfp-service/.../adapter/persistence/entity/UserAuditEntity.java`

```java
@Entity
@Table(name = "user_audit_events")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuditEntity extends BaseEntity {

    @Column(nullable = false)
    private String userId;             // username string (not FK — survives user deletion)

    @Column(nullable = false)
    private String action;             // AuditAction enum value stored as string

    private String documentId;         // nullable — UUID of related document

    @Column(nullable = false)
    private Instant timestamp;

    private String ipAddress;

    @Column(nullable = false)
    private boolean success;
}
```

**Acceptance Criteria:**

```gherkin
Given an AgentExecutionEntity with 10 AgentStepEntity children
When agent_steps is queried ordered by sequence
Then all 10 steps are returned in correct insertion order

Given an AnalysisResultEntity is saved
When the analysis_results table is inspected
Then resultJson column is of type jsonb (not text)
```

**Estimation:** 3 SP

---

#### Story 8.6 — Spring Data JPA Repositories

**Description:**
Create the 6 Spring Data JPA repository interfaces with the key query methods required by the pipeline.

**Interfaces/Contracts:**

```java
// DocumentRepository.java
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {
    Optional<DocumentEntity> findBySha256Checksum(String sha256Checksum);
    List<DocumentEntity> findByUploadedBy(UserEntity uploadedBy);
}

// AnalysisJobRepository.java
public interface AnalysisJobRepository extends JpaRepository<AnalysisJobEntity, UUID> {
    List<AnalysisJobEntity> findByDocumentId(UUID documentId);
    List<AnalysisJobEntity> findBySubmittedByAndStatus(UserEntity submittedBy, AnalysisStatus status);
    List<AnalysisJobEntity> findAllByStatus(AnalysisStatus status);
}

// AnalysisResultRepository.java
public interface AnalysisResultRepository extends JpaRepository<AnalysisResultEntity, UUID> {
    Optional<AnalysisResultEntity> findByAnalysisJobId(UUID analysisJobId);
}

// AgentExecutionRepository.java
public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, UUID> {
    List<AgentExecutionEntity> findByAnalysisJobId(UUID analysisJobId);
}

// AgentStepRepository.java
public interface AgentStepRepository extends JpaRepository<AgentStepEntity, UUID> {
    List<AgentStepEntity> findByExecutionIdOrderBySequence(UUID executionId);
}

// UserAuditRepository.java
public interface UserAuditRepository extends JpaRepository<UserAuditEntity, UUID> {
    List<UserAuditEntity> findByUserIdOrderByCreatedAtDesc(String userId);
    List<UserAuditEntity> findAllByOrderByCreatedAtDesc();
}
```

**Acceptance Criteria:**

```gherkin
Given a DocumentEntity saved with sha256Checksum "abc123"
When DocumentRepository.findBySha256Checksum("abc123") is called
Then the entity is returned

Given 10 AgentStepEntity rows with sequence 0–9 for the same execution
When AgentStepRepository.findByExecutionIdOrderBySequence(executionId) is called
Then 10 steps are returned in sequence order (0 first, 9 last)
```

**Test Plan:**

Use `@DataJpaTest` with an embedded database (H2 in PostgreSQL compatibility mode, or Testcontainers) for each
repository. Minimum 2 tests per repository covering the custom query methods.

**Estimation:** 3 SP

---

## 4) PR Plan

### PR 1: `feat/sprint1-infra` — Maven Multi-Module Build + Docker Compose

**Contains:**

- Parent `pom.xml` with all dependency versions.
- `rfp-core/pom.xml` + package-info.java files.
- `rfp-service/pom.xml` + `RfpApplication.java`.
- `application.properties` + `application-docker.properties`.
- `LlmProviderProperties.java`.
- `AsyncConfig.java`.
- `rfp-python-ocr/` (all files).
- `rfp-frontend/` (all files from Story 4.1).
- `docker-compose.yml` + Dockerfiles + `.env.example`.
- `prompts/` directory (README + entity-general-v1.md).
- Custom exception classes in `rfp-core`.

**Review Checklist:**

- [ ] Parent POM uses `<dependencyManagement>` — no version numbers in child POMs.
- [ ] `rfp-core/pom.xml` has zero Spring/LangChain4J/LangGraph4J dependencies.
- [ ] `mvn clean compile` passes on all modules.
- [ ] `LlmProviderProperties` uses `@ConfigurationProperties` (not `@Value` field injection).
- [ ] Docker Compose health checks defined for all four services (no Redis service present).
- [ ] `.env.example` committed; `.env` in `.gitignore`.
- [ ] `rfp-frontend/` has TypeScript strict mode enabled in `tsconfig.json`.
- [ ] `rfp-python-ocr/requirements.txt` has easyocr commented out.
- [ ] No hardcoded secrets in any committed file.
- [ ] Prompt file has valid YAML frontmatter (validate with a YAML linter).

---

### PR 2: `feat/sprint1-llm` — LLM Abstraction + Resilience + Health Endpoint

**Contains:**

- `LlmProviderConfig.java`.
- `LlmResilienceConfig.java`.
- `LlmAdapter.java`.
- `HealthController.java`.
- `HealthService.java`.
- `HealthResponse.java`.
- Unit tests: `LlmAdapterTest`, `LlmResilienceConfigTest`, `LlmProviderConfigTest`, `HealthServiceTest`,
  `LlmProviderPropertiesTest`.

**Review Checklist:**

- [ ] `LlmAdapter` uses constructor injection only — no `@Autowired` on fields.
- [ ] All LLM calls log at INFO with format: `LLM_CALL op={} model={} provider={} latencyMs={} status={}`.
- [ ] Resilience4j names match constants: `LLM_RETRY`, `LLM_CB`, `LLM_RL`, `LLM_TIMEOUT`.
- [ ] `parseResponse()` strips markdown code fences before JSON parse.
- [ ] `LlmAdapter` methods return `Optional<T>` — not null.
- [ ] `LlmUnavailableException` is NOT a `LlmResponseParseException` (separate hierarchy).
- [ ] Unit tests use `should{Behaviour}When{Condition}` naming convention.
- [ ] All assertions use AssertJ (no JUnit 5 `assertEquals`).
- [ ] No Spring context loaded in any unit test (`@ExtendWith(MockitoExtension.class)` only).
- [ ] Health endpoint returns `ocrSidecar: "unreachable"` (not 5xx) when sidecar is down.
- [ ] Classes are under 250 lines. Methods under 20 lines.

---

### PR 3: `feat/sprint1-db-schema` — PostgreSQL DB Schema (all entities, enums, repositories)

**Contains:**

- Enums in `rfp-core`: `AnalysisStatus`, `ExecutionStatus`, `TerminationReason`, `AgentStepType`, `StepOutcome`.
- `BaseEntity.java` (MappedSuperclass).
- `UserEntity.java` (plain JPA entity — no Spring Security wiring).
- `DocumentEntity.java`, `AnalysisJobEntity.java`, `AnalysisResultEntity.java`,
  `AgentExecutionEntity.java`, `AgentStepEntity.java`, `UserAuditEntity.java`.
- `DocumentRepository`, `AnalysisJobRepository`, `AnalysisResultRepository`,
  `AgentExecutionRepository`, `AgentStepRepository`, `UserAuditRepository`.
- `JpaConfig.java` (enables `@EnableJpaAuditing`).
- Unit tests for each repository using `@DataJpaTest` with embedded H2 or Testcontainers PostgreSQL.

**Review Checklist:**

- [ ] All entities extend `BaseEntity`. No entity has its own `@Id` field.
- [ ] All `@Enumerated` fields use `EnumType.STRING` (never `ORDINAL`).
- [ ] `AnalysisJobEntity.id` == `ExtractionJob.jobId` (same UUID used system-wide).
- [ ] `DocumentEntity.sha256Checksum` is `unique = true`.
- [ ] `AgentStepEntity` has `@Index(columnList = "execution_id, sequence")`.
- [ ] `AnalysisResultEntity.resultJson` is `columnDefinition = "JSONB"`.
- [ ] No Spring Security imports in any entity class — `UserEntity` is plain JPA only.
- [ ] `@EnableJpaAuditing` is present (in `JpaConfig` or equivalent).
- [ ] `mvn test` — Hibernate DDL auto-creates all 6 tables on startup.
- [ ] `grep -r "RedisTemplate\|RedisConnectionFactory\|spring-boot-starter-data-redis" rfp-service/` returns nothing.

---

## 5) Validation & Demo Script

### Step 1: Build Verification

```bash
cd rfp-extractor
mvn test
# Expected: BUILD SUCCESS, 0 failures, 0 errors
# Expected: at least 10 tests run
```

### Step 2: Start Full Stack

```bash
cp .env.example .env
# Edit .env and set OPENROUTER_API_KEY=sk-or-v1-your-actual-key

docker compose up --build
# Wait ~90 seconds for all services to be healthy
```

Expected output:

```
rfp-service-1       | Started RfpApplication in 8.342 seconds
rfp-python-ocr-1    | INFO:     Application startup complete.
rfp-frontend-1      | nginx: configuration file /etc/nginx/nginx.conf test is successful
```

### Step 3: Verify Health Endpoint

```bash
curl -s http://localhost:8080/api/v1/health | jq .
```

Expected output:

```json
{
    "status": "UP",
    "provider": "openrouter",
    "model": "google/gemini-2.0-flash-001",
    "ocrSidecar": "reachable"
}
```

### Step 4: Verify OCR Sidecar Health

```bash
curl -s http://localhost:8000/health | jq .
```

Expected output:

```json
{
    "status": "ok",
    "version": "1.0.0"
}
```

### Step 5: Verify Frontend

```bash
curl -s http://localhost:3000 | grep -o '<title>.*</title>'
```

Expected: `<title>Vite + React + TS</title>` (or configured title)

### Step 6: Verify OCR Stub Raises NotImplementedError

```bash
docker compose exec rfp-python-ocr python3 -c "
from ocr_service import OcrService
s = OcrService()
try:
    s.extract_page(b'', 1)
except NotImplementedError as e:
    print('PASS:', e)
"
```

Expected: `PASS: OCR not yet implemented, coming Sprint 6`

### Step 7: LLM Resilience Manual Test (optional — requires API key)

```bash
# Simulate the LLM being called from the health check (if health check makes LLM test call)
curl -s http://localhost:8080/api/v1/health
# Check logs for LLM_CALL log line:
docker compose logs rfp-service | grep "LLM_CALL"
```

Expected log line:

```
INFO  LLM_CALL op=health-ping model=google/gemini-2.0-flash-001 provider=openrouter latencyMs=1234 status=SUCCESS
```

### Step 8: Verify DB Schema (PostgreSQL tables auto-created)

```bash
# Connect to PostgreSQL and verify all 6 tables exist
docker compose exec postgres psql -U rfpuser -d rfpdb -c "\dt"
```

Expected output includes all tables:

```
 Schema |       Name        | Type  |  Owner
--------+-------------------+-------+---------
 public | agent_executions  | table | rfpuser
 public | agent_steps       | table | rfpuser
 public | analysis_jobs     | table | rfpuser
 public | analysis_results  | table | rfpuser
 public | documents         | table | rfpuser
 public | user_audit_events | table | rfpuser
 public | users             | table | rfpuser
```

```bash
# Verify no Redis connection beans in Spring context
docker compose logs rfp-service | grep -i redis
# Expected: no output (zero Redis references in logs)
```

```bash
# Verify analysis_jobs table structure
docker compose exec postgres psql -U rfpuser -d rfpdb \
  -c "\d analysis_jobs"
# Expected: columns id (uuid), document_id (uuid FK), submitted_by_id (uuid FK),
#           status (varchar), started_at, completed_at, error_message, progress_percent,
#           created_at, updated_at
```

```bash
# Verify agent_steps index
docker compose exec postgres psql -U rfpuser -d rfpdb \
  -c "SELECT indexname FROM pg_indexes WHERE tablename = 'agent_steps';"
# Expected: agent_steps_execution_id_sequence_idx (composite index)
```

### Performance Checks:

- Spring Boot startup time < 15s.
- `GET /api/v1/health` response time < 3s (OCR ping has 2s timeout).
- `mvn test` completes in < 3 minutes on a standard laptop.

---

## 6) Exit Criteria (NON-NEGOTIABLE)

- [ ] `mvn test` exits with code 0 on all modules. Zero compilation errors. Zero test failures.
- [ ] `rfp-core` module has zero dependencies with `groupId` starting with `org.springframework`, `dev.langchain4j`, or
  `org.bsc.langgraph4j`. Verified by `mvn dependency:analyze`.
- [ ] `GET /api/v1/health` returns HTTP 200 with `status`, `provider`, `model`, and `ocrSidecar` fields — verified by
  `curl` in demo script.
- [ ] `GET /health` on port 8000 returns `{"status":"ok","version":"1.0.0"}` — verified by `curl`.
- [ ] Runtime smoke checks are optional and non-blocking (unit-test-only baseline).
- [ ] `LlmAdapter.extractStructured()` returns `Optional.empty()` (not null, not exception) when LLM returns malformed
  JSON — verified by unit test `shouldReturnEmptyWhenLlmReturnsInvalidJson`.
- [ ] Resilience4j retry is configured with exactly 3 max attempts — verified by
  `LlmResilienceConfigTest.shouldConfigureRetryWithThreeAttempts()`.
- [ ] All unit test class names follow `should{Behaviour}When{Condition}` naming — verified by code review.
- [ ] No `@Autowired` field injection anywhere in the codebase — verified by
  `grep -r "@Autowired" rfp-service/src/main`.
- [ ] `LlmProviderProperties` class has `@ConfigurationProperties(prefix = "app.llm")` annotation.
- [ ] `prompts/entity-general-v1.md` has YAML frontmatter with all 5 required keys (id, version, model, max_tokens,
  temperature).
- [ ] `.env` is in `.gitignore` — verified by `git check-ignore .env`.
- [ ] Frontend builds with zero TypeScript errors: `cd rfp-frontend && npm run build` exits 0.
- [ ] Each Java class is under 250 lines. Each method is under 20 lines. Verified during code review.

**DB Schema Exit Criteria (NON-NEGOTIABLE):**

- [ ] Hibernate DDL auto-creates all 7 tables on startup: `documents`, `analysis_jobs`, `analysis_results`,
  `agent_executions`, `agent_steps`, `user_audit_events`, `users`. Verified by `\dt` in psql.
- [ ] No `RedisConnectionFactory`, `RedisTemplate`, or `spring-boot-starter-data-redis` in Spring context or POM.
  Verified by `grep -r "RedisTemplate\|spring-boot-starter-data-redis" rfp-service/`.
- [ ] `DocumentEntity.sha256Checksum` has UNIQUE constraint — verified by `\d documents` in psql.
- [ ] `AgentStepEntity` table has composite index on `(execution_id, sequence)` — verified by `pg_indexes` query.
- [ ] `AnalysisResultEntity.resultJson` column is of type `jsonb` — verified by `\d analysis_results` in psql.
- [ ] All enum columns use `VARCHAR` storage (not integer) — verified by `\d analysis_jobs` showing `character varying`.
- [ ] `UserEntity` has no Spring Security imports — verified by code review of `UserEntity.java`.

---

## 7) Notes: Assumptions / Open Questions

**Assumption:** Spring AI 1.0.0 GA is available on Maven Central with the artifact IDs listed in the POM. If a different
GA version is the latest, use the latest GA version. Do NOT use SNAPSHOT versions.

**Assumption:** `org.bsc.langgraph4j:langgraph4j-core` is available on Maven Central. If not found, add the JitPack
repository to the parent POM: `<repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>` and change the
groupId/artifactId accordingly.

**Assumption:** OpenRouter is fully compatible with the Spring AI OpenAI-compatible client when the base URL is set to
`https://openrouter.ai/api/v1`. If there are auth header differences (e.g., `HTTP-Referer` header required by
OpenRouter), add them via a `ClientHttpRequestInterceptor` in the `LlmProviderConfig`.

**Assumption:** The
`@ConditionalOnProperty(name = "app.llm.provider", havingValue = "openrouter", matchIfMissing = true)` approach works
correctly. If two `ChatClient` beans are accidentally registered (e.g., autoconfiguration also registers one), add
`@Primary` to the beans defined in `LlmProviderConfig` and suppress autoconfigured beans via
`spring.ai.openai.chat.enabled=false` in properties.

**Open Question:** Should the health endpoint make an actual LLM API call (to verify the API key is valid) or just check
connectivity? In Sprint 1, it only pings the OCR sidecar. LLM key validation happens at startup via `@PostConstruct`. A
live LLM ping in the health endpoint would consume tokens and add latency. Decision: defer live LLM ping to a separate
`/api/v1/health/full` endpoint in Sprint 2.

**Open Question:** Does the `rfp-frontend` Nginx container need CORS headers? In Docker Compose, the frontend proxies to
`rfp-service:8080` via Nginx, so CORS headers are not needed. For local `npm run dev`, the Vite proxy handles it.
Confirm with team before Sprint 2.

**Assumption:** Java virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) are used in `LlmAdapter` for the
CompletableFuture supplier. Java 21 supports virtual threads natively. No additional configuration needed beyond JDK 21.

**Architecture Decision — Redis removed from stack:** Redis is removed from the system entirely. Job state, analysis
results, and agent execution traces are stored permanently in PostgreSQL via JPA. All planned Redis deliverables
(`RedisJobStateRepository`, `RedisUserAuditRepository`, `RedisConfig`) are replaced by JPA equivalents.
`JobStatePort` gets a JPA implementation backed by `AnalysisJobRepository`; `UserAuditPort` gets a JPA
implementation backed by `UserAuditRepository`. The 24-hour TTL limitation is eliminated — all rows are permanent.

**Architecture Decision — UserEntity FK wiring in Sprint 1:** `DocumentEntity.uploadedBy` and
`AnalysisJobEntity.submittedBy` FKs to `UserEntity` are created in Sprint 1 but are nullable. Before Sprint 11
adds authentication, the FKs are simply null. Sprint 11 populates them from the JWT principal — zero schema changes
needed in Sprint 11.

**Architecture Decision — UserRole enum pre-added in Sprint 1:** `UserRole` (ANALYST/ADMIN/AUDITOR) is added to
`rfp-core` in Sprint 1, even though it is only used by Sprint 11 Spring Security. This avoids a schema migration
in Sprint 11. `UserEntity.role` column is populated at signup in Sprint 11; pre-Sprint-11 entities have role=null
(the column is nullable in Sprint 1).
