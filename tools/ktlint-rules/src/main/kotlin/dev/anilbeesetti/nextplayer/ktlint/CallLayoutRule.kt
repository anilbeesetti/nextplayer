package dev.anilbeesetti.nextplayer.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.IndentConfig
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.VisitorModifier.RunAfterRule
import com.pinterest.ktlint.rule.engine.core.api.Rule.VisitorModifier.RunAfterRule.Mode.REGARDLESS_WHETHER_RUN_AFTER_RULE_IS_LOADED_OR_DISABLED
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_SIZE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpaceWithNewline20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import com.pinterest.ktlint.rule.engine.core.api.upsertWhitespaceBeforeMe
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList

/** Keeps multi-argument calls vertical and their initializers beside the assignment operator. */
class CallLayoutRule :
    Rule(
        ruleId = RuleId(value = "nextplayer:call-layout"),
        about = About(maintainer = "Next Player"),
        visitorModifiers = setOf(
            RunAfterRule(
                ruleId = RuleId(value = "standard:argument-list-wrapping"),
                mode = REGARDLESS_WHETHER_RUN_AFTER_RULE_IS_LOADED_OR_DISABLED,
            ),
            RunAfterRule(
                ruleId = RuleId(value = "standard:binary-expression-wrapping"),
                mode = REGARDLESS_WHETHER_RUN_AFTER_RULE_IS_LOADED_OR_DISABLED,
            ),
            RunAfterRule(
                ruleId = RuleId(value = "standard:function-literal"),
                mode = REGARDLESS_WHETHER_RUN_AFTER_RULE_IS_LOADED_OR_DISABLED,
            ),
        ),
        usesEditorConfigProperties = setOf(
            INDENT_SIZE_PROPERTY,
            INDENT_STYLE_PROPERTY,
            MAX_LINE_LENGTH_PROPERTY,
        ),
    ),
    RuleAutocorrectApproveHandler {
    private var indent = IndentConfig.DEFAULT_INDENT_CONFIG
    private var maxLineLength = Int.MAX_VALUE

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        indent = IndentConfig(
            indentStyle = editorConfig[INDENT_STYLE_PROPERTY],
            tabWidth = editorConfig[INDENT_SIZE_PROPERTY],
        )
        maxLineLength = editorConfig[MAX_LINE_LENGTH_PROPERTY].takeIf { it > 0 } ?: Int.MAX_VALUE
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (Int, String, Boolean) -> AutocorrectDecision,
    ) {
        when (val element = node.psi) {
            is KtValueArgumentList -> {
                if (element.arguments.size > 1) {
                    for (argument in element.arguments) {
                        wrap(
                            node = argument.node,
                            whitespace = indent.childIndentOf(node),
                            emit = emit,
                        )
                    }
                    element.rightParenthesis?.node?.let {
                        wrap(
                            node = it,
                            whitespace = indent.parentIndentOf(node),
                            emit = emit,
                        )
                    }
                }
            }

            is KtProperty -> joinInitializer(
                expression = element.initializer,
                emit = emit,
            )

            is KtBinaryExpression -> {
                if (element.operationToken == KtTokens.EQ) {
                    joinInitializer(
                        expression = element.right,
                        emit = emit,
                    )
                }
            }

            is KtLambdaExpression -> {
                if (element.valueParameters.isEmpty() &&
                    element.bodyExpression?.text?.isBlank() == true && node.text != "{}"
                ) {
                    emit(
                        node.startOffset,
                        "Keep an empty lambda on one line",
                        true,
                    )
                        .ifAutocorrectAllowed {
                            node.replaceAllChildrenToChildrenOf(
                                KtPsiFactory(project = element.project).createExpression("{}").node,
                            )
                        }
                }
            }
        }
    }

    override fun afterVisitChildNodes(
        node: ASTNode,
        emit: (Int, String, Boolean) -> AutocorrectDecision,
    ) = Unit

    private fun wrap(
        node: ASTNode,
        whitespace: String,
        emit: (Int, String, Boolean) -> AutocorrectDecision,
    ) {
        if (!node.prevLeaf.isWhiteSpaceWithNewline20) {
            emit(
                node.startOffset,
                "Put each argument of a multi-argument call on its own line",
                true,
            )
                .ifAutocorrectAllowed { node.upsertWhitespaceBeforeMe(whitespace) }
        }
    }

    private fun joinInitializer(
        expression: KtExpression?,
        emit: (Int, String, Boolean) -> AutocorrectDecision,
    ) {
        if (expression !is KtCallExpression && expression !is KtQualifiedExpression) return
        val whitespace = expression.node.prevLeaf ?: return
        if (!whitespace.isWhiteSpaceWithNewline20 || !expression.text.contains('\n')) return
        val equals = whitespace.prevLeaf ?: return
        if (equals.elementType != KtTokens.EQ) return
        val source = expression.containingFile.text
        val lineStart = source.lastIndexOf(
            '\n',
            equals.startOffset,
        ) + 1
        val firstLineLength =
            equals.startOffset + 2 - lineStart + expression.text.substringBefore('\n').length
        if (firstLineLength <= maxLineLength) {
            emit(
                expression.textOffset,
                "Keep a multiline call beside the assignment operator",
                true,
            )
                .ifAutocorrectAllowed { expression.node.upsertWhitespaceBeforeMe(" ") }
        }
    }
}
