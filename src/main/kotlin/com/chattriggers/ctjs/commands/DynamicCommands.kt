package com.chattriggers.ctjs.commands

import com.chattriggers.ctjs.CTClientCommandSource
import com.chattriggers.ctjs.engine.js.JSLoader
import com.chattriggers.ctjs.minecraft.wrappers.Player
import com.chattriggers.ctjs.minecraft.wrappers.World
import com.chattriggers.ctjs.minecraft.wrappers.entity.Entity
import com.chattriggers.ctjs.minecraft.wrappers.entity.PlayerMP
import com.chattriggers.ctjs.minecraft.wrappers.world.block.BlockFace
import com.chattriggers.ctjs.minecraft.wrappers.world.block.BlockPos
import com.chattriggers.ctjs.mixins.CommandContextAccessor
import com.chattriggers.ctjs.utils.MCEntity
import com.chattriggers.ctjs.utils.asMixin
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.ImmutableStringReader
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.*
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import gg.essential.universal.wrappers.message.UTextComponent
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.command.EntitySelector
import net.minecraft.command.argument.BlockPosArgumentType
import net.minecraft.command.argument.BlockPredicateArgumentType
import net.minecraft.command.argument.BlockStateArgument
import net.minecraft.command.argument.BlockStateArgumentType
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.GameModeArgumentType
import net.minecraft.command.argument.HeightmapArgumentType
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.command.argument.PosArgument
import net.minecraft.command.argument.SwizzleArgumentType
import net.minecraft.command.argument.Vec3ArgumentType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.predicate.NumberRange
import net.minecraft.registry.BuiltinRegistries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.util.TypeFilter
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.math.min

/**
 * An alternative to the command register that allows full use of the
 * functionality provided by Brigadier.
 */
object DynamicCommands : CommandCollection() {
    private var currentNode: DynamicCommand.Node? = null

    @JvmStatic
    fun registerCommand(name: String, builder: Function) = buildCommand(name, builder).register()

    @JvmStatic
    @JvmOverloads
    fun buildCommand(name: String, builder: Function? = null): DynamicCommand.Node.Root {
        require(currentNode == null) { "Command.buildCommand() called while already building a command" }
        val node = DynamicCommand.Node.Root(name)
        if (builder != null)
            processNode(node, builder)
        return node
    }

    @JvmStatic
    fun argument(name: String, type: ArgumentType<Any>, builder: Function) {
        requireNotNull(currentNode) { "Call to Commands.argument() outside of Commands.buildCommand()" }
        require(!currentNode!!.hasRedirect) { "Cannot redirect node with children" }
        val node = DynamicCommand.Node.Argument(currentNode, name, type)
        processNode(node, builder)
        currentNode!!.children.add(node)
    }

    @JvmStatic
    fun literal(name: String, builder: Function) {
        requireNotNull(currentNode) { "Call to Commands.literal() outside of Commands.buildCommand()" }
        require(!currentNode!!.hasRedirect) { "Cannot redirect node with children" }
        val node = DynamicCommand.Node.Literal(currentNode, name)
        processNode(node, builder)
        currentNode!!.children.add(node)
    }

    @JvmStatic
    @JvmOverloads
    fun redirect(node: DynamicCommand.Node.Root, modifier: Function? = null) {
        requireNotNull(currentNode) { "Call to Commands.redirect() outside of Commands.buildCommand()" }
        require(!currentNode!!.hasRedirect) { "Duplicate call to Commands.redirect()" }

        val redirectModifier = modifier ?: object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any>): Any {
                check(args.size == 1)
                val ctx = args[0]
                check(ctx is CommandContext<*>)
                val source = ctx.source
                check(source is CTClientCommandSource)

                for ((name, arg) in source.asMixin<CommandContextAccessor>().arguments)
                    source.setContextValue(name, arg.result)

                return Undefined.instance
            }
        }

        currentNode!!.children.add(DynamicCommand.Node.Redirect(currentNode, node, redirectModifier))
        currentNode!!.hasRedirect = true
    }

    @JvmStatic
    fun exec(method: Function) {
        requireNotNull(currentNode) { "Call to Commands.argument() outside of Commands.buildCommand()" }
        require(!currentNode!!.hasRedirect) { "Cannot execute node with children" }
        require(currentNode!!.method == null) { "Duplicate call to Commands.exec()" }
        currentNode!!.method = method
    }

    @JvmStatic
    fun boolean(): BoolArgumentType = BoolArgumentType.bool()

    @JvmStatic
    @JvmOverloads
    fun int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): IntegerArgumentType =
        IntegerArgumentType.integer(min, max)

    @JvmStatic
    @JvmOverloads
    fun long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): LongArgumentType =
        LongArgumentType.longArg(min, max)

    @JvmStatic
    @JvmOverloads
    fun float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE): FloatArgumentType =
        FloatArgumentType.floatArg(min, max)

    @JvmStatic
    @JvmOverloads
    fun double(min: Double = Double.MIN_VALUE, max: Double = Double.MAX_VALUE): DoubleArgumentType =
        DoubleArgumentType.doubleArg(min, max)

    @JvmStatic
    fun string(): StringArgumentType = StringArgumentType.string()

    @JvmStatic
    fun greedyString(): StringArgumentType = StringArgumentType.greedyString()

    @JvmStatic
    fun word(): StringArgumentType = StringArgumentType.word()

    @JvmStatic
    fun blockPos(): ArgumentType<PosArgument> {
        return wrapArgument(BlockPosArgumentType.blockPos(), ::PosArgumentWrapper)
    }

    @JvmStatic
    fun blockPredicate(): ArgumentType<BlockPredicateWrapper> {
        val registryAccess = CommandManager.createRegistryAccess(BuiltinRegistries.createWrapperLookup())
        val predicate = BlockPredicateArgumentType.blockPredicate(registryAccess)
        return wrapArgument(predicate, ::BlockPredicateWrapper)
    }

    @JvmStatic
    fun blockState(): ArgumentType<BlockStateArgumentWrapper> {
        val registryAccess = CommandManager.createRegistryAccess(BuiltinRegistries.createWrapperLookup())
        val predicate = BlockStateArgumentType.blockState(registryAccess)
        return wrapArgument(predicate, ::BlockStateArgumentWrapper)
    }

    @JvmStatic
    fun entity() = wrapArgument(EntityArgumentType.entity()) { EntitySelectorWrapper(it).getEntity() }

    @JvmStatic
    fun entities() = wrapArgument(EntityArgumentType.entities()) { EntitySelectorWrapper(it).getEntities() }

    @JvmStatic
    fun player() = wrapArgument(EntityArgumentType.player()) {
        EntitySelectorWrapper(it).getPlayers().let { players ->
            when {
                players.isEmpty() -> throw EntityArgumentType.PLAYER_NOT_FOUND_EXCEPTION.create()
                players.size > 1 -> throw EntityArgumentType.TOO_MANY_PLAYERS_EXCEPTION.create()
                else -> players[0]
            }
        }
    }

    @JvmStatic
    fun players() = wrapArgument(EntityArgumentType.players()) { EntitySelectorWrapper(it).getPlayers() }

    @JvmStatic
    fun gameProfile() = players()

    @JvmStatic
    fun identifier() = IdentifierArgumentType.identifier()

    @JvmStatic
    fun gameMode() = GameModeArgumentType.gameMode()

    @JvmStatic
    fun choices(vararg options: String): ArgumentType<String> {
        require(options.isNotEmpty()) {
            "No strings passed to Commands.choices()"
        }
        require(options.all { CommandDispatcher.ARGUMENT_SEPARATOR_CHAR !in it }) {
            "Commands.choices() cannot accept strings with spaces"
        }
        require(options.none(String::isEmpty)) {
            "Commands.choices() cannot accept empty strings"
        }

        return object : ArgumentType<String> {
            override fun parse(reader: StringReader): String {
                val start = reader.cursor
                val optionChars = options.toMutableList()

                var offset = 0
                while (reader.canRead()) {
                    val ch = reader.read()
                    optionChars.removeIf { it[offset] != ch }
                    if (optionChars.isEmpty())
                        reader.fail(start)
                    offset += 1

                    val found = optionChars.find { it.length == offset }
                    if (found != null)
                        return found
                }

                reader.fail(start)
            }

            override fun <S : Any?> listSuggestions(
                context: CommandContext<S>,
                builder: SuggestionsBuilder
            ): CompletableFuture<Suggestions> {
                options.forEach(builder::suggest)
                return builder.buildFuture()
            }

            override fun getExamples(): MutableCollection<String> = options.toMutableList()

            private fun StringReader.fail(originalOffset: Int): Nothing {
                cursor = originalOffset
                error(this, "Expected one of: ${options.joinToString(", ")}")
            }
        }
    }

    @JvmStatic
    fun dimension() = wrapArgument(choices(
        "minecraft:overworld",
        "minecraft:the_nether",
        "minecraft:the_end",
        "minecraft:overworld_caves",
    )) { name ->
        Entity.DimensionType.values().first { it.toMC().value.toString() == name }
    }

    @JvmStatic
    fun entityAnchor() = EntityAnchorArgumentType.entityAnchor()

    @JvmStatic
    fun heightmap() = HeightmapArgumentType.heightmap()

    @JvmStatic
    fun swizzle() = wrapArgument(SwizzleArgumentType.swizzle()) { it.map(BlockFace.Axis::fromMC) }

    @JvmStatic
    @JvmOverloads
    fun vec3(centerIntegers: Boolean = true) = wrapArgument(Vec3ArgumentType.vec3(centerIntegers), ::PosArgumentWrapper)

    @JvmStatic
    fun custom(obj: NativeObject): ArgumentType<Any> {
        val parse = obj["parse"] as? Function ?: error(
            "Object provided to Commands.custom() must contain a \"parse\" function"
        )

        val suggest = obj["suggest"]?.let {
            require(it is Function) { "A \"suggest\" key in a custom command argument type must be a Function" }
            it
        }

        val getExamples = obj["getExamples"]?.let {
            require(it is Function) { "A \"getExamples\" key in a custom command argument type must be a Function" }
            it
        }

        return object : ArgumentType<Any> {
            override fun parse(reader: StringReader?): Any? {
                return try {
                    JSLoader.invoke(parse, arrayOf(reader))
                } catch (e: WrappedException) {
                    throw e.wrappedException
                }
            }

            override fun <S : Any?> listSuggestions(
                context: CommandContext<S>?,
                builder: SuggestionsBuilder?
            ): CompletableFuture<Suggestions> {
                return if (suggest != null) {
                    @Suppress("UNCHECKED_CAST")
                    JSLoader.invoke(suggest, arrayOf(context, builder)) as CompletableFuture<Suggestions>
                } else super.listSuggestions(context, builder)
            }

            override fun getExamples(): MutableCollection<String> {
                return if (getExamples != null) {
                    @Suppress("UNCHECKED_CAST")
                    JSLoader.invoke(getExamples, emptyArray()) as MutableCollection<String>
                } else super.getExamples()
            }

            override fun toString() = obj.toString()
        }
    }

    @JvmStatic
    fun error(reader: ImmutableStringReader, message: String): Nothing {
        throw SimpleCommandExceptionType(UTextComponent(message)).createWithContext(reader)
    }

    @JvmStatic
    fun error(reader: ImmutableStringReader, message: UTextComponent): Nothing =
        throw SimpleCommandExceptionType(message).createWithContext(reader)

    private fun getMockCommandSource(): ServerCommandSource {
        return ServerCommandSource(
            Player.toMC(),
            Player.getPos().toVec3d(),
            Player.getRotation(),
            null,
            0,
            Player.getName(),
            Player.getDisplayName(),
            null,
            Player.toMC(),
        )
    }

    private fun <T, U> wrapArgument(base: ArgumentType<T>, block: (T) -> U): ArgumentType<U> {
        return object : ArgumentType<U> {
            override fun parse(reader: StringReader): U = block(base.parse(reader))

            override fun <S : Any?> listSuggestions(
                context: CommandContext<S>,
                builder: SuggestionsBuilder,
            ) = base.listSuggestions(context, builder)

            override fun getExamples() = base.examples

            override fun toString() = base.toString()
        }
    }

    data class PosArgumentWrapper(val impl: PosArgument) : PosArgument by impl {
        fun toAbsolutePos(): Vec3d = impl.toAbsolutePos(getMockCommandSource())

        fun toAbsoluteBlockPos(): BlockPos = BlockPos(impl.toAbsoluteBlockPos(getMockCommandSource()))

        fun toAbsoluteRotation(): Vec2f = impl.toAbsoluteRotation(getMockCommandSource())

        override fun toString() = "PosArgument"
    }

    data class BlockPredicateWrapper(val impl: BlockPredicateArgumentType.BlockPredicate) {
        fun test(blockPos: BlockPos): Boolean {
            return impl.test(CachedBlockPosition(World.toMC(), blockPos.toMC(), true))
        }

        override fun toString() = "BlockPredicateArgument"
    }

    data class BlockStateArgumentWrapper(val impl: BlockStateArgument) {
        fun test(blockPos: BlockPos): Boolean =
            impl.test(CachedBlockPosition(World.toMC(), blockPos.toMC(), true))

        override fun toString() = "BlockStateArgument"
    }

    class EntitySelectorWrapper(private val impl: EntitySelector) {
        private val limit by lazy { limitField(impl) }
        private val includesNonPlayers by lazy { includesNonPlayersField(impl) }
        private val basePredicate by lazy { basePredicateField(impl) }
        private val distance by lazy { distanceField(impl) }
        private val positionOffset by lazy { positionOffsetField(impl) }
        private val box by lazy { boxField(impl) }
        private val sorter by lazy { sorterField(impl) }
        private val senderOnly by lazy { senderOnlyField(impl) }
        private val playerName by lazy { playerNameField(impl) }
        private val uuid by lazy { uuidField(impl) }
        private val entityFilter by lazy { entityFilterField(impl) }

        fun getEntity(): Entity {
            val entities = getEntities()
            return when {
                entities.isEmpty() -> throw EntityArgumentType.ENTITY_NOT_FOUND_EXCEPTION.create()
                entities.size > 1 -> throw EntityArgumentType.TOO_MANY_ENTITIES_EXCEPTION.create()
                else -> entities[0]
            }
        }

        fun getEntities(): List<Entity> {
            return getUnfilteredEntities().filter {
                it.toMC().type.isEnabled(World.toMC()!!.enabledFeatures)
            }
        }

        private fun getUnfilteredEntities(): List<Entity> {
            if (!includesNonPlayers)
                return getPlayers()

            if (playerName != null) {
                val entity = World.getAllEntitiesOfType(PlayerEntity::class.java).find {
                    it.getName() == playerName
                }
                return listOfNotNull(entity)
            }

            if (uuid != null) {
                val entity = World.getAllEntitiesOfType(PlayerEntity::class.java).find {
                    it.getUUID() == uuid
                }
                return listOfNotNull(entity)
            }

            val position = positionOffset.apply(Player.getPos().toVec3d())
            val predicate = getPositionPredicate(position)
            if (senderOnly) {
                if (predicate.test(Player.toMC()!!))
                    return listOf(Player.asPlayerMP()!!)
                return emptyList()
            }

            val entities = mutableListOf<MCEntity>()
            appendEntitiesFromWorld(entities, position, predicate)
            return getEntities(position, entities).map(Entity::fromMC)
        }

        fun getPlayers(): List<PlayerMP> {
            if (playerName != null) {
                val entity = World.getAllEntitiesOfType(PlayerEntity::class.java).find {
                    it.getName() == playerName
                }
                @Suppress("UNCHECKED_CAST")
                return listOfNotNull(entity) as List<PlayerMP>
            }

            if (uuid != null) {
                val entity = World.getAllEntitiesOfType(PlayerEntity::class.java).find {
                    it.getUUID() == uuid
                }
                @Suppress("UNCHECKED_CAST")
                return listOfNotNull(entity) as List<PlayerMP>
            }

            val position = positionOffset.apply(Player.getPos().toVec3d())
            val predicate = getPositionPredicate(position)
            if (senderOnly) {
                if (predicate.test(Player.toMC()!!))
                    return listOf(Player.asPlayerMP()!!)
                return emptyList()
            }

            val limit = if (sorter == EntitySelector.ARBITRARY) limit else Int.MAX_VALUE
            val players = World.toMC()!!.players.filter(predicate::test).take(limit).toMutableList()
            return getEntities(position, players).map { PlayerMP(it as PlayerEntity) }
        }

        private fun <T : MCEntity> getEntities(pos: Vec3d, entities: MutableList<T>): List<T> {
            if (entities.size > 1)
                sorter.accept(pos, entities)
            return entities.subList(0, min(limit, entities.size))
        }

        private fun appendEntitiesFromWorld(entities: MutableList<MCEntity>, pos: Vec3d, predicate: Predicate<MCEntity>) {
            val limit = if (sorter == EntitySelector.ARBITRARY) limit else Int.MAX_VALUE
            if (entities.size >= limit)
                return

            val min = pos.add(Vec3d(-1000.0, -1000.0, -1000.0))
            val max = pos.add(Vec3d(1000.0, 1000.0, 1000.0))
            val box = this.box?.offset(pos) ?: Box(min, max)
            World.toMC()!!.collectEntitiesByType(entityFilter, box, predicate, entities, limit)
        }

        private fun getPositionPredicate(pos: Vec3d): Predicate<MCEntity> {
            var predicate = basePredicate
            if (box != null) {
                val box = this.box!!.offset(pos)
                predicate = predicate.and { box.intersects(it.boundingBox) }
            }
            if (!distance.isDummy)
                predicate = predicate.and { distance.testSqrt(it.squaredDistanceTo(pos)) }
            return predicate
        }

        companion object {
            private val limitField = reflect<EntitySelector, Int>("limit")
            private val includesNonPlayersField = reflect<EntitySelector, Boolean>("includesNonPlayers")
            private val basePredicateField = reflect<EntitySelector, Predicate<MCEntity>>("basePredicate")
            private val distanceField = reflect<EntitySelector, NumberRange.FloatRange>("distance")
            private val positionOffsetField = reflect<EntitySelector, java.util.function.Function<Vec3d, Vec3d>>("positionOffset")
            private val boxField = reflect<EntitySelector, Box?>("box")
            private val sorterField = reflect<EntitySelector, BiConsumer<Vec3d, MutableList<out MCEntity>>>("sorter")
            private val senderOnlyField = reflect<EntitySelector, Boolean>("senderOnly")
            private val playerNameField = reflect<EntitySelector, String?>("playerName")
            private val uuidField = reflect<EntitySelector, UUID?>("uuid")
            private val entityFilterField = reflect<EntitySelector, TypeFilter<MCEntity, *>>("entityFilter")
        }
    }

    private inline fun <reified ClassT, reified FieldT> reflect(name: String): (ClassT) -> FieldT {
        val field = ClassT::class.java.getDeclaredField(name)
        field.isAccessible = true
        return { field.get(it) as FieldT }
    }

    private fun processNode(node: DynamicCommand.Node, builder: Function) {
        currentNode = node
        try {
            JSLoader.invoke(builder, emptyArray())
        } finally {
            currentNode = node.parent
        }
    }
}
