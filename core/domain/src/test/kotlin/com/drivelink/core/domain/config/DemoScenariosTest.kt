package com.drivelink.core.domain.config

import com.drivelink.core.domain.ContractFiles
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DemoScenariosTest {
    /** Scenario names are the two-space-indented keys under "scenarios:" in api/scenarios.yaml. */
    @Test fun list_matchesCatalog_inOrder() {
        val names = ContractFiles.repoRoot.resolve("api/scenarios.yaml").readLines()
            .dropWhile { it.trim() != "scenarios:" }
            .mapNotNull { Regex("^  ([a-z0-9-]+):\\s*$").find(it)?.groupValues?.get(1) }
        assertThat(DemoScenarios.ALL).containsExactlyElementsIn(names).inOrder()
        assertThat(DemoScenarios.ALL).hasSize(14)
    }
}
