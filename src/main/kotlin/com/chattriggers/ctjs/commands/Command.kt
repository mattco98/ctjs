package com.chattriggers.ctjs.commands

import com.chattriggers.ctjs.console.LogType
import com.chattriggers.ctjs.console.printToConsole
import com.chattriggers.ctjs.engine.js.JSLoader
import com.chattriggers.ctjs.minecraft.wrappers.Client
import com.chattriggers.ctjs.mixins.CommandNodeAccessor
import com.chattriggers.ctjs.utils.Initializer
import com.chattriggers.ctjs.utils.InternalApi
import com.chattriggers.ctjs.utils.asMixin
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.command.CommandSource

@InternalApi
interface Command {
    val overrideExisting: Boolean
    val name: String

    fun register(dispatcher: CommandDispatcher<FabricClientCommandSource>)
}

@InternalApi
abstract class CommandCollection : Initializer {
    private val activeCommands = mutableSetOf<Command>()
    private val pendingCommands = mutableSetOf<Command>()

    private var clientDispatcher: CommandDispatcher<FabricClientCommandSource>? = null

    @Suppress("UNCHECKED_CAST")
    private val dispatchers: List<CommandDispatcher<FabricClientCommandSource>>
        get() = listOfNotNull(
            clientDispatcher,
            Client.getMinecraft().networkHandler?.commandDispatcher as CommandDispatcher<FabricClientCommandSource>,
        )

    override fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            this.clientDispatcher = dispatcher
            activeCommands.forEach(::register)
            pendingCommands.forEach(::register)
            pendingCommands.clear()
        }
    }

    fun register(command: Command) {
        if (clientDispatcher == null) {
            pendingCommands.add(command)
            return
        }

        if (command.hasConflict(command.name)) {
            existingCommandWarning(command.name).printToConsole(JSLoader.console, LogType.WARN)
        } else {
            activeCommands.add(command)
            dispatchers.forEach {
                command.register(it)
            }
        }
    }

    fun unregister(command: Command) {
        unregisterImpl(setOf(command))
    }

    fun unregisterAll() {
        unregisterImpl(pendingCommands + activeCommands)
    }

    private fun unregisterImpl(commands: Set<Command>) {
        val names = commands.mapTo(mutableSetOf(), Command::name)
        pendingCommands.removeAll(commands)
        activeCommands.removeAll(commands)

        for (dispatcher in listOfNotNull(clientDispatcher, Client.getMinecraft().networkHandler?.commandDispatcher)) {
            dispatcher.root.apply {
                children.removeIf { it.name in names }
                for (name in names)
                    asMixin<CommandNodeAccessor>().literals.remove(name)
            }
        }
    }

    fun <S, T : ArgumentBuilder<S, T>> ArgumentBuilder<S, T>.onExecute(block: (CommandContext<S>) -> Unit): T = this.executes {
        block(it)
        1
    }

    private fun Command.hasConflict(name: String) = !overrideExisting && clientDispatcher!!.root.getChild(name) != null

    private fun existingCommandWarning(name: String) =
        """
                Command with name $name already exists! This will not override the 
                other command with the same name. To override the other command, set the 
                overrideExisting flag in setName() (the second argument) to true.
            """.trimIndent().replace("\n", "")
}
