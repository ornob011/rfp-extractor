package com.dsi.rfp.config;

import com.dsi.rfp.domain.exception.SystemIoException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class YamlConfigLoader {

    public <T> T load(
        String path,
        Class<T> type,
        String errorContext
    ) {
        List<PropertySource<?>> propertySources = loadPropertySources(
            path,
            errorContext
        );

        Iterable<org.springframework.boot.context.properties.source.ConfigurationPropertySource> configurationSources =
            org.springframework.boot.context.properties.source.ConfigurationPropertySources
                .from(propertySources);

        return new Binder(configurationSources).bind(
            "",
            Bindable.of(type)
        ).orElseThrow(() -> new SystemIoException(
            String.format(
                "Failed to bind %s: %s",
                errorContext,
                path
            ),
            new IllegalStateException(
                String.format("%s binder returned empty result", errorContext)
            )
        ));
    }

    private List<PropertySource<?>> loadPropertySources(
        String path,
        String errorContext
    ) {
        Resource resource = new ClassPathResource(path);

        try {
            return new YamlPropertySourceLoader().load(
                path,
                resource
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to load %s: %s",
                    errorContext,
                    path
                ),
                exception
            );
        }
    }
}
