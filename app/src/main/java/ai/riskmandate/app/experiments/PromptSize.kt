package ai.riskmandate.app.experiments

/**
 * How big a case's prompt is: characters of the text sent in the first request in a mode (the system prompt, the
 * messages, the tool calls and results in the history) plus, when the runtime declares the tools, the tools'
 * names, descriptions and parameters. Tokens are not computed here: the runtime reports them for a run and the
 * runner records them (`promptTokens`), because a character count says little about tokens.
 */
object PromptSize {
    fun chars(case: ToolCase, mode: ToolMode): Int {
        val messages = ToolTestRunner.initialMessages(case, mode).sumOf { m -> m.text.length + (m.toolName?.length ?: 0) + m.toolCalls.sumOf { it.name.length + it.argsJson.length } }
        val tools = if (mode == ToolMode.NATIVE) case.tools.sumOf { t -> t.name.length + t.description.length + t.params.sumOf { it.name.length + it.type.length } } else 0
        return messages + tools
    }
}
