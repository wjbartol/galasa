/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.galasaecosystem.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class KubernetesEcosystemProfileTest {

    @Test
    public void testMinimalProfileResourceFiltering() {
        System.setProperty("galasaecosystem.profile", "MINIMAL");
        System.setProperty("galasaecosystem.simbank.enabled", "false");

        KubernetesEcosystemImpl ecosystem = new KubernetesEcosystemImpl(null, "test-tag", null);

        assertThat(ecosystem.getExecutionProfile()).isEqualTo(KubernetesEcosystemImpl.ExecutionProfile.MINIMAL);
        assertThat(ecosystem.isSimbankEnabled()).isFalse();

        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.CPS_STATEFULSET)).isTrue();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.API_DEPLOYMENT)).isTrue();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.ENGINE_DEPLOYMENT)).isTrue();

        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.SIMBANK_DEPLOYMENT)).isFalse();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.GRAFANA_DEPLOYMENT)).isFalse();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.PROMETHEUS_DEPLOYMENT)).isFalse();
    }

    @Test
    public void testMinimalSimbankProfileResourceFiltering() {
        System.setProperty("galasaecosystem.profile", "MINIMAL_SIMBANK");

        KubernetesEcosystemImpl ecosystem = new KubernetesEcosystemImpl(null, "test-tag", null);

        assertThat(ecosystem.getExecutionProfile()).isEqualTo(KubernetesEcosystemImpl.ExecutionProfile.MINIMAL_SIMBANK);
        assertThat(ecosystem.isSimbankEnabled()).isTrue();

        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.CPS_STATEFULSET)).isTrue();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.API_DEPLOYMENT)).isTrue();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.ENGINE_DEPLOYMENT)).isTrue();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.SIMBANK_DEPLOYMENT)).isTrue();

        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.GRAFANA_DEPLOYMENT)).isFalse();
        assertThat(ecosystem.isResourceEnabled(AbstractEcosystemImpl.ResourceType.PROMETHEUS_DEPLOYMENT)).isFalse();
    }
}
