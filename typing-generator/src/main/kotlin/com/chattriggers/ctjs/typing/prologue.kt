package com.chattriggers.ctjs.typing

internal val globals = mapOf(
    "Thread" to "com.chattriggers.ctjs.engine.WrappedThread",
    "Console" to "com.chattriggers.ctjs.console.Console",
    "ArrayList" to "java.util.ArrayList",
    "HashMap" to "java.util.HashMap",
    "Keyboard" to "gg.essential.universal.UKeyboard",
    "ChatLib" to "com.chattriggers.ctjs.minecraft.libs.ChatLib",
    "FileLib" to "com.chattriggers.ctjs.minecraft.libs.FileLib",
    "MathLib" to "com.chattriggers.ctjs.minecraft.libs.MathLib",
    "Image" to "com.chattriggers.ctjs.minecraft.libs.renderer.Image",
    "Rectangle" to "com.chattriggers.ctjs.minecraft.libs.renderer.Rectangle",
    "Renderer" to "com.chattriggers.ctjs.minecraft.libs.renderer.Renderer",
    "Shape" to "com.chattriggers.ctjs.minecraft.libs.renderer.Shape",
    "Text" to "com.chattriggers.ctjs.minecraft.libs.renderer.Text",
    "Book" to "com.chattriggers.ctjs.minecraft.objects.Book",
    "KeyBind" to "com.chattriggers.ctjs.minecraft.objects.KeyBind",
    "Sound" to "com.chattriggers.ctjs.minecraft.objects.Sound",
    "Display" to "com.chattriggers.ctjs.minecraft.objects.Display",
    "Gui" to "com.chattriggers.ctjs.minecraft.objects.Gui",
    "Message" to "com.chattriggers.ctjs.minecraft.objects.Message",
    "TextComponent" to "com.chattriggers.ctjs.minecraft.objects.TextComponent",
    "BossBars" to "com.chattriggers.ctjs.minecraft.wrappers.BossBars",
    "Client" to "com.chattriggers.ctjs.minecraft.wrappers.Client",
    "CPS" to "com.chattriggers.ctjs.minecraft.wrappers.CPS",
    "Player" to "com.chattriggers.ctjs.minecraft.wrappers.Player",
    "Scoreboard" to "com.chattriggers.ctjs.minecraft.wrappers.Scoreboard",
    "Server" to "com.chattriggers.ctjs.minecraft.wrappers.Server",
    "TabList" to "com.chattriggers.ctjs.minecraft.wrappers.TabList",
    "World" to "com.chattriggers.ctjs.minecraft.wrappers.World",
    "BlockEntity" to "com.chattriggers.ctjs.minecraft.wrappers.entity.BlockEntity",
    "Entity" to "com.chattriggers.ctjs.minecraft.wrappers.entity.Entity",
    "LivingEntity" to "com.chattriggers.ctjs.minecraft.wrappers.entity.LivingEntity",
    "Particle" to "com.chattriggers.ctjs.minecraft.wrappers.entity.Particle",
    "PlayerMP" to "com.chattriggers.ctjs.minecraft.wrappers.entity.PlayerMP",
    "Team" to "com.chattriggers.ctjs.minecraft.wrappers.entity.Team",
    "Chunk" to "com.chattriggers.ctjs.minecraft.wrappers.world.Chunk",
    "PotionEffect" to "com.chattriggers.ctjs.minecraft.wrappers.world.PotionEffect",
    "PotionEffectType" to "com.chattriggers.ctjs.minecraft.wrappers.world.PotionEffectType",
    "Block" to "com.chattriggers.ctjs.minecraft.wrappers.world.block.Block",
    "BlockFace" to "com.chattriggers.ctjs.minecraft.wrappers.world.block.BlockFace",
    "BlockPos" to "com.chattriggers.ctjs.minecraft.wrappers.world.block.BlockPos",
    "BlockType" to "com.chattriggers.ctjs.minecraft.wrappers.world.block.BlockType",
    "Vec2f" to "com.chattriggers.ctjs.utils.vec.Vec2f",
    "Vec3f" to "com.chattriggers.ctjs.utils.vec.Vec3f",
    "Vec3i" to "com.chattriggers.ctjs.utils.vec.Vec3i",
    "Inventory" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.Inventory",
    "Item" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.Item",
    "ItemType" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.ItemType",
    "Slot" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.Slot",
    "Action" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.action.Action",
    "ClickAction" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.action.ClickAction",
    "DragAction" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.action.DragAction",
    "DropAction" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.action.DropAction",
    "KeyAction" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.action.KeyAction",
    "NBT" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.nbt.NBT",
    "NBTBase" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.nbt.NBTBase",
    "NBTTagCompound" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.nbt.NBTTagCompound",
    "NBTTagList" to "com.chattriggers.ctjs.minecraft.wrappers.inventory.nbt.NBTTagList",
    "TriggerRegister" to "com.chattriggers.ctjs.engine.Register",
    "OnChatTrigger" to "com.chattriggers.ctjs.triggers.ChatTrigger",
    "OnCommandTrigger" to "com.chattriggers.ctjs.triggers.CommandTrigger",
    "OnRegularTrigger" to "com.chattriggers.ctjs.triggers.RegularTrigger",
    "OnRenderTrigger" to "com.chattriggers.ctjs.triggers.EventTrigger",
    "OnSoundPlayerTrigger" to "com.chattriggers.ctjs.triggers.SoundPlayTrigger",
    "OnStepTrigger" to "com.chattriggers.ctjs.triggers.StepTrigger",
    "OnTrigger" to "com.chattriggers.ctjs.triggers.Trigger",
    "Priority" to "com.chattriggers.ctjs.triggers.Trigger.Priority",
    "Config" to "com.chattriggers.ctjs.utils.Config",
    "ChatTriggers" to "com.chattriggers.ctjs.Reference",
    "Commands" to "com.chattriggers.ctjs.commands.DynamicCommands",
    "GL11" to null,
    "GL12" to null,
    "GL13" to null,
    "GL14" to null,
    "GL15" to null,
    "GL20" to null,
    "GL21" to null,
    "GL30" to null,
    "GL31" to null,
    "GL32" to null,
    "GL33" to null,
    "GL40" to null,
    "GL41" to null,
    "GL42" to null,
    "GL43" to null,
    "GL44" to null,
    "GL45" to null,
)

private val staticPrologue = """
    const Java: {
      /**
       * Returns the Java Class or Package given by name. If you want to
       * enforce the name is a class, use Java.class() instead.
       */
      type(name: string): java.lang.Package | java.lang.Class<any>;
      
      /**
       * Returns the Java Class given by `className`. Throws an error if the
       * name is not a valid class name.
       */
      class(className: string): java.lang.Class<any>;
    };
    
    /**
     * Runs `func` in a Java synchronized() block with `lock` as the synchronizer
     */
    function sync(func: () => void, lock: any): void;
    
    /**
     * Cancels the given event
     */
    function cancel(event: any): void;
    
    /**
     * Creates a custom trigger. `name` can be used as the first argument of a
     * subsequent call to `register`. Returns an object that can be used to
     * invoke the trigger.
     */
    function createCustomTrigger(name: string): { trigger(...args: any): void };

    const console: {
      log(...args: any): void;
    };
    
    interface RegisterTypes {
      chat(...args: (string | unknown)[]): com.chattriggers.ctjs.triggers.ChatTrigger;
      actionBar(...args: (string | unknown)[]): com.chattriggers.ctjs.triggers.ChatTrigger;
      worldLoad(): com.chattriggers.ctjs.triggers.Trigger;
      worldUnload(): com.chattriggers.ctjs.triggers.Trigger;
      clicked(mouseX: number, mouseY: number, button: number, isPressed: boolean): com.chattriggers.ctjs.triggers.Trigger;
      scrolled(mouseX: number, mouseY: number, scrollDelta: number): com.chattriggers.ctjs.triggers.Trigger;
      dragged(mouseXDelta: number, mouseYDelta: number, mouseX: number, mouseY: number, mouseButton: number): com.chattriggers.ctjs.triggers.Trigger;
      soundPlay(position: com.chattriggers.ctjs.utils.vec.Vec3f, name: string, volume: number, pitch: number, category: net.minecraft.sound.SoundCategory, event: unknown): com.chattriggers.ctjs.triggers.SoundPlayTrigger;
      tick(ticksElapsed: number): com.chattriggers.ctjs.triggers.Trigger;
      step(stepsElapsed: number): com.chattriggers.ctjs.triggers.StepTrigger;
      renderWorld(partialTicks: number): com.chattriggers.ctjs.triggers.Trigger;
      preRenderWorld(partialTicks: number): com.chattriggers.ctjs.triggers.Trigger;
      postRenderWorld(partialTicks: number): com.chattriggers.ctjs.triggers.Trigger;
      renderOverlay(): com.chattriggers.ctjs.triggers.Trigger;
      renderPlayerList(event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      drawBlockHighlight(position: com.chattriggers.ctjs.minecraft.wrappers.world.block.BlockPos, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      gameLoad(): com.chattriggers.ctjs.triggers.Trigger;
      gameUnload(): com.chattriggers.ctjs.triggers.Trigger;
      command(...args: string[]): com.chattriggers.ctjs.triggers.Trigger;
      guiOpened(screen: net.minecraft.client.gui.screen.Screen, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      guiClosed(screen: net.minecraft.client.gui.screen.Screen): com.chattriggers.ctjs.triggers.Trigger;
      dropItem(item: com.chattriggers.ctjs.minecraft.wrappers.inventory.Item, entireStack: boolean, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      messageSent(message: string, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      itemTooltip(lore: com.chattriggers.ctjs.minecraft.objects.TextComponent[], item: com.chattriggers.ctjs.minecraft.wrappers.inventory.Item, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      playerInteract(interaction: com.chattriggers.ctjs.minecraft.wrappers.Player.Interaction, interactionTarget: com.chattriggers.ctjs.minecraft.wrappers.entity.Entity | com.chattriggers.ctjs.minecraft.wrappers.world.block.Block | com.chattriggers.ctjs.minecraft.wrappers.inventory.Item, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      entityDamage(entity: com.chattriggers.ctjs.minecraft.wrappers.entity.Entity, attacker: com.chattriggers.ctjs.minecraft.wrappers.entity.PlayerMP): com.chattriggers.ctjs.triggers.Trigger;
      entityDeath(entity: com.chattriggers.ctjs.minecraft.wrappers.entity.Entity): com.chattriggers.ctjs.triggers.Trigger;
      guiRender(mouseX: number, mouseY: number, screen: net.minecraft.client.gui.screen.Screen): com.chattriggers.ctjs.triggers.Trigger;
      guiKey(char: String, keyCode: number, screen: net.minecraft.client.gui.screen.Screen, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      guiMouseClick(mouseX: number, mouseY: number, mouseButton: number, isPressed: boolean, screen: net.minecraft.client.gui.screen.Screen, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      guiMouseDrag(mouseXDelta: number, mouseYDelta: number, mouseX: number, mouseY: number, isPressed: boolean, screen: net.minecraft.client.gui.screen.Screen, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
      packetSent(packet: net.minecraft.network.packet.Packet<unknown>, event: unknown): com.chattriggers.ctjs.triggers.PacketTrigger;
      packetReceived(packet: net.minecraft.network.packet.Packet<unknown>, event: unknown): com.chattriggers.ctjs.triggers.PacketTrigger;
      serverConnect(): com.chattriggers.ctjs.triggers.Trigger;
      serverDisconnect(): com.chattriggers.ctjs.triggers.Trigger;
      renderEntity(entity: com.chattriggers.ctjs.minecraft.wrappers.entity.Entity, partialTicks: number, event: unknown): com.chattriggers.ctjs.triggers.RenderEntityTrigger;
      renderBlockEntity(blockEntity: com.chattriggers.ctjs.minecraft.wrappers.entity.BlockEntity, partialTicks: number, event: unknown): com.chattriggers.ctjs.triggers.RenderBlockEntityTrigger;
      postGuiRender(mouseX: number, mouseY: number, screen: net.minecraft.client.gui.screen.Screen): com.chattriggers.ctjs.triggers.Trigger;
      spawnParticle(particle: com.chattriggers.ctjs.minecraft.wrappers.entity.Particle, event: unknown): com.chattriggers.ctjs.triggers.EventTrigger;
    }

    function register<T extends keyof RegisterTypes>(
      name: T, 
      cb: (...args: Parameters<RegisterTypes[T]>) => void,
    ): ReturnType<RegisterTypes[T]>;

""".trimIndent()

fun wrapTypings(typing: String): String {
    return buildString {
        append("""
            /// <reference no-default-lib="true" />
            /// <reference lib="es2015" />
            export {};
            
            declare global {

        """.trimIndent())

        append(staticPrologue.prependIndent("  "))
        append('\n')

        for ((name, clazz) in globals) {
            val type = clazz?.let { "typeof $it" } ?: "unknown"
            append("  const $name: $type;\n")
        }

        append(typing.prependIndent("  "))

        append("\n}\n")
    }
}
