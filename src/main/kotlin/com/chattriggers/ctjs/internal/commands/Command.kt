package com.chattriggers.ctjs.internal.commands

import com.chattriggers.ctjs.mixins.commands.CommandNodeAccessor
import com.chattriggers.ctjs.typing.annotations.InternalApi
import com.chattriggers.ctjs.internal.utils.asMixin
import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

@InternalApi
interface Command {
    val overrideExisting: Boolean
    val name: String

    fun registerImpl(dispatcher: CommandDispatcher<FabricClientCommandSource>)

    fun unregisterImpl(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.root.asMixin<CommandNodeAccessor>().apply {
            childNodes.remove(name)
            literals.remove(name)
        }
    }
}
