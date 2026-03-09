package com.dsi.rfp.adapter.entity.contract;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.tables.*;
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class PromptContractParser {

    private final Parser parser;
    private final PromptContractParserConfig config;

    public PromptContractParser(PromptContractParserConfig config) {
        this.config = config;

        MutableDataSet options = new MutableDataSet()
            .set(
                Parser.EXTENSIONS,
                List.of(
                    TablesExtension.create(),
                    YamlFrontMatterExtension.create()
                )
            );

        parser = Parser.builder(options).build();
    }

    public Set<String> parseFieldKeys(Resource resource) {
        Node document = parse(resource);
        TableBlock fieldTable = findFieldTable(document);

        return readFieldColumn(fieldTable);
    }

    public String parseFrontmatterId(Resource resource) {
        Node document = parse(resource);
        AbstractYamlFrontMatterVisitor visitor = new AbstractYamlFrontMatterVisitor();
        visitor.visit(document);

        List<String> ids = visitor.getData().get("id");

        if (ids == null) {
            throw new EntityMetadataContractException("Prompt frontmatter must define 'id'");
        }

        return ids.stream()
                  .findFirst()
                  .orElseThrow(() -> new EntityMetadataContractException(
                      "Prompt frontmatter 'id' must contain a value"
                  ));
    }

    private Node parse(Resource resource) {
        String markdown = read(resource);
        return parser.parse(markdown);
    }

    private String read(Resource resource) {
        try (InputStream input = resource.getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SystemIoException(
                "Failed to read prompt resource",
                exception
            );
        }
    }

    private TableBlock findFieldTable(Node document) {
        List<Node> blocks = directChildren(document);
        int sectionIndex = indexOfFieldsSection(blocks);
        List<Node> trailingBlocks = blocks.subList(sectionIndex + 1, blocks.size());

        return trailingBlocks.stream()
                             .filter(TableBlock.class::isInstance)
                             .map(TableBlock.class::cast)
                             .findFirst()
                             .orElseThrow(() -> new EntityMetadataContractException(
                                 String.format(
                                     "No table found after section heading '%s'",
                                     config.fieldsSectionHeading()
                                 )
                             ));
    }

    private int indexOfFieldsSection(List<Node> blocks) {
        return IntStream.range(0, blocks.size())
                        .filter(index -> headingMatches(blocks.get(index)))
                        .findFirst()
                        .orElseThrow(() -> new EntityMetadataContractException(
                            String.format(
                                "Section heading '%s' is missing",
                                config.fieldsSectionHeading()
                            )
                        ));
    }

    private boolean headingMatches(Node node) {
        return switch (node) {
            case Heading heading -> text(heading).equalsIgnoreCase(config.fieldsSectionHeading());
            case null, default -> false;
        };
    }

    private Set<String> readFieldColumn(TableBlock tableBlock) {
        validateHeader(tableBlock);
        TableBody body = bodyNode(tableBlock);

        return rowNodes(body).stream()
                             .map(this::firstCellText)
                             .filter(value -> !value.isBlank())
                             .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateHeader(TableBlock tableBlock) {
        TableHead head = headNode(tableBlock);
        TableRow firstRow = rowNodes(head).stream()
                                          .findFirst()
                                          .orElseThrow(() -> new EntityMetadataContractException(
                                              "Prompt table header row is missing"
                                          ));

        String firstHeader = firstCellText(firstRow);

        if (firstHeader.equalsIgnoreCase(config.fieldColumnHeader())) {
            return;
        }

        throw new EntityMetadataContractException(
            String.format(
                "Prompt field column header must be '%s', found '%s'",
                config.fieldColumnHeader(),
                firstHeader
            )
        );
    }

    private TableHead headNode(TableBlock tableBlock) {
        return directChildren(tableBlock).stream()
                                         .filter(TableHead.class::isInstance)
                                         .map(TableHead.class::cast)
                                         .findFirst()
                                         .orElseThrow(() -> new EntityMetadataContractException(
                                             "Prompt table head is missing"
                                         ));
    }

    private TableBody bodyNode(TableBlock tableBlock) {
        return directChildren(tableBlock).stream()
                                         .filter(TableBody.class::isInstance)
                                         .map(TableBody.class::cast)
                                         .findFirst()
                                         .orElseThrow(() -> new EntityMetadataContractException(
                                             "Prompt table body is missing"
                                         ));
    }

    private List<TableRow> rowNodes(Node node) {
        return directChildren(node).stream()
                                   .filter(TableRow.class::isInstance)
                                   .map(TableRow.class::cast)
                                   .toList();
    }

    private String firstCellText(TableRow row) {
        return directChildren(row).stream()
                                  .filter(TableCell.class::isInstance)
                                  .map(TableCell.class::cast)
                                  .findFirst()
                                  .map(this::text)
                                  .orElse("");
    }

    private String text(Node node) {
        return new TextCollectingVisitor().collectAndGetText(node).strip();
    }

    private List<Node> directChildren(Node node) {
        List<Node> children = new ArrayList<>();
        Node current = node.getFirstChild();

        while (current != null) {
            children.add(current);
            current = current.getNext();
        }

        return children;
    }
}
