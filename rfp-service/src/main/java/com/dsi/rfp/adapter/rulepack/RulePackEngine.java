package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RulePackDefinition;
import com.dsi.rfp.domain.model.RulePackResults;
import com.dsi.rfp.domain.port.out.RulePackPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RulePackEngine implements RulePackPort {

    private final RulePackLoader rulePackLoader;
    private final RulePackRunner rulePackRunner;

    @Override
    public Optional<RulePackDefinition> loadPack(String packId) {
        return rulePackLoader.load(packId);
    }

    @Override
    public List<RulePackDefinition> listPacks() {
        return rulePackLoader.listPacks();
    }

    @Override
    public RulePackResults runPack(
        RulePackDefinition pack,
        String rfpJson
    ) {
        return rulePackRunner.run(
            pack,
            rfpJson
        );
    }
}
