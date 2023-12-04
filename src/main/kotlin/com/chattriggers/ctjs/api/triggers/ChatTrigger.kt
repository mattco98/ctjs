package com.chattriggers.ctjs.api.triggers

import com.chattriggers.ctjs.api.message.ChatLib
import com.chattriggers.ctjs.api.message.TextComponent
import org.mozilla.javascript.regexp.NativeRegExp

class ChatTrigger(method: Any, type: ITriggerType) : Trigger(method, type) {
    private lateinit var chatCriteria: Any
    private var formatted: Boolean = false
    private var formattedForced = false
    private var caseInsensitive: Boolean = false
    private lateinit var criteriaPattern: Regex
    private var parameter: Parameter? = null
    private var triggerIfCanceled: Boolean = true

    /**
     * Sets if the chat trigger should run if the chat event has already been canceled.
     * True by default.
     * @param bool Boolean to set
     * @return the trigger object for method chaining
     */
    fun triggerIfCanceled(bool: Boolean) = apply { triggerIfCanceled = bool }

    /**
     * Sets the chat criteria for [matchesChatCriteria].
     * Arguments for the trigger's method can be passed in using ${variable}.
     * Example: `setChatCriteria("<${name}> ${message}");`
     * Use ${*} to match a chat message but ignore the pass through.
     * @param chatCriteria the chat criteria to set
     * @return the trigger object for method chaining
     */
    fun setChatCriteria(chatCriteria: Any) = apply {
        this.chatCriteria = chatCriteria
        val flags = mutableSetOf<RegexOption>()
        var source = ".+"

        when (chatCriteria) {
            is String -> {
                if (!formattedForced)
                    formatted = ChatLib.FORMATTING_CODE_REGEX in chatCriteria

                val replacedCriteria = Regex.escape(chatCriteria)
                    .replace("""\\n""", "\n") // Undo the escaping of '\n'
                    .replace(Regex("""\$\{[^*]+?}"""), """\\E(.+)\\Q""")
                    .replace(Regex("""\$\{\*?}"""), """\\E(?:.+)\\Q""")

                if (caseInsensitive)
                    flags.add(RegexOption.IGNORE_CASE)

                if ("" != chatCriteria)
                    source = replacedCriteria
            }
            is NativeRegExp -> {
                if (chatCriteria["ignoreCase"] as Boolean || caseInsensitive)
                    flags.add(RegexOption.IGNORE_CASE)

                if (chatCriteria["multiline"] as Boolean)
                    flags.add(RegexOption.MULTILINE)

                source = (chatCriteria["source"] as String).let {
                    if ("" == it) ".+" else it
                }

                if (!formattedForced)
                    formatted = ChatLib.FORMATTING_CODE_REGEX in source
            }
            else -> throw IllegalArgumentException("Expected String or Regexp Object")
        }

        criteriaPattern = Regex(source, flags)
    }

    /**
     * Alias for [setChatCriteria].
     * @param chatCriteria the chat criteria to set
     * @return the trigger object for method chaining
     */
    fun setCriteria(chatCriteria: Any) = setChatCriteria(chatCriteria)

    /**
     * Sets the chat parameter for [Parameter].
     * Clears current parameter list.
     * @param parameter the chat parameter to set
     * @return the trigger object for method chaining
     */
    fun setParameter(parameter: String?) = apply {
        this.parameter = parameter?.let(Parameter::getParameterByName)
    }

    /**
     * Adds the "start" parameter
     * @return the trigger object for method chaining
     */
    fun setStart() = setParameter("start")

    /**
     * Adds the "contains" parameter
     * @return the trigger object for method chaining
     */
    fun setContains() = setParameter("contains")

    /**
     * Adds the "end" parameter
     * @return the trigger object for method chaining
     */
    fun setEnd() = setParameter("end")

    /**
     * Makes the trigger match the entire chat message.
     *
     * All this does is clear the parameter; it is equivalent to `setParameter(null)`
     *
     * @return the trigger object for method chaining
     */
    fun setExact() = setParameter(null)

    /**
     * Forces this trigger to be formatted or unformatted. If no argument is
     * provided, it will be set to formatted. This method overrides the
     * behavior of inferring the formatted status from the criteria.
     */
    @JvmOverloads
    fun setFormatted(formatted: Boolean = true) = apply {
        this.formatted = formatted
        this.formattedForced = true
    }

    /**
     * Makes the chat criteria case insensitive
     * @return the trigger object for method chaining
     */
    fun setCaseInsensitive() = apply {
        caseInsensitive = true

        // Reparse criteria if setCriteria has already been called
        if (::chatCriteria.isInitialized)
            setCriteria(chatCriteria)
    }

    /**
     * Argument 1 (String) The chat message received
     * Argument 2 (ClientChatReceivedEvent) the chat event fired
     * @param args list of arguments as described
     */
    override fun trigger(args: Array<out Any?>) {
        val chatEvent = args.firstOrNull() as? Event ?:
            throw IllegalArgumentException("The first argument must be a ChatTrigger.Event")

        if (!triggerIfCanceled && chatEvent.isCancelled()) return

        val chatMessage = getChatMessage(chatEvent.message)
        val variables = (getVariables(chatMessage) ?: return) + chatEvent

        callMethod(variables.toTypedArray())
    }

    // helper method to get the proper chat message based on the presence of color codes
    private fun getChatMessage(chatMessage: TextComponent) =
        if (formatted)
            chatMessage.formattedText.replace("\u00a7", "&")
        else chatMessage.unformattedText

    // helper method to get the variables to pass through
    private fun getVariables(chatMessage: String) =
        if (::criteriaPattern.isInitialized) matchesChatCriteria(chatMessage) else emptyList()

    /**
     * A method to check whether a received chat message
     * matches this trigger's definition criteria.
     * Ex. "FalseHonesty joined Cops vs Crims" would match `${playername} joined ${gamejoined}`
     * @param chat the chat message to compare against
     * @return a list of the variables, in order or null if it doesn't match
     */
    private fun matchesChatCriteria(chat: String): List<Any>? {
        val matches = criteriaPattern.find(chat)?.groups ?: return null

        if (parameter != null) {
            // This will never be null, as the 0-group is the entire capture, and there must
            // be a capture here if we haven't already returned
            val first = matches[0]!!

            when (parameter) {
                Parameter.START -> if (first.range.first != 0) return null
                Parameter.END -> if (first.range.last != chat.length) return null
                else -> {}
            }
        }

        return matches.drop(0).map { it?.value.orEmpty() }
    }

    /**
     * The parameter to match chat criteria to.
     * Location parameters
     * - contains
     * - start
     * - end
     */
    private enum class Parameter(vararg names: String) {
        CONTAINS("<c>", "<contains>", "c", "contains"),
        START("<s>", "<start>", "s", "start"),
        END("<e>", "<end>", "e", "end");

        var names: List<String> = names.asList()

        companion object {
            fun getParameterByName(name: String) = values().first { it.names.contains(name.lowercase()) }
        }
    }

    class Event(@JvmField val message: TextComponent) : CancellableEvent()
}
