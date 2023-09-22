package com.chattriggers.ctjs

import com.chattriggers.ctjs.api.client.Client
import com.chattriggers.ctjs.api.commands.DynamicCommands
import com.chattriggers.ctjs.internal.commands.StaticCommand
import com.chattriggers.ctjs.internal.console.ConsoleManager
import com.chattriggers.ctjs.engine.Register
import com.chattriggers.ctjs.internal.engine.module.ModuleManager
import com.chattriggers.ctjs.api.message.ChatLib
import com.chattriggers.ctjs.api.render.Image
import com.chattriggers.ctjs.api.client.KeyBind
import com.chattriggers.ctjs.api.client.Sound
import com.chattriggers.ctjs.api.triggers.TriggerType
import com.chattriggers.ctjs.api.world.World
import kotlin.concurrent.thread

object Reference {
    const val MOD_VERSION = "3.0.0-beta"
    const val MODULES_FOLDER = "./config/ChatTriggers/modules"

    var isLoaded = true
        private set

    fun unloadCT(asCommand: Boolean = true) {
        TriggerType.WORLD_UNLOAD.triggerAll()
        TriggerType.GAME_UNLOAD.triggerAll()

        isLoaded = false

        ModuleManager.teardown()
        KeyBind.clearKeyBinds()
        ConsoleManager.clearConsoles()
        Register.clearCustomTriggers()
        StaticCommand.unregisterAll()
        DynamicCommands.unregisterAll()

        Client.scheduleTask {
            CTJS.images.forEach(Image::destroy)
            CTJS.sounds.forEach(Sound::destroy)

            CTJS.images.clear()
            CTJS.sounds.clear()
        }

        if (asCommand)
            ChatLib.chat("&7Unloaded ChatTriggers")
    }

    fun loadCT(asCommand: Boolean = true) {
        Client.getMinecraft().options.write()
        unloadCT(asCommand = false)

        if (asCommand)
            ChatLib.chat("&cReloading ChatTriggers...")

        thread {
            ModuleManager.setup()
            Client.getMinecraft().options.load()

            // Need to set isLoaded to true before running modules, otherwise custom triggers
            // activated at the top level will not work
            isLoaded = true

            ModuleManager.entryPass()

            if (asCommand)
                ChatLib.chat("&aDone reloading!")

            TriggerType.GAME_LOAD.triggerAll()
            if (World.isLoaded())
                TriggerType.WORLD_LOAD.triggerAll()
        }
    }
}
