package org.school.MckoReport.MckoCompleks.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticCodeUtilTest {

    @Test
    void normalizesSupportedDiagnosticCodes() {
        assertThat(DiagnosticCodeUtil.normalize(" 9116-0004i ")).isEqualTo("9116-0004I");
        assertThat(DiagnosticCodeUtil.normalize("17")).isEqualTo("17");
    }

    @Test
    void rejectsLabelsAndEmptyValues() {
        assertThat(DiagnosticCodeUtil.normalize("Бланк")).isNull();
        assertThat(DiagnosticCodeUtil.normalize("")).isNull();
        assertThat(DiagnosticCodeUtil.normalize(null)).isNull();
    }
}
