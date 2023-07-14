package com.chattriggers.ctjs.commands

import com.chattriggers.ctjs.console.LogType
import com.chattriggers.ctjs.console.printToConsole
import com.chattriggers.ctjs.engine.js.JSLoader
import com.chattriggers.ctjs.minecraft.CTEvents
import com.chattriggers.ctjs.mixins.CommandNodeAccessor
import com.chattriggers.ctjs.utils.Initializer
import com.chattriggers.ctjs.utils.InternalApi
import com.chattriggers.ctjs.utils.asMixin
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents

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
    private var networkDispatcher: CommandDispatcher<FabricClientCommandSource>? = null
    private var commandsRegistered = false

    override fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            networkDispatcher = dispatcher
            tryRegisterPendingCommands()
        }

        CTEvents.COMMAND_DISPATCHER_REGISTER.register { dispatcher ->
            networkDispatcher = dispatcher
            tryRegisterPendingCommands()
        }

        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            unregisterImpl(pendingCommands + activeCommands)
            clientDispatcher = null
            networkDispatcher = null
            commandsRegistered = false
        }
    }

    private fun tryRegisterPendingCommands() {
        require(!commandsRegistered)

        if (clientDispatcher != null && networkDispatcher != null) {
            commandsRegistered = true
            pendingCommands.forEach(::register)
            pendingCommands.clear()
        }
    }

    fun register(command: Command) {
        if (!commandsRegistered) {
            pendingCommands.add(command)
            return
        }

        if (command.hasConflict(command.name)) {
            existingCommandWarning(command.name).printToConsole(JSLoader.console, LogType.WARN)
        } else {
            activeCommands.add(command)
            command.register(clientDispatcher!!)
            command.register(networkDispatcher!!)
        }
    }

    fun unregister(command: Command) {
        unregisterImpl(setOf(command))
    }

    private fun unregisterImpl(commands: Set<Command>) {
        val names = commands.mapTo(mutableSetOf(), Command::name)
        pendingCommands.removeAll(commands)
        activeCommands.removeAll(commands)

        for (dispatcher in listOf(clientDispatcher, networkDispatcher)) {
            dispatcher?.root?.apply {
                children.removeIf { it.name in names }
                for (name in names)
                    asMixin<CommandNodeAccessor>().literals.remove(name)
            }
        }
    }

    fun <S, T : ArgumentBuilder<S, T>> ArgumentBuilder<S, T>.onExecute(block: (CommandContext<S>) -> Unit): T =
        this.executes {
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
