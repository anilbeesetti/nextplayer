package dev.anilbeesetti.nextplayer.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision.ALLOW_AUTOCORRECT
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.CODE_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.CodeStyleValue
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider
import com.pinterest.ktlint.ruleset.standard.rules.ArgumentListWrappingRule
import com.pinterest.ktlint.ruleset.standard.rules.TrailingCommaOnCallSiteRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLayoutRuleTest {
    private val engine = KtLintRuleEngine(
        ruleProviders = StandardRuleSetProvider().getRuleProviders() +
            NextPlayerRuleSetProvider().getRuleProviders(),
        editorConfigOverride = EditorConfigOverride.from(
            CODE_STYLE_PROPERTY to CodeStyleValue.android_studio,
            TrailingCommaOnCallSiteRule.TRAILING_COMMA_ON_CALL_SITE_PROPERTY to true,
            ArgumentListWrappingRule.IGNORE_WHEN_PARAMETER_COUNT_GREATER_OR_EQUAL_THAN_PROPERTY to
                Int.MAX_VALUE,
        ),
    )

    @Test
    fun wrapsTwoArgumentsEvenWhenTheyFitOnOneLine() {
        assertFormatted(
            input = "val value = pair(first = 1, second = 2)",
            expected = """
                val value = pair(
                    first = 1,
                    second = 2,
                )
            """.trimIndent(),
        )
    }

    @Test
    fun keepsSingleArgumentCallsOnOneLine() {
        assertFormatted(
            input = "val value = single(value = 1)",
            expected = "val value = single(value = 1)",
        )
    }

    @Test
    fun joinsTheMultilineHistoryStateInitializerToItsAssignment() {
        assertFormatted(
            input = """
                val state =
                    mutableStateOf(
                        value = HistoryUiState(history = DataState.Success(value = listOf(Video.sample))),
                    )
            """.trimIndent(),
            expected = """
                val state = mutableStateOf(
                    value = HistoryUiState(history = DataState.Success(value = listOf(Video.sample))),
                )
            """.trimIndent(),
        )
    }

    @Test
    fun wrapsNestedCallbacksAndCompactsEmptyLambdas() {
        assertFormatted(
            input = """
                val model = viewModel<MoreViewModel>(
                    store = store,
                    MoreViewModel.Output(openHistory = {
                    }, playVideo = {}, openSettings = {}, openTrash = {}, openVault = {}),
                )
            """.trimIndent(),
            expected = """
                val model = viewModel<MoreViewModel>(
                    store = store,
                    MoreViewModel.Output(
                        openHistory = {},
                        playVideo = {},
                        openSettings = {},
                        openTrash = {},
                        openVault = {},
                    ),
                )
            """.trimIndent(),
        )
    }

    @Test
    fun alsoWrapsListsWithMoreThanEightArguments() {
        val formatted = engine.format(
            Code.fromSnippet("val values = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)"),
        )
        for (number in 1..9) {
            assertTrue(formatted.contains("\n    $number,"))
        }
        assertEquals(
            formatted,
            format(input = formatted),
        )
    }

    @Test
    fun preservesCommentsInEmptyCallbacksAndAfterAssignments() {
        val formatted = format(
            input = """
                val value = // Keep this explanation.
                    output(first = { /* Keep this callback comment. */ }, second = {})
            """.trimIndent(),
        )
        assertTrue(formatted.contains("= // Keep this explanation.\n"))
        assertTrue(formatted.contains("/* Keep this callback comment. */"))
        assertEquals(
            formatted,
            format(input = formatted),
        )
    }

    @Test
    fun reportsInlineArgumentsDuringLintChecks() {
        val rules = mutableListOf<String>()
        engine.lint(Code.fromSnippet("val value = pair(1, 2)")) { error ->
            rules += error.ruleId.value
        }
        assertTrue("nextplayer:call-layout" in rules)
    }

    @Test
    fun keepsTheInitializerOnTheNextLineWhenTheAssignmentWouldBeTooLong() {
        val input = """
            val ${"state".repeat(17)} =
                someLongFactoryName(
                    first = 1,
                    second = 2,
                )
        """.trimIndent()
        assertFormatted(
            input = input,
            expected = input,
        )
    }

    private fun format(input: String): String = engine.format(
        code = Code.fromSnippet(input),
        callback = { _: LintError -> ALLOW_AUTOCORRECT },
    )

    private fun assertFormatted(input: String, expected: String) {
        val formatted = format(input = input)
        assertEquals(
            expected + "\n",
            formatted,
        )
        assertEquals(
            formatted,
            format(input = formatted),
        )
    }
}
