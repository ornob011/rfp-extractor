package com.dsi.rfp.domain.port.out;

import com.dsi.rfp.domain.model.RulePackDefinition;
import com.dsi.rfp.domain.model.RulePackResults;

import java.util.List;
import java.util.Optional;

public interface RulePackPort {

    Optional<RulePackDefinition> loadPack(String packId);

    List<RulePackDefinition> listPacks();

    RulePackResults runPack(
        RulePackDefinition pack,
        String rfpJson
    );
}
