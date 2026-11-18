package br.com.includelabs.gateway.stone.bootstrap;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
@AnalyzeClasses(packages="br.com.includelabs.gateway.stone")
class HexagonalArchitectureTest {
@ArchTest static final ArchRule domainIsIndependent = noClasses().that().resideInAPackage("..domain..")
.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..adapters..", "..application..");
}
