package com.chattriggers.ctjs.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import org.mozilla.javascript.Function

object DynamicCommand {
    abstract class Node(val parent: Node?) {
        val method: Function? = null
        val redirect: RedirectNode? = null
        val children = mutableListOf<Node>()

        fun register() = register(this)

        private fun build() = build(this)
    }

    class LiteralNode(parent: Node?, val name: String) : Node(parent)

    class ArgumentNode(parent: Node?, val name: String, val type: ArgumentType<*>) : Node(parent)

    class RedirectNode(parent: Node?, val target: Node, val modifier: Function? = null) : Node(parent)

    private fun build(node: Node): Command.Registration {

    }

    private fun register(node: Node): LiteralCommandNode<FabricClientCommandSource> {
        DynamicCommands.register(build(node))
    }
}
