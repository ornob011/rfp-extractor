package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RulePackDefinition;
import com.dsi.rfp.domain.model.RulePackResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulePackEngineTest {

    @Mock
    private RulePackLoader rulePackLoader;

    @Mock
    private RulePackRunner rulePackRunner;

    @InjectMocks
    private RulePackEngine rulePackEngine;

    @Test
    void shouldDelegateLoadPackToLoader() {
        RulePackDefinition pack = RulePackDefinition.builder().packId("bd-govt-ict-v1").build();
        when(rulePackLoader.load("bd-govt-ict-v1")).thenReturn(Optional.of(pack));

        Optional<RulePackDefinition> result = rulePackEngine.loadPack("bd-govt-ict-v1");

        assertThat(result).contains(pack);
    }

    @Test
    void shouldDelegateListPacksToLoader() {
        RulePackDefinition pack = RulePackDefinition.builder().packId("bd-govt-ict-v1").build();
        when(rulePackLoader.listPacks()).thenReturn(List.of(pack));

        List<RulePackDefinition> result = rulePackEngine.listPacks();

        assertThat(result).containsExactly(pack);
    }

    @Test
    void shouldDelegateRunPackToRunner() {
        RulePackDefinition pack = RulePackDefinition.builder().packId("bd-govt-ict-v1").build();
        RulePackResults results = RulePackResults.builder().packId("bd-govt-ict-v1").build();
        when(rulePackRunner.run(pack, "{}")).thenReturn(results);

        RulePackResults result = rulePackEngine.runPack(
            pack,
            "{}"
        );

        assertThat(result).isEqualTo(results);
        verify(rulePackRunner).run(
            pack,
            "{}"
        );
    }
}
