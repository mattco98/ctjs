package com.chattriggers.ctjs.commands

import com.chattriggers.ctjs.engine.js.JSLoader
import com.chattriggers.ctjs.CTClientCommandSource
import com.chattriggers.ctjs.utils.asMixin
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.mozilla.javascript.Function

object DynamicCommand {
    sealed class Node(val parent: Node?) {
        var method: Function? = null
        var hasRedirect = false
        val children = mutableListOf<Node>()

        var builder: ArgumentBuilder<FabricClientCommandSource, *>? = null

        fun allArguments() = generateSequence(this) { it.parent }.filterIsInstance<Argument>().toList().asReversed()

        open class Literal(parent: Node?, val name: String) : Node(parent)

        class Root(name: String) : Literal(null, name) {
            var commandNode: LiteralCommandNode<FabricClientCommandSource>? = null

            fun register() {
                DynamicCommands.register(CommandImpl(this))
            }
        }

        class Argument(parent: Node?, val name: String, val type: ArgumentType<*>) : Node(parent)

        class Redirect(parent: Node?, val target: Root, val modifier: Function? = null) : Node(parent)

        fun initialize(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
            when (this) {
                is Literal -> {
                    builder = ClientCommandManager.literal(name)
                    parent?.builder?.then(builder)
                }
                is Argument -> {
                    builder = ClientCommandManager.argument(name, type)
                    parent?.builder?.then(builder)
                }
                is Redirect -> {
                    target.initialize(dispatcher)
                    parent!!.builder!!.redirect(target.commandNode) {
                        if (modifier != null)
                            JSLoader.invoke(modifier, arrayOf(it.source))
                        it.source
                    }
                }
            }

            if (method != null) {
                val arguments = allArguments()
                builder!!.executes { ctx ->
                    val argMap = ctx.source.asMixin<CTClientCommandSource>().values
                    arguments.forEach {
                        argMap[it.name] = ctx.getArgument(it.name, Any::class.java)
                    }

                    JSLoader.invoke(method!!, arrayOf(argMap))
                    1
                }
            }

            if (this is Root)
                commandNode = dispatcher.register(builder!! as LiteralArgumentBuilder<FabricClientCommandSource>)
        }
    }

    class CommandImpl(private val node: Node.Root) : Command {
        override val overrideExisting = false
        override val name = node.name

        override fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
            node.initialize(dispatcher)
            val builder = node.builder!! as LiteralArgumentBuilder<FabricClientCommandSource>
            node.commandNode = dispatcher.register(builder)
        }
    }
}
