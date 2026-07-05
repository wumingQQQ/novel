# ragservice Redis Vector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Dubbo-based `rag-api` contract module and Redis VectorStore-backed `ragservice` module for the new role runtime RAG chain.

**Architecture:** `rag-api` is a dependency-only contract jar with DTOs, enums, and Dubbo facades. `ragservice` is an independent Spring Boot provider that owns embedding, Redis Search index creation commands, metadata schema validation, rerank, and search orchestration. Existing `chat` and `novel` modules are not migrated in this first implementation slice.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Apache Dubbo Triple, Spring AI OpenAI embedding, Spring AI Redis VectorStore, JUnit 5.

---

## File Structure

- Create `rag-api/pom.xml`: Maven jar module for Dubbo contracts.
- Create `rag-api/src/main/java/com/wuming/api/rag/RagIndexFacade.java`: index lifecycle facade.
- Create `rag-api/src/main/java/com/wuming/api/rag/RoleRuntimeRagFacade.java`: role runtime search facade.
- Create `rag-api/src/main/java/com/wuming/api/rag/enums/IndexStatus.java`: create-index status enum.
- Create `rag-api/src/main/java/com/wuming/api/rag/enums/MetadataFieldType.java`: Redis Search metadata field type enum.
- Create `rag-api/src/main/java/com/wuming/api/rag/dto/*.java`: serializable request/response DTOs.
- Modify `pom.xml`: add `rag-api` and `ragservice` modules.
- Create `ragservice/pom.xml`: Spring Boot service module.
- Create `ragservice/src/main/java/com/wuming/rag/RagServiceApplication.java`: service entrypoint.
- Create `ragservice/src/main/java/com/wuming/rag/config/RagServiceProperties.java`: configuration binding.
- Create `ragservice/src/main/java/com/wuming/rag/config/RagVectorConfig.java`: embedding, Redis, and vector store beans.
- Create `ragservice/src/main/java/com/wuming/rag/index/*`: dynamic index definition storage and validation.
- Create `ragservice/src/main/java/com/wuming/rag/vector/redis/*`: Redis VectorStore adapter.
- Create `ragservice/src/main/java/com/wuming/rag/search/*`: search orchestration.
- Create `ragservice/src/main/java/com/wuming/rag/rerank/*`: optional HTTP rerank with fallback.
- Create `ragservice/src/main/java/com/wuming/rag/integration/rpc/*`: Dubbo facade implementations.
- Create `ragservice/src/test/java/com/wuming/rag/index/*Test.java`: metadata validation tests.
- Create `ragservice/src/main/resources/application.yml`: service defaults.

### Task 1: Add `rag-api` Contract Module

**Files:**
- Create: `rag-api/pom.xml`
- Create: `rag-api/src/main/java/com/wuming/api/rag/RagIndexFacade.java`
- Create: `rag-api/src/main/java/com/wuming/api/rag/RoleRuntimeRagFacade.java`
- Create: `rag-api/src/main/java/com/wuming/api/rag/enums/IndexStatus.java`
- Create: `rag-api/src/main/java/com/wuming/api/rag/enums/MetadataFieldType.java`
- Create: `rag-api/src/main/java/com/wuming/api/rag/dto/*.java`
- Modify: `pom.xml`

- [ ] **Step 1: Add module entries**

Add `rag-api` and `ragservice` to the root Maven modules so the reactor can build them.

- [ ] **Step 2: Create `rag-api` Maven module**

Create a jar module that depends only on Lombok. No Spring AI, Redis, or Dubbo implementation dependencies belong in `rag-api`.

- [ ] **Step 3: Define enums and DTOs**

Create serializable DTO classes for create-index, upsert, delete, and search operations. `CreateIndexRequest` must carry `indexName`, `keyPrefix`, `vectorDimension`, and `metadataFields`; later upsert/delete/search requests use `indexName`.

- [ ] **Step 4: Define Dubbo facade interfaces**

Create `RagIndexFacade` and `RoleRuntimeRagFacade` exactly as specified in the design doc.

- [ ] **Step 5: Verify `rag-api` compiles**

Run: `mvn -pl rag-api -am test`

Expected: reactor builds `rag-api` successfully.

- [ ] **Step 6: Commit**

Commit message: `feat: add rag api contract module`

### Task 2: Add `ragservice` Skeleton and Configuration

**Files:**
- Create: `ragservice/pom.xml`
- Create: `ragservice/src/main/java/com/wuming/rag/RagServiceApplication.java`
- Create: `ragservice/src/main/java/com/wuming/rag/config/RagServiceProperties.java`
- Create: `ragservice/src/main/java/com/wuming/rag/config/RagVectorConfig.java`
- Create: `ragservice/src/main/resources/application.yml`

- [ ] **Step 1: Create service Maven module**

Create a Spring Boot app module with dependencies on `rag-api`, `common`, Dubbo, Redis, Spring AI OpenAI model starter, Spring AI Redis store, and test starter.

- [ ] **Step 2: Add application entrypoint**

Create `RagServiceApplication` with `@SpringBootApplication` and `@EnableDubbo`.

- [ ] **Step 3: Add configuration properties**

Create typed properties for embedding, Redis indexes, retrieve defaults, reranker, and `enableRerank`.

- [ ] **Step 4: Add vector configuration**

Create beans for `EmbeddingModel` and `JedisPooled`. Do not predefine one `VectorStore` bean per logical index.

- [ ] **Step 5: Verify service module compiles**

Run: `mvn -pl ragservice -am test`

Expected: compilation succeeds.

- [ ] **Step 6: Commit**

Commit message: `feat: add ragservice spring boot skeleton`

### Task 3: Implement Index Definition and Metadata Validation

**Files:**
- Create: `ragservice/src/main/java/com/wuming/rag/index/RagIndexDefinition.java`
- Create: `ragservice/src/main/java/com/wuming/rag/index/RedisIndexDefinitionStore.java`
- Create: `ragservice/src/main/java/com/wuming/rag/index/MetadataValidator.java`
- Create: `ragservice/src/test/java/com/wuming/rag/index/MetadataValidatorTest.java`

- [ ] **Step 1: Write metadata validation tests**

Cover:
- metadata declared in create-index schema passes.
- unknown metadata field fails.
- blank document text fails.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `mvn -pl ragservice -Dtest=MetadataValidatorTest test`

Expected: fail because validator classes do not exist.

- [ ] **Step 3: Implement registry and validator**

Implement metadata validation from the persisted dynamic index definition.

- [ ] **Step 4: Run validator tests**

Run: `mvn -pl ragservice -Dtest=MetadataValidatorTest test`

Expected: pass.

- [ ] **Step 5: Commit**

Commit message: `feat: validate rag document metadata`

### Task 4: Implement Redis Vector Adapter and Facades

**Files:**
- Create: `ragservice/src/main/java/com/wuming/rag/vector/redis/RedisVectorIndexService.java`
- Create: `ragservice/src/main/java/com/wuming/rag/search/RoleRuntimeSearchService.java`
- Create: `ragservice/src/main/java/com/wuming/rag/rerank/RerankDocument.java`
- Create: `ragservice/src/main/java/com/wuming/rag/rerank/RerankedDocument.java`
- Create: `ragservice/src/main/java/com/wuming/rag/rerank/RerankService.java`
- Create: `ragservice/src/main/java/com/wuming/rag/rerank/HttpRerankService.java`
- Create: `ragservice/src/main/java/com/wuming/rag/integration/rpc/RagIndexFacadeImpl.java`
- Create: `ragservice/src/main/java/com/wuming/rag/integration/rpc/RoleRuntimeRagFacadeImpl.java`

- [ ] **Step 1: Implement index definition store**

Persist and load dynamic index definitions in Redis using key `rag:index-definition:{indexName}`.

- [ ] **Step 2: Implement Redis vector index service**

Implement create-index through Redis Search commands, then implement document upsert/delete and similarity search with filter expressions.

- [ ] **Step 3: Implement search service**

Validate requests, apply default topK, run vector search, optionally rerank, apply thresholds, and convert hits to `SearchResult`.

- [ ] **Step 4: Implement Dubbo facade adapters**

Catch runtime exceptions and convert them to structured response codes.

- [ ] **Step 5: Verify compile**

Run: `mvn -pl ragservice -am test`

Expected: pass.

- [ ] **Step 6: Commit**

Commit message: `feat: implement ragservice redis vector facades`

### Task 5: Reactor Verification

**Files:**
- Modify only if compilation reveals missing dependency wiring.

- [ ] **Step 1: Run full Maven test suite**

Run: `mvn test`

Expected: all modules compile and tests pass.

- [ ] **Step 2: Review dependency isolation**

Confirm `rag-api` has no Spring AI or Redis dependencies.

- [ ] **Step 3: Commit any final wiring fixes**

Commit message: `chore: verify ragservice reactor build`
