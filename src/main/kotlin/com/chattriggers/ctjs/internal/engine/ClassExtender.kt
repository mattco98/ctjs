package com.chattriggers.ctjs.internal.engine

import codes.som.koffee.assembleClass
import codes.som.koffee.insns.InstructionAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.*
import codes.som.koffee.modifiers.Modifiers
import codes.som.koffee.modifiers.abstract
import codes.som.koffee.modifiers.public
import codes.som.koffee.types.*
import com.chattriggers.ctjs.CTJS
import com.chattriggers.ctjs.internal.launch.Descriptor
import org.mozilla.javascript.*
import org.mozilla.javascript.Function
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.TryCatchBlockNode
import java.io.File
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader

interface MockInterface {
    fun sum(a: Int, b: Int): Int
}

abstract class MockClass {
    abstract fun doThing()
}

/*

class BackingObject {
    int foo(int a) {
        return (int) Context.jsToJava(this.jsImpl.foo$1(..., new Object[]{a}), int.class)
    }

    float foo(float a) {
        return (float) Context.jsToJava(this.jsImpl.foo$2(..., new Object[]{a}), float.class)
    }
}

class WrapperObject {
    Object js_foo(Context, cx, ..., Object[] args) {
        // TODO: Determine appropriate method from args
    }

    Object foo$1(Context cx, ..., Object[] args) {
        // ...
    }
    Object foo$2(Context cx, ..., Object[] args) {
        // ...
    }
    Object foo$fallback(Context, cx, ..., Object[] args) {

    }
}

 */

class ClassExtender private constructor(
    private val className: String,
    private val superClass: Class<*>,
    private val interfaces: List<Class<*>>,
    private val instanceImpl: NativeObject?,
    private val staticImpl: NativeObject?,
) {
    // Use separate class loaders for each instance so that they can be eagerly garbage collected
    private var cl = OpenClassLoader()

    private val backingName = "${superClass.simpleName}_backingObj_$counter"
    private val backingType = "$GENERATED_PACKAGE/${backingName}"

    private val wrapperName = "${superClass.simpleName}_wrapperObj_$counter"
    private val wrapperType = "$GENERATED_PACKAGE/$wrapperName"

    // Instance IDs start at 2 since "constructor" is always 1
    private val instanceMembers = getMembers(instanceImpl, idStart = 2, idStep = 1)
    private val staticMembers = getMembers(staticImpl, idStart = -1, idStep = -1)
    private val minId = staticMembers.idProvider.nextId + 1
    private val maxId = instanceMembers.idProvider.nextId - 1
    private val isAbstract: Boolean

    init {
        val overridableMethods = collectOverridableMethods(superClass) +
            interfaces.flatMapTo(mutableSetOf(), ::collectOverridableMethods)

        val remainingMethods = overridableMethods.toMutableSet()

        for (method in overridableMethods) {
            if (instanceMembers.methods.associate(method))
                remainingMethods.remove(method)
        }

        isAbstract = remainingMethods.any {
            Modifier.isAbstract(it.modifiers)
        }

        counter++
    }

    // TODO: Symbol/number properties
    // TODO: Getters/setters
    private fun getMembers(obj: NativeObject?, idStart: Int, idStep: Int): ImplementationMembers {
        val members = ImplementationMembers(IdProvider(idStart, idStep))

        if (obj != null) {
            for (name in obj.ids) {
                if (name is String)
                    members.add(name, obj[name])
            }
        }

        return members
    }

    data class IdProvider(var nextId: Int, var step: Int)

    data class ImplementationMembers(
        val idProvider: IdProvider,
        val properties: MutableMap<String, Any?> = mutableMapOf(),
        val methods: ImplementationMethods = ImplementationMethods(),
        val ids: MutableMap<String, Int> = mutableMapOf(),
    ) {
        fun add(name: String, value: Any?) {
            require(name !in properties) {
                "Duplicate property \"$name\" in implementation object"
            }

            if (value is Function) {
                methods.add(name, value)
            } else {
                properties[name] = value
            }

            // Only functions get IDs
            if (value is Function) {
                ids[name] = idProvider.nextId
                idProvider.nextId += idProvider.step
            }
        }
    }

    class ImplementationMethods {
        private val methods = mutableMapOf<String, MutableList<ImplMethod>>()

        fun add(signature: String, function: Function) {
            val (name, type) = parseMethodSignature(signature)
            val list = methods.getOrPut(name, ::mutableListOf)
            require(list.none { it.type == type }) {
                "Multiple implementation methods with same signature \"$type\""
            }
            list.add(ImplMethod(function, type, null))
        }

        fun associate(method: Method): Boolean {
            val methodList = methods[method.name] ?: return false
            val handle = MethodHandles.lookup().unreflect(method)
            val type = handle.type()

            methodList.forEach {
                if (it.type == type) {
                    it.javaHandle = handle
                    return true
                }
            }

            return false
        }

        // TODO: Parse these in the same way Rhino does
        private fun parseMethodSignature(signature: String): Pair<String, MethodType> {
            val paren = signature.indexOf('(')
            if (paren == -1)
                return signature to MethodType.methodType(Any::class.java, Array<Any>::class.java)

            val name = signature.substring(0, paren)
            val descriptor = Descriptor.Parser(signature.substring(paren)).parseMethod(full = true)
            return name to descriptor.originalMethodType
        }

        operator fun iterator() = methods.entries.iterator()

        data class ImplMethod(
            val function: Function,
            val type: MethodType,
            var javaHandle: MethodHandle?,
        )
    }

    private fun collectOverridableMethods(clazz: Class<*>): Set<Method> {
        val methods = mutableSetOf<Method>()

        for (method in clazz.declaredMethods) {
            if (method.modifiers.let { Modifier.isFinal(it) || Modifier.isPrivate(it) || Modifier.isStatic(it) })
                continue

            methods.add(method)
        }

        clazz.superclass?.let { methods += collectOverridableMethods(it) }
        clazz.interfaces.forEach { methods += collectOverridableMethods(it) }

        return methods
    }

    fun generate(): Any {
        generateBackingClass()

        val instance: IdScriptableObject = generateWrapperClass()
            .getConstructor(NativeObject::class.java, NativeObject::class.java, Boolean::class.java)
            .newInstance(staticImpl, instanceImpl, true) as IdScriptableObject

        return instance.exportAsJSClass(maxId, null, false, true)
    }

    private fun generateWrapperClass(): Class<*> {
        val node = assembleClass(
            public,
            wrapperType,
            version = Opcodes.V17,
            superClass = IdScriptableObject::class,
            interfaces = listOf(Wrapper::class),
        ) {
            field(private + static + final, "TAG", String::class, wrapperType)
            field(private + static, "staticImpl", NativeObject::class)
            field(private + static, "instanceImpl", NativeObject::class)

            field(private + final, "backingObject", Any::class)
            field(private, "isConstructor", boolean)

            method(public, "<init>", void, NativeObject::class, NativeObject::class, boolean) {
                getstatic(wrapperType, "instanceImpl", NativeObject::class)
                ifStatement(JumpCondition.NonNull) {
                    construct<IllegalStateException>(String::class) {
                        ldc("Duplicate construction of Scriptable class object $wrapperName")
                    }
                    athrow
                }

                aload_0
                invokespecial<IdScriptableObject>("<init>", void)

                aload_1
                putstatic(wrapperType, "staticImpl", NativeObject::class)

                aload_2
                putstatic(wrapperType, "instanceImpl", NativeObject::class)

                aload_0
                ldc(true)
                putfield(wrapperType, "isConstructor", boolean)

                _return
            }

            method(public, "<init>", void, backingType) {
                getstatic(wrapperType, "instanceImpl", NativeObject::class)
                ifStatement(JumpCondition.Null) {
                    construct<IllegalStateException>(String::class) {
                        ldc("Construction of Scriptable class instance $wrapperName before constructor")
                    }
                    athrow
                }

                aload_0
                invokespecial<IdScriptableObject>("<init>", void)

                aload_0
                aload_1
                putfield(wrapperType, "backingObject", backingType)

                aload_0
                ldc(false)
                putfield(wrapperType, "isConstructor", boolean)

                aload_1
                aload_0
                putfield(backingType, "jsWrapper", wrapperType)

                _return
            }

            method(public, "unwrap", backingType) {
                aload_0
                getfield(wrapperType, "backingObject", backingType)
                areturn
            }

            method(protected, "fillConstructorProperties", void, IdFunctionObject::class) {
                val propertyAttr = ScriptableObject.NOT_ENUMERABLE or
                    ScriptableObject.NOT_CONFIGURABLE or
                    ScriptableObject.NOT_WRITABLE

                for ((name, value) in staticMembers.properties) {
                    aload_1
                    ldc(name)

                    when {
                        value == null -> aconst_null
                        value::class.java.isPrimitive ||
                            value is String ||
                            value is Class<*> ||
                            value is MethodHandle ||
                            value is ConstantDynamic -> ldc(value)
                        else -> {
                            aload_0
                            getfield(wrapperType, "staticImpl", NativeObject::class)
                            ldc(name)
                            invokevirtual<Map<*, *>>("get", Any::class, String::class)
                        }
                    }

                    ldc(propertyAttr)

                    invokevirtual<IdFunctionObject>("defineProperty", void, String::class, Any::class, int)
                }

                for ((name, methods) in staticMembers.methods) {
                    val largestArity = methods.maxOf {
                        ScriptRuntime.toNumber(it.function.get("length", it.function)).toInt()
                    }
                    aload_0
                    aload_1

                    getstatic(wrapperType, "TAG", String::class)

                    ldc(staticMembers.ids[name]!!)
                    ldc(name)
                    ldc(largestArity)

                    invokevirtual(
                        wrapperType,
                        "addIdFunctionProperty",
                        void,
                        Scriptable::class,
                        Any::class,
                        int,
                        String::class,
                        int
                    )
                }

                aload_0
                aload_1
                invokevirtual<IdScriptableObject>("addCtorSpecies", void, IdFunctionObject::class)
                _return
            }

            method(
                public,
                "execIdCall",
                Any::class,
                IdFunctionObject::class,
                Context::class,
                Scriptable::class,
                Scriptable::class,
                Array<Any>::class
            ) {
                aload_1
                getstatic(wrapperType, "TAG", String::class)
                invokevirtual<IdFunctionObject>("hasTag", boolean, Any::class)
                ifStatement(JumpCondition.False) {
                    aload_0
                    aload_1
                    aload_2
                    aload_3
                    aload(4)
                    aload(5)
                    invokespecial<IdScriptableObject>(
                        "execIdCall",
                        Any::class,
                        IdFunctionObject::class,
                        Context::class,
                        Scriptable::class,
                        Scriptable::class,
                        Array<Any>::class
                    )
                    areturn
                }

                // Extra case for id zero, the default case, which throws
                val methodLabels = Array(maxId - minId + 1) { makeLabel() }

                aload_1
                invokevirtual<IdFunctionObject>("methodId", int)
                tableswitch(minId, maxId, methodLabels[-minId], *methodLabels)

                for (name in staticMembers.methods.keys) {
                    placeLabel(methodLabels[staticMembers.ids[name]!! - minId])

                    aload_2
                    aload_3
                    aload(4)
                    aload(5)
                    invokestatic(
                        wrapperType,
                        "js_$name",
                        Any::class,
                        Context::class,
                        Scriptable::class,
                        Scriptable::class,
                        Array<Any>::class
                    )
                    areturn
                }

                for (name in instanceMembers.methods.keys) {
                    placeLabel(methodLabels[instanceMembers.ids[name]!! - minId])

                    aload(4)
                    aload_1
                    invokestatic(wrapperType, "realThis", wrapperType, Scriptable::class, IdFunctionObject::class)

                    aload_2
                    aload_3
                    aload(5)
                    invokevirtual(
                        wrapperType,
                        "js_$name",
                        Any::class,
                        Context::class,
                        Scriptable::class,
                        Array<Any>::class
                    )
                    areturn
                }

                // Constructor case (always ID 1)
                placeLabel(methodLabels[-minId + 1])
                aload_0
                aload_2
                aload_3
                aload(4)
                aload(5)
                invokevirtual(
                    wrapperType,
                    "js_construct",
                    wrapperType,
                    Context::class,
                    Scriptable::class,
                    Scriptable::class,
                    Array<Any>::class
                )
                areturn

                // Default case
                placeLabel(methodLabels[-minId])
                construct<IllegalArgumentException>(String::class) {
                    construct<StringBuilder>()
                    ldc("$wrapperName.prototype has no method: ")
                    invokevirtual<StringBuilder>("append", StringBuilder::class, String::class)
                    aload_1
                    invokevirtual<IdFunctionObject>("getFunctionName", String::class)
                    invokevirtual<StringBuilder>("append", StringBuilder::class, String::class)
                    invokevirtual<StringBuilder>("toString", String::class)
                }
                athrow
            }

            method(private + static, "realThis", wrapperType, Scriptable::class, IdFunctionObject::class) {
                aload_0
                ifStatement(JumpCondition.Null) {
                    aload_1
                    invokestatic<IdFunctionObject>("incompatibleCallError", EcmaError::class, IdFunctionObject::class)
                    athrow
                }

                val start = makeLabel()
                val end = makeLabel()
                val handler = makeLabel()

                placeLabel(start)
                aload_0
                invokestatic<ScriptRuntime>("unwrapProxy", Scriptable::class, Scriptable::class)
                checkcast(wrapperType)
                areturn
                placeLabel(end)

                placeLabel(handler)
                aload_1
                invokestatic<IdFunctionObject>("incompatibleCallError", EcmaError::class, IdFunctionObject::class)
                athrow

                val tryCatch = TryCatchBlockNode(start, end, handler, "java/lang/ClassCastException")
                tryCatchBlocks.add(tryCatch)
            }

            method(protected, "initPrototypeId", void, int) {
                val end = makeLabel()

                val methodLabels = Array(maxId + 1) { makeLabel() }

                aload_0
                getstatic(wrapperType, "TAG", String::class)
                iload_1

                iload_1
                tableswitch(0, maxId, methodLabels[0], *methodLabels)

                for ((name, method) in instanceMembers.methods) {
                    placeLabel(methodLabels[instanceMembers.ids[name]!!])

                    ldc(name)
                    aconst_null
                    push_int(ScriptRuntime.toNumber(method.get("length", method)).toInt())
                    goto(end)
                }

                // Constructor case (always ID 1)
                val constructor = instanceMembers.methods["constructor"]
                placeLabel(methodLabels[1])
                ldc("constructor")
                aconst_null
                if (constructor != null) {
                    push_int(ScriptRuntime.toNumber(constructor.get("length", constructor)).toInt())
                } else {
                    push_int(0)
                }
                goto(end)

                // Default case
                placeLabel(methodLabels[0])
                construct<IllegalArgumentException>(String::class) {
                    iload_1
                    invokestatic<String>("valueOf", String::class, int)
                }
                athrow

                placeLabel(end)
                invokevirtual(
                    wrapperType,
                    "initPrototypeMethod",
                    IdFunctionObject::class,
                    Any::class,
                    int,
                    String::class,
                    String::class,
                    int
                )
                pop
                _return
            }

            method(protected, "findPrototypeId", int, String::class) {
                val default = makeLabel()

                // We always have a custom constructor, even if the user doesn't provide one
                val labels = (instanceMembers.methods.keys + "constructor").map { it to makeLabel() }

                aload_1
                invokevirtual<String>("hashCode", int)
                lookupswitch(
                    default,
                    *labels.map { it.first.hashCode() to it.second }.sortedBy { it.first }.toTypedArray()
                )

                for ((name, label) in labels) {
                    placeLabel(label)
                    aload_1
                    ldc(name)
                    invokevirtual<String>("equals", boolean, Any::class)
                    ifStatement(JumpCondition.True) {
                        val id = instanceMembers.ids[name]
                        if (id != null) {
                            push_int(id)
                        } else {
                            require(name == "constructor")
                            push_int(1)
                        }
                        ireturn
                    }
                    goto(default)
                }

                placeLabel(default)
                push_int(0)
                ireturn
            }

            method(public, "getClassName", String::class) {
                ldc(className)
                areturn
            }

            // TODO: JVM methods?
            for ((name, method) in staticMembers.methods) {
                method(
                    private + static,
                    "js_$name",
                    Any::class,
                    Context::class,
                    Scriptable::class,
                    Scriptable::class,
                    Array<Any>::class,
                ) {

                    getstatic(wrapperType, "staticImpl", NativeObject::class)
                    ldc(name)
                    invokeinterface<Map<*, *>>("get", Any::class, Any::class)
                    checkcast<Function>()
                    aload_0
                    aload_1
                    aload_2
                    aload_3
                    invokeinterface<Function>(
                        "call",
                        Any::class,
                        Context::class,
                        Scriptable::class,
                        Scriptable::class,
                        Array<Any>::class
                    )
                    areturn
                }
            }

            //     method(
            //         private,
            //         "js_construct",
            //         wrapperType,
            //         Context::class,
            //         Scriptable::class,
            //         Scriptable::class,
            //         Array<Any>::class
            //     ) {
            //         aload_3
            //         ifStatement(JumpCondition.NonNull) {
            //             ldc("msg.no.new")
            //             ldc(wrapperName)
            //             invokestatic<ScriptRuntime>("typeError1", EcmaError::class, String::class, Any::class)
            //             athrow
            //         }
            //
            //
            //     }
        }

        return cl.load(node)
    }

    private fun generateBackingClass(): Class<*> {
        var modifiers: Modifiers = public
        if (isAbstract)
            modifiers += abstract

        val node = assembleClass(
            modifiers,
            backingType,
            version = Opcodes.V17,
            superClass = superClass,
            interfaces = interfaces
        ) {
            field(private, "jsWrapper", wrapperType)

            this@ClassExtender.interfaces.forEach { intf ->
                intf.declaredMethods.forEach method@{ intfMethod ->
                    var modifiers = when {
                        Modifier.isProtected(intfMethod.modifiers) -> protected
                        Modifier.isPrivate(intfMethod.modifiers) -> return@method
                        else -> public
                    }

                    if (Modifier.isStatic(intfMethod.modifiers))
                        modifiers += static

                    method(
                        modifiers,
                        intfMethod.name,
                        intfMethod.returnType,
                        *intfMethod.parameterTypes
                    ) {
                        invokestatic(Context::class, "getScope", Scriptable::class)
                        val scope = astore()

                        aload_0
                        getfield(backingType, "jsWrapper", wrapperType)

                        // returnType, jsWrapper

                        invokestatic(Context::class, "getContext", Context::class)
                        // returnType, jsWrapper, cx
                        aload(scope)
                        // returnType, jsWrapper, cx, scope

                        ldc(intfMethod.parameterTypes.size)
                        // returnType, jsWrapper, cx, scope, 2
                        anewarray<Any>()
                        // returnType, jsWrapper, cx, scope, args

                        for ((i, type) in intfMethod.parameterTypes.withIndex()) {
                            dup
                            // returnType, jsWrapper, cx, scope, args, args
                            ldc(i)
                            // returnType, jsWrapper, cx, scope, args, args, 0

                            getLoadInsn(type)(i + 1)
                            boxIfNecessary(type)
                            // returnType, jsWrapper, cx, scope, args, args, 0, param
                            aload(scope)
                            // returnType, jsWrapper, cx, scope, args, args, 0, param, scope
                            invokestatic<Context>("javaToJS", Any::class, Any::class, Scriptable::class)

                            aastore
                        }

                        // returnType, jsWrapper, cx, scope, args
                        invokevirtual(
                            wrapperType,
                            "js_" + intfMethod.name,
                            Any::class,
                            Context::class,
                            Scriptable::class,
                            Array<Any>::class,
                        )
                        // returnType, value
                        ldc(intfMethod.returnType)
                        // returnType, value, type
                        invokestatic<Context>("jsToJava", Any::class, Any::class, Class::class)
                        // returnType, value

                        checkcast(intfMethod.returnType)

                        getReturnInsn(intfMethod.returnType)()
                    }
                }
            }
        }

        return cl.load(node)
    }

    private fun InstructionAssembly.getLoadInsn(type: Class<*>): (Int) -> Unit {
        return when (type) {
            java.lang.Boolean.TYPE, java.lang.Byte.TYPE, Character.TYPE, java.lang.Short.TYPE, Integer.TYPE -> ::iload
            java.lang.Long.TYPE -> ::lload
            java.lang.Float.TYPE -> ::fload
            java.lang.Double.TYPE -> ::dload
            else -> ::aload
        }
    }

    private fun InstructionAssembly.getReturnInsn(type: Class<*>): () -> Unit {
        return when (type) {
            java.lang.Boolean.TYPE, java.lang.Byte.TYPE, Character.TYPE, java.lang.Short.TYPE, Integer.TYPE -> ::ireturn
            java.lang.Long.TYPE -> ::lreturn
            java.lang.Float.TYPE -> ::freturn
            java.lang.Double.TYPE -> ::dreturn
            else -> ::areturn
        }
    }

    private fun InstructionAssembly.boxIfNecessary(type: Class<*>) {
        when (type) {
            java.lang.Boolean.TYPE -> invokestatic<Boolean>("valueOf", Boolean::class.java, boolean)
            java.lang.Byte.TYPE -> invokestatic<Byte>("valueOf", Byte::class.java, boolean)
            Character.TYPE -> invokestatic<Char>("valueOf", Character::class.java, char)
            java.lang.Short.TYPE -> invokestatic<Short>("valueOf", Short::class.java, short)
            Integer.TYPE -> invokestatic<Int>("valueOf", Integer::class.java, int)
            java.lang.Long.TYPE -> invokestatic<Long>("valueOf", Long::class.java, long)
            java.lang.Float.TYPE -> invokestatic<Float>("valueOf", Float::class.java, float)
            java.lang.Double.TYPE -> invokestatic<Double>("valueOf", Double::class.java, double)
        }
    }

    private class OpenClassLoader : URLClassLoader(emptyArray()) {
        fun load(node: ClassNode): Class<*> {
            val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            node.accept(writer)
            val bytes = writer.toByteArray()

            if (CTJS.isDevelopment) {
                val dir = File(CTJS.configLocation, "ChatTriggers/extended-classes")
                dir.mkdirs()
                File(dir, node.name.substringAfterLast('/') + ".class").writeBytes(bytes)
            }

            // TODO: Why does CL.defineClass() causes ctor lookup to produce NoSuchMethodException?
            return MethodHandles.lookup().defineClass(bytes)
        }
    }

    companion object {
        private const val GENERATED_PACKAGE = "com/chattriggers/ctjs/internal/engine"
        private var counter = 0

        @JvmStatic
        fun extend(obj: NativeObject): Any {
            val superClass = obj.getOrDefault("superClass", Any::class.java)
            require(superClass is Class<*>) { "If \"superClass\" is provided, it must be a java.lang.Class object" }

            val interfaces = obj.getOrDefault("interfaces", listOf<Class<*>>())
            require(interfaces is List<*> && interfaces.all { it is Class<*> }) {
                "If \"interfaces\" are provided, it must be a java.lang.List of java.lang.Class objects"
            }

            val instanceImpl = obj["instance"]
            require(instanceImpl == null || instanceImpl is NativeObject) {
                "The \"instance\" object provided must be a plain object with properties and methods"
            }

            val staticImpl = obj["static"]
            require(staticImpl == null || staticImpl is NativeObject) {
                "The \"static\" object provided must be a plain object with properties and methods"
            }

            val name = obj.getOrDefault("name", "ExtendedClass$counter")
            require(name is String) {
                "If \"name\" is provided, it must be a string"
            }

            val className = "${GENERATED_PACKAGE.replace('/', '.')}/$name"
            try {
                Class.forName(className)
                throw IllegalArgumentException("Class \"$className\" already exists")
            } catch (_: ClassNotFoundException) {
            }

            @Suppress("UNCHECKED_CAST")
            return extend(
                name,
                superClass,
                interfaces as List<Class<*>>,
                instanceImpl as NativeObject?,
                staticImpl as NativeObject?
            )
        }

        @JvmStatic
        private fun extend(
            name: String,
            superClass: Class<*>,
            interfaces: List<Class<*>>,
            instanceImpl: NativeObject?,
            staticImpl: NativeObject?
        ): Any {
            require(!superClass.isInterface && canInherit(superClass)) {
                "extend()'s superClass argument must be a public, non-final, non-interface class"
            }

            require(interfaces.all { it.isInterface && canInherit(it) }) {
                "extend()'s interfaces arguments must be public, non-final, interface classes"
            }

            val extender = ClassExtender(name, superClass, interfaces, instanceImpl, staticImpl)
            return extender.generate()
        }

        private fun canInherit(clazz: Class<*>): Boolean {
            val modifiers = clazz.modifiers
            return Modifier.isPublic(modifiers) && !Modifier.isFinal(modifiers)
        }
    }
}

/*

 */
