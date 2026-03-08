package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.Section;
import com.dsi.rfp.domain.model.TableExtractionResult;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeMap;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

@Component
public class TableSectionLinker {

    public void link(
        List<TableExtractionResult> tables,
        List<Section> sections,
        List<Clause> clauses
    ) {
        var sectionIndex = buildSectionIndex(sections);
        var clauseIndex = buildClauseIndex(clauses);

        tables.forEach(
            table -> {
                Optional.ofNullable(sectionIndex.get(table.getPageStart()))
                        .map(Section::getId)
                        .ifPresent(table::setSectionId);

                Optional.ofNullable(clauseIndex.floorEntry(table.getPageStart()))
                        .map(java.util.Map.Entry::getValue)
                        .map(Clause::getClauseId)
                        .ifPresent(table::setClauseId);
            }
        );
    }

    private TreeRangeMap<@NonNull Integer, @NonNull Section> buildSectionIndex(
        List<Section> sections
    ) {
        TreeRangeMap<@NonNull Integer, @NonNull Section> index = TreeRangeMap.create();

        flattenSections(sections)
            .sorted(
                Comparator.comparingInt(Section::getLevel)
            )
            .forEach(section -> index.put(
                Range.closed(
                    section.getPageStart(),
                    section.getPageEnd()
                ),
                section
            ));

        return index;
    }

    private Stream<Section> flattenSections(List<Section> sections) {
        return sections.stream()
                       .flatMap(section -> Stream.concat(
                           Stream.of(section),
                           flattenSections(section.getChildren())
                       ));
    }

    private NavigableMap<Integer, Clause> buildClauseIndex(
        List<Clause> clauses
    ) {
        NavigableMap<Integer, Clause> index = new TreeMap<>();

        clauses.forEach(clause -> index.put(clause.getPageNumber(), clause));

        return index;
    }
}
