package com.dsi.rfp.domain.port.out;

import com.dsi.rfp.domain.model.RulePackDefinition;

import java.util.List;

public interface RulePackManagementPort {

    List<RulePackDefinition> listPacks();

    List<String> reloadAll();
}
