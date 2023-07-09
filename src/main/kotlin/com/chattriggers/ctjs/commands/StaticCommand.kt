package com.chattriggers.ctjs.commands

import com.chattriggers.ctjs.triggers.CommandTrigger
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

internal class StaticCommand(
    val trigger: CommandTrigger,
    override val name: String,
    private val aliases: Set<String>,
    override val overrideExisting: Boolean,
    private val staticSuggestions: List<String>,
    private val dynamicSuggestions: ((List<String>) -> List<String>)?,
) : Command {
    override fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        val builder = literal(name)
            .then(argument("args", StringArgumentType.greedyString())
                .suggests { ctx, builder ->
                    val suggestions = if (dynamicSuggestions != null) {
                        val args = try {
                            StringArgumentType.getString(ctx, "args").split(" ")
                        } catch (e: IllegalArgumentException) {
                            emptyList()
                        }

                        // Kotlin compiler bug: Without this null assert, it complains that the receiver is
                        // nullable, but with it, it says it's unnecessary.
                        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                        dynamicSuggestions!!(args)
                    } else staticSuggestions

                    for (suggestion in suggestions)
                        builder.suggest(suggestion)

                    builder.buildFuture()
                }
                .onExecute {
                    trigger.trigger(StringArgumentType.getString(it, "args").split(" ").toTypedArray())
                })
            .onExecute { trigger.trigger(emptyArray()) }

        val node = dispatcher.register(builder)
        for (alias in aliases)
            dispatcher.register(literal(alias).redirect(node))
    }

    companion object : CommandCollection()
}
