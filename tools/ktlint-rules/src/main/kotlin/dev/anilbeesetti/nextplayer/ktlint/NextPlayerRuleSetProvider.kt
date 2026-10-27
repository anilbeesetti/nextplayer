package dev.anilbeesetti.nextplayer.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

class NextPlayerRuleSetProvider : RuleSetProviderV3(id = RuleSetId(value = "nextplayer")) {
    override fun getRuleProviders(): Set<RuleProvider> = setOf(RuleProvider { CallLayoutRule() })
}
