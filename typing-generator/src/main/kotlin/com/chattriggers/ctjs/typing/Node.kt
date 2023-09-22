package com.chattriggers.ctjs.typing

import java.util.*

// A very simple TypeScript declaration file (.d.ts) AST
sealed interface Node {
    val parent: Node?
    val name: String
    val docString: String?

    class Namespace(
        override val parent: Node?,
        override val docString: String?,
        override val name: String,
        val children: MutableSet<Node> = mutableSetOf(),
    ) : Node {
        override fun hashCode(): Int {
            return Objects.hash(parent, name)
        }

        override fun equals(other: Any?): Boolean {
            return other is Namespace && parent == other.parent && name == other.name
        }
    }

    class Class(
        override val parent: Node?,
        override val name: String,
        override val docString: String?,
        val isAbstract: Boolean,
        val superClass: Type?,
        val typeParameters: List<String>,
        val children: List<Node>,
    ) : Node {
        override fun hashCode(): Int {
            return Objects.hash(parent, name)
        }

        override fun equals(other: Any?): Boolean {
            return other is Class && parent == other.parent && name == other.name
        }
    }

    class Type(
        override val name: String,
        val typeArguments: List<Type>,
        val isMarkedNullable: Boolean,
    ) : Node {
        override val parent: Node? = null
        override val docString: String? = null

        fun toString(isFromJava: Boolean): String = buildString {
            append(name)
            if (typeArguments.isNotEmpty()) {
                append('<')
                append(typeArguments.joinToString { it.toString(isFromJava) })
                append('>')
            }
            if (isMarkedNullable) {
                append(" | null")

                // If this type originates from Java (i.e. a property or a function return type), then
                // it can only be the object or null. But if this it originates from JS, it can be either
                // undefined or null, since Rhino will convert undefined to null
                if (!isFromJava)
                    append(" | undefined")
            }
        }

        companion object {
            val UNKNOWN = Type("unknown", emptyList(), false)
            val VOID = Type("void", emptyList(), false)
        }
    }

    class Property(
        override val parent: Node?,
        override val name: String,
        override val docString: String?,
        val isStatic: Boolean,
        val type: Type,
    ) : Node

    class Function(
        override val parent: Node?,
        override val name: String,
        override val docString: String?,
        val isStatic: Boolean,
        val typeParameters: List<String>,
        val parameters: List<Parameter>,
        val returnType: Type?, // null if this is a constructor
    ) : Node {
        data class Parameter(val name: String, val type: Type, val isVararg: Boolean)
    }
}
