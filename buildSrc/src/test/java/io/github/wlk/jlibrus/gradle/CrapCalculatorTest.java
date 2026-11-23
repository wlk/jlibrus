package io.github.wlk.jlibrus.gradle;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CrapCalculatorTest {

    private static final String REPORT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
            <report name="demo">
              <package name="dev/demo">
                <class name="dev/demo/Calc" sourcefilename="Calc.java">
                  <method name="simple" desc="()I" line="3">
                    <counter type="INSTRUCTION" missed="0" covered="10"/>
                    <counter type="COMPLEXITY" missed="0" covered="1"/>
                  </method>
                  <method name="branchy" desc="(I)I" line="9">
                    <counter type="INSTRUCTION" missed="10" covered="0"/>
                    <counter type="COMPLEXITY" missed="4" covered="0"/>
                  </method>
                  <method name="half" desc="(I)I" line="20">
                    <counter type="INSTRUCTION" missed="5" covered="5"/>
                    <counter type="COMPLEXITY" missed="1" covered="1"/>
                  </method>
                </class>
              </package>
            </report>
            """;

    private List<MethodCrap> parse() {
        return CrapCalculator.fromJacocoXml(
                new ByteArrayInputStream(REPORT.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void fullyCoveredMethodCrapEqualsComplexity() {
        MethodCrap simple = parse().stream().filter(m -> m.method().equals("simple")).findFirst().orElseThrow();
        assertThat(simple.crap()).isEqualTo(1.0);
        assertThat(simple.className()).isEqualTo("dev.demo.Calc");
    }

    @Test
    void uncoveredMethodCrapIsComplexitySquaredPlusComplexity() {
        MethodCrap branchy = parse().stream().filter(m -> m.method().equals("branchy")).findFirst().orElseThrow();
        assertThat(branchy.complexity()).isEqualTo(4);
        assertThat(branchy.coverage()).isEqualTo(0.0);
        assertThat(branchy.crap()).isEqualTo(20.0);
    }

    @Test
    void partiallyCoveredMethodUsesCubedUncoveredRatio() {
        MethodCrap half = parse().stream().filter(m -> m.method().equals("half")).findFirst().orElseThrow();
        assertThat(half.crap()).isCloseTo(2.5, within(1e-9));
    }

    @Test
    void offendersAreThoseAtOrAboveThresholdSortedWorstFirst() {
        List<MethodCrap> offenders = CrapCalculator.offenders(parse(), 10.0);
        assertThat(offenders).extracting(MethodCrap::method).containsExactly("branchy");
    }

    @Test
    void formatsReadableLine() {
        MethodCrap branchy = parse().stream().filter(m -> m.method().equals("branchy")).findFirst().orElseThrow();
        assertThat(branchy.describe()).isEqualTo("dev.demo.Calc#branchy (line 9) CRAP=20.0 complexity=4 coverage=0%");
    }
}
