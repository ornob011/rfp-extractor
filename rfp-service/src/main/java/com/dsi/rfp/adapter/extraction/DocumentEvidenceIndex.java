package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.DocumentChunk;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

@Component
public class DocumentEvidenceIndex {

    public RetrievalResult<DocumentChunk> retrieveChunks(
        List<DocumentChunk> chunks,
        List<String> queries,
        int topK,
        double minimumScore
    ) {
        try (IndexedSearchContext<DocumentChunk> context = buildChunkContext(chunks)) {
            return retrieve(
                context,
                queries,
                topK,
                minimumScore
            );
        }
    }

    public RetrievalResult<Integer> retrievePageNumbers(
        Map<Integer, String> pageTexts,
        List<Integer> pageNumbers,
        List<String> queries,
        int topK,
        double minimumScore
    ) {
        try (IndexedSearchContext<Integer> context = buildPageContext(
            pageTexts,
            pageNumbers
        )) {
            return retrieve(
                context,
                queries,
                topK,
                minimumScore
            );
        }
    }

    private IndexedSearchContext<DocumentChunk> buildChunkContext(List<DocumentChunk> chunks) {
        ByteBuffersDirectory directory = new ByteBuffersDirectory();
        Map<String, DocumentChunk> payloadById = new LinkedHashMap<>();

        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            for (DocumentChunk chunk : chunks) {
                String identifier = String.valueOf(chunk.getChunkIndex());
                payloadById.put(identifier, chunk);
                writer.addDocument(chunkDocument(
                    identifier,
                    chunk.getContextHeader(),
                    chunk.getRawText()
                ));
            }
            writer.commit();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return searchContext(
            directory,
            payloadById
        );
    }

    private IndexedSearchContext<Integer> buildPageContext(
        Map<Integer, String> pageTexts,
        List<Integer> pageNumbers
    ) {
        ByteBuffersDirectory directory = new ByteBuffersDirectory();
        Map<String, Integer> payloadById = new LinkedHashMap<>();

        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            for (Integer pageNumber : pageNumbers) {
                String identifier = String.valueOf(pageNumber);
                payloadById.put(identifier, pageNumber);
                writer.addDocument(chunkDocument(
                    identifier,
                    String.format("page %d", pageNumber),
                    pageTexts.getOrDefault(pageNumber, "")
                ));
            }
            writer.commit();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        return searchContext(
            directory,
            payloadById
        );
    }

    private <T> RetrievalResult<T> retrieve(
        IndexedSearchContext<T> context,
        List<String> queries,
        int topK,
        double minimumScore
    ) {
        Map<String, Double> scores = new LinkedHashMap<>();

        queries.forEach(query -> scoreQuery(
            context,
            query,
            topK,
            scores
        ));

        double bestScore = scores.values()
                                 .stream()
                                 .max(Comparator.naturalOrder())
                                 .orElse(0.0);

        List<ScoredPayload<T>> ranked = scores.entrySet()
                                              .stream()
                                              .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                                              .limit(topK)
                                              .map(entry -> new ScoredPayload<>(
                                                  context.payloadById().get(entry.getKey()),
                                                  entry.getValue()
                                              ))
                                              .toList();

        List<ScoredPayload<T>> matches = ranked.stream()
                                               .filter(entry -> entry.score() >= minimumScore)
                                               .toList();

        return new RetrievalResult<>(
            ranked.stream().map(ScoredPayload::payload).toList(),
            matches.stream().map(ScoredPayload::payload).toList(),
            bestScore
        );
    }

    private <T> void scoreQuery(
        IndexedSearchContext<T> context,
        String queryString,
        int topK,
        Map<String, Double> scores
    ) {
        try {
            Query query = new MultiFieldQueryParser(
                new String[]{"context", "content"},
                new StandardAnalyzer()
            ).parse(queryString);

            TopDocs docs = context.searcher().search(
                query,
                topK
            );

            for (ScoreDoc scoreDoc : docs.scoreDocs) {
                Document document = context.searcher().storedFields().document(scoreDoc.doc);
                String identifier = document.get("id");
                scores.merge(
                    identifier,
                    (double) scoreDoc.score,
                    Math::max
                );
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (ParseException exception) {
            throw new IllegalArgumentException(
                String.format("Failed to parse retrieval query: %s", queryString),
                exception
            );
        }
    }

    private <T> IndexedSearchContext<T> searchContext(
        ByteBuffersDirectory directory,
        Map<String, T> payloadById
    ) {
        try {
            DirectoryReader reader = DirectoryReader.open(directory);
            return new IndexedSearchContext<>(
                reader,
                new IndexSearcher(reader),
                payloadById
            );
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Document chunkDocument(
        String identifier,
        String contextHeader,
        String content
    ) {
        Document document = new Document();
        document.add(new StoredField("id", identifier));
        document.add(new TextField("context", Optional.ofNullable(contextHeader).orElse(""), Field.Store.NO));
        document.add(new TextField("content", Optional.ofNullable(content).orElse(""), Field.Store.NO));
        return document;
    }

    public record RetrievalResult<T>(
        List<T> rankedItems,
        List<T> items,
        double bestScore
    ) {
    }

    private record IndexedSearchContext<T>(
        DirectoryReader reader,
        IndexSearcher searcher,
        Map<String, T> payloadById
    ) implements AutoCloseable {

        @Override
        public void close() {
            try {
                reader.close();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private record ScoredPayload<T>(
        T payload,
        double score
    ) {
    }
}
