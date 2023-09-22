package com.chattriggers.ctjs.typing

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile

class Provider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return Processor(environment)
    }
}

class Processor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger
    private val dependentFiles = mutableSetOf<KSFile>()
    private val builder = StringBuilder()
    private var indent = 0

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val topLevelNamespaces = Collector(resolver, logger).collect().map(::createSpan)
        var first = true
        for (ns in topLevelNamespaces) {
            if (!first)
                builder.append('\n')
            first = false
            builder.append('\n')
            appendSpan(ns)
        }

        check(indent == 0)

        return emptyList()
    }

    override fun finish() {
        codeGenerator
            .createNewFileByPath(Dependencies(true, *dependentFiles.toTypedArray()), "ctjs", "d.ts")
            .write(wrapTypings(builder.toString()).toByteArray())
    }

    private fun createSpan(node: Node): Span? {
        return when (node) {
            is Node.Namespace -> {
                val mappedChildren = node.children.mapNotNull(::createSpan)
                if (mappedChildren.isNotEmpty()) {
                    Span("namespace ${node.name} {\n", node.docString, "}", mappedChildren)
                } else null
            }
            is Node.Class -> {
                val prefix = buildString {
                    if (node.isAbstract)
                        append("abstract ")
                    append("class ")
                    append(node.name)
                    if (node.typeParameters.isNotEmpty()) {
                        append('<')
                        append(node.typeParameters.joinToString())
                        append('>')
                    }
                    if (node.superClass != null) {
                        append(" extends ")
                        append(node.superClass.toString(isFromJava = true))
                    }
                    append(" {\n")
                }

                Span(prefix, node.docString, "}", node.children.mapNotNull(::createSpan))
            }
            is Node.Property -> {
                var prefix = "${node.name}: ${node.type.toString(isFromJava = true)};"
                if (node.isStatic)
                    prefix = "static $prefix"
                Span(prefix, node.docString)
            }
            is Node.Function -> {
                val prefix = buildString {
                    if (node.isStatic)
                        append("static ")
                    append(node.name)
                    if (node.typeParameters.isNotEmpty()) {
                        append('<')
                        append(node.typeParameters.joinToString())
                        append('>')
                    }
                    append("(")
                    append(node.parameters.joinToString {
                        val base = "${it.name}: ${it.type.toString(isFromJava = false)}"
                        if (it.isVararg) "...$base[]" else base
                    })
                    append(")")

                    node.returnType?.let {
                        append(": ")
                        append(it.toString(isFromJava = true))
                    }

                    append(';')
                }
                Span(prefix, node.docString)
            }
            is Node.Type -> throw IllegalStateException("unreachable")
        }
    }

    private fun appendSpan(span: Span?) {
        if (span == null)
            return

        if (span.docString != null) {
            val lines = span.docString.lines()
            builder.append("  ".repeat(indent))
            builder.append("/**\n")

            lines.forEach {
                builder.append("  ".repeat(indent))
                builder.append(" * ")
                // There is (sometimes) an extra space at the beginning for some reason
                if (it.getOrNull(0) == ' ')
                    builder.append(it.drop(1))
                else builder.append(it)
                builder.append('\n')
            }

            builder.append("  ".repeat(indent))
            builder.append(" */\n")
        }

        builder.append("  ".repeat(indent))
        builder.append(span.prefix)
        indent += 1

        for (child in span.children) {
            appendSpan(child)
            builder.append('\n')
        }

        indent -= 1
        if (span.suffix != null) {
            builder.append("  ".repeat(indent))
            builder.append(span.suffix)
        }
    }

    private class Span(
        val prefix: String,
        docString: String? = null,
        val suffix: String? = null,
        val children: List<Span> = emptyList(),
    ) {
        val docString = docString?.trim()
    }
}
