package com.chattriggers.ctjs.internal.engine

import codes.som.koffee.assembleClass
import codes.som.koffee.insns.InstructionAssembly
import codes.som.koffee.insns.jvm.*
import codes.som.koffee.insns.sugar.*
import codes.som.koffee.labels.LabelLike
import codes.som.koffee.modifiers.public
import codes.som.koffee.types.*
import com.chattriggers.ctjs.CTJS
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.IdFunctionCall
import org.mozilla.javascript.IdFunctionObject
import org.mozilla.javascript.IdScriptableObject
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptRuntime
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Wrapper
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.TryCatchBlockNode
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier
import java.net.URLClassLoader

interface MockInterface {
    fun sum(a: Int, b: Int): Int
}

/*
extend(null, [MockInterface], {
    sum(a, b) {
        return a + b;
    }
});
 */

/*
public class Object_backingObj_0 implements MockInterface {
    Object_wrapperObj_1 jsWrapper;

    int sum(int a, int b) {
        Scriptable scope = Context.getScope();
        Object result = jsWrapper.js_sum(Context.getContext(), scope, new Object[]{
            Context.javaToJS(a, scope), Context.javaToJS(b, scope)
        });
        return (int) Context.jsToJava(int.class, result);
    }
}

public class Object_wrapperObj_1 extends IdScriptableObject implements Wrapper {
    private static final Object TAG = "Object_wrapperObj_1";
    private static Scriptable impl = null;

    private final Object_backingObj_0 backingObject = null;
    private boolean isConstructor;

    // Not actually generated
    private static final Id_constructor = 1;
    private static final Id_sum = 2;

    Object_wrapperObj_1(Scriptable impl, boolean __ctorMarker) {
        if (impl != null) {
            throw new IllegalStateException("Duplicate construction of Scriptable class object");
        }

        Object_wrapperObj_1.impl = impl;
        this.isConstructor = true;
    }

    Object_wrapperObj_1(Object_backingObj_0 backingObject) {
        if (impl == null) {
            throw new IllegalStateException("Construction of Scriptable class instance before constructor");
        }

        this.backingObject = backingObject;
        backingObject.jsWrapper = this;
        this.isConstructor = false;
    }

    @Override
    public Object_backingObj_0 unwrap() {
        return backingObject;
    }

    @Override
    public Object execIdCall(IdFunctionObject f, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        if (!f.hasTag(TAG))
            return super.execIdCall(f, cx, scope, thisObj, args);

        switch (f.methodId()) {
            case Id_constructor:
                return js_construct(cx, scope, thisObj, args);
            case Id_sum:
                return realThis(thisObj, f).js_sum(cx, scope, args);
        }

        throw new IllegalArgumentException("Object_wrapperObj_1 has no method: " + f.getFunctionName());
    }

    public Object js_sum(Context cx, Scriptable scope, Object[] args) {
        Callable func = (Callable) impl.get("sum");
        return func.call(cx, scope, this, args);
    }

    @Override
    protected void initPrototypeId(int id) {
        String s, fnName = null;
        int arity;

        switch (id) {
            case Id_constructor:
                arity = 0;
                s = "constructor";
                break;
            case Id_sum:
                arity = 2;
                s = "sum";
                break;
            default:
                throw new IllegalArgumentException(String.valueOf(id));
        }

        initPrototypeMethod(TAG, id, s, null, arity);
    }

    @Override int findPrototypeId(String s) {
        switch (s) {
            case "constructor": return Id_constructor;
            case "sum": return Id_sum;
        }
        return 0;
    }

    private static Object_wrapperObj_1 realThis(Scriptable thisObj, IdFunctionObject f) {
        if (thisObj == null) {
            throw incompatibleCallError(f);
        }

        try {
            Object_wrapperObj_1 nm = (Object_wrapperObj_1) ScriptRuntime.unwrapProxy(thisObj)
            return nm;
        } catch (ClassCastException cce) {
            throw incompatibleCallError(f);
        }
    }
}
 */

class Foo {
    constructor(a: String, b: Int) {

    }
}

class ClassExtender private constructor(
    private val superClass: Class<*>,
    private val interfaces: List<Class<*>>,
    private val impl: NativeObject,
) {
    // Use separate class loaders for each instance so that they can be eagerly garbage collected
    private var cl = OpenClassLoader()

    private val backingName = "${superClass.simpleName}_backingObj_${counter++}"
    private val backingType = "com/chattriggers/ctjs/internal/engine/${backingName}"

    private val wrapperName = "${superClass.simpleName}_wrapperObj_${counter++}"
    private val wrapperType = "com/chattriggers/ctjs/internal/engine/$wrapperName"

    private val properties = mutableMapOf<String, Any?>()
    private val methods = mutableMapOf<String, Callable>()
    private val ids = mutableMapOf<String, Int>()
    private val minId: Int
    private val maxId: Int

    init {
        var nextId = 1
        for (id in impl.ids) {
            if (id !is String)
                continue

            val value = impl[id]
            if (value is Callable) {
                methods[id] = value
            } else {
                properties[id] = value
            }

            ids[id] = nextId++
        }

        minId = -1 // TODO: Static methods
        maxId = nextId - 1
    }

    fun generate(): Any {
        generateBackingClass()

        return generateWrapperClass()
            .getConstructor(NativeObject::class.java, Boolean::class.java)
            .newInstance(impl, true)
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
            field(private + static, "impl", NativeObject::class)

            field(private + final, "backingObject", Any::class)
            field(private, "isConstructor", boolean)

            method(public, "<init>", void, NativeObject::class, boolean) {
                getstatic(wrapperType, "impl", NativeObject::class)
                ifStatement(JumpCondition.NonNull) {
                    construct<IllegalStateException>(String::class) {
                        ldc("Duplicate construction of Scriptable class object $wrapperName")
                    }
                    athrow
                }

                aload_0
                invokespecial<IdScriptableObject>("<init>", void)

                aload_1
                putstatic(wrapperType, "impl", NativeObject::class)

                aload_0
                ldc(true)
                putfield(wrapperType, "isConstructor", boolean)

                _return
            }

            method(public, "<init>", void, backingType) {
                getstatic(wrapperType, "impl", NativeObject::class)
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
                invokevirtual<IdFunctionObject>("hasTag", boolean, String::class)
                ifStatement(JumpCondition.False) {
                    aload_0
                    aload_1
                    aload_2
                    aload_3
                    aload(4)
                    aload(5)
                    invokevirtual<IdFunctionObject>(
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

                val methodLabels = Array(maxId - minId + 1) { makeLabel() }

                aload_1
                invokevirtual<IdFunctionObject>("methodId", int)
                tableswitch(minId, maxId, methodLabels[-minId], *methodLabels)

                for (name in methods.keys) {
                    placeLabel(methodLabels[ids[name]!! - minId])

                    aload(4)
                    aload_1
                    invokestatic(wrapperType, "realThis", wrapperType, Scriptable::class, IdFunctionObject::class)

                    aload_2
                    aload_3
                    aload(5)
                    invokevirtual(wrapperType, "js_$name", Any::class, Context::class, Scriptable::class, Array<Any>::class)
                    areturn
                }

                placeLabel(methodLabels[0]) // TODO: Constructor/static methods
                placeLabel(methodLabels[-minId])
                construct<IllegalArgumentException>(String::class) {
                    construct<StringBuilder>()
                    ldc("$wrapperName has no method: ")
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

                val methodLabels = Array(maxId - minId + 1) { makeLabel() }

                aload_0
                getstatic(wrapperType, "TAG", String::class)
                iload_1

                iload_1
                tableswitch(minId, maxId, methodLabels[-minId], *methodLabels)

                for (name in methods.keys) {
                    val id = ids[name]!!
                    placeLabel(methodLabels[id - minId])

                    // TODO: Arity
                    ldc(name)
                    aconst_null
                    push_int(0)
                    goto(end)
                }

                placeLabel(methodLabels[0]) // TODO: Constructor/static methods
                placeLabel(methodLabels[-minId])
                construct<IllegalArgumentException>(String::class) {
                    iload_1
                    invokestatic<String>("valueOf", String::class, int)
                }
                athrow

                placeLabel(end)
                invokevirtual(wrapperType, "initPrototypeMethod", IdFunctionObject::class, Any::class, int, String::class, String::class, int)
                pop
                _return
            }
        }

        return cl.load(node)
    }

    private fun generateBackingClass(): Class<*> {
        val node = assembleClass(
            public,
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
        private var counter = 0

        @JvmStatic
        fun extend(superClassArg: Class<*>?, interfaces: List<Class<*>>, impl: NativeObject): Any {
            val superClass = superClassArg ?: Any::class.java
            require(!superClass.isInterface && canInherit(superClass)) {
                "extend()'s superClass argument must be a public, non-final, non-interface class"
            }

            require(interfaces.all { it.isInterface && canInherit(it) }) {
                "extend()'s interfaces arguments must be public, non-final, interface classes"
            }

            val extender = ClassExtender(superClass, interfaces, impl)
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
