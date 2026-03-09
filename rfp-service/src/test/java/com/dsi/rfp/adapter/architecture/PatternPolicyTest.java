package com.dsi.rfp.adapter.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "com.dsi.rfp",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class PatternPolicyTest {

    @ArchTest
    static final ArchRule noRegexPatternClassUsage = noClasses()
        .should().dependOnClassesThat().haveFullyQualifiedName("java.util.regex.Pattern");

    @ArchTest
    static final ArchRule noRegexMatcherClassUsage = noClasses()
        .should().dependOnClassesThat().haveFullyQualifiedName("java.util.regex.Matcher");

    @ArchTest
    static final ArchRule noGuavaCharMatcherUsage = noClasses()
        .should().dependOnClassesThat().haveFullyQualifiedName("com.google.common.base.CharMatcher");
}
