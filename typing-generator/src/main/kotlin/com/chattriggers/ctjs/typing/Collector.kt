package com.chattriggers.ctjs.typing

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import java.util.*

@OptIn(KspExperimental::class)
class Collector(
    private val resolver: Resolver,
    @Suppress("unused") private val logger: KSPLogger,
) {
    private val loadedClasses = mutableSetOf<String>()
    private val packages = mutableMapOf<String, Node.Namespace>()
    private val topLevelPackages = mutableListOf<Node.Namespace>()

    private val internalPackages = listOf(
        "com.chattriggers.ctjs.internal",
        "java.awt",
        "org.mozilla.javascript"
    )

    fun collect(): Collection<Node> {
        val roots = resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .toMutableList()

        globals.values
            .filterNotNull()
            .map {
                resolver.getClassDeclarationByName(resolver.getKSNameFromString(it))
                    ?: error("Unknown class \"$it\"")
            }
            .let(roots::addAll)

        roots.forEach { collectClass(it, 0) }

        return topLevelPackages
    }

    private fun collectClass(decl: KSClassDeclaration, depth: Int): Boolean {
        require(decl.classKind != ClassKind.ENUM_ENTRY || decl.classKind == ClassKind.ANNOTATION_CLASS)

        if (decl.hasInvalidName())
            return false

        val packageName = decl.packageName.asString()
        if (!decl.isPublic() || internalPackages.any(packageName::startsWith))
            return false

        val qualifiedName = decl.qualifiedName!!.asString()
        if (qualifiedName in loadedClasses)
            return true

        if (depth >= MAX_DEPTH)
            return false

        loadedClasses.add(qualifiedName)

        // Get the package of the declaration. The package of the class (decl.packageName) is the
        // Java package, so it will not contain the name of the outer class if decl corresponds to
        // an inner class. In this case, we need to place this declaration in a namespace with the
        // name of the outer class (TypeScript allows "class Foo {}; namespace Foo {};" and will
        // "merge" them)
        val namespaceName = decl.packageName.asString() +
            generateSequence(decl.parentDeclaration, KSDeclaration::parentDeclaration)
                .toList()
                .asReversed()
                .fold("") { prev, curr -> prev + ".${curr.simpleName.asString()}" }

        val pkg = getPackage(namespaceName)
        val children = mutableListOf<Node>()
        val isEnum = decl.classKind == ClassKind.ENUM_CLASS

        val superClass = if (!isEnum) {
            decl.superTypes.firstOrNull {
                val superDecl = it.resolve().declaration
                superDecl is KSClassDeclaration &&
                    superDecl.classKind == ClassKind.CLASS &&
                    superDecl.qualifiedName!!.asString() != "kotlin.Any"
            }?.let { ref ->
                collectType(ref.resolve(), depth + 1).takeIf { it != Node.Type.UNKNOWN }
            }
        } else null

        val node = Node.Class(
            pkg,
            decl.simpleName.asString(),
            decl.docString,
            decl.isAbstract() || decl.classKind == ClassKind.INTERFACE,
            superClass,
            decl.typeParameters.map { it.name.asString() },
            children,
        )

        pkg.children.add(node)

        // We process properties and functions separately so we can do some
        // postprocessing once we've collected them all
        val properties = mutableListOf<Node.Property>()
        val functions = mutableListOf<Node.Function>()
        var companionClass: KSClassDeclaration? = null

        // Process enum entries
        if (isEnum) {
            decl.declarations.filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .forEach {
                    properties.add(
                        Node.Property(
                            node,
                            it.simpleName.asString(),
                            it.docString,
                            true,
                            Node.Type(qualifiedName, emptyList(), false),
                        )
                    )
                }
        } else {
            // Collect all subclasses. Don't increase depth; if we load a class, we want
            // to always load all the subclasses
            decl.declarations.filterIsInstance<KSClassDeclaration>().forEach {
                if (it.simpleName.asString() == "Companion") {
                    companionClass = it
                } else collectClass(it, depth)
            }
        }

        for (property in decl.getAllProperties()) {
            collectProperty(node, property, depth)?.forEach {
                when (it) {
                    is Node.Property -> properties.add(it)
                    is Node.Function -> functions.add(it)
                    else -> throw IllegalStateException()
                }
            }
        }

        decl.getAllFunctions()
            .mapNotNull { collectFunction(node, it, depth) }
            .forEach(functions::add)

        // Add all static functions
        companionClass?.getAllFunctions()
            ?.filter { it.isAnnotationPresent(JvmStatic::class) }
            ?.mapNotNull { collectFunction(node, it, depth) }
            ?.forEach(functions::add)

        // Java allows properties and functions to have the same name, but JS does
        // not. We prefer the function here
        properties.removeIf { prop -> functions.any { it.name == prop.name } }

        children.addAll(properties)

        // Ensure functions are ordered nicely
        val comparator = Comparator.comparing { f: Node.Function -> !f.isStatic }
            .thenComparing { f: Node.Function -> f.name != "constructor" }
            .thenComparing(Node.Function::name)

        children.addAll(functions.sortedWith(comparator))

        return true
    }

    private fun collectProperty(parent: Node, property: KSPropertyDeclaration, depth: Int): List<Node>? {
        if (!property.isPublic() || property.findOverridee() != null)
            return null

        val isStatic = property.isAnnotationPresent(JvmStatic::class) ||
            (property.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.OBJECT

        if (property.isAnnotationPresent(JvmField::class) || (!property.hasBackingField && (property.getter == null && property.setter == null))) {
            return listOf(Node.Property(
                parent,
                property.simpleName.asString(),
                property.docString,
                isStatic,
                collectType(property.type.resolve(), depth + 1),
            ))
        }

        val accessors = mutableListOf<Node.Function>()

        property.getter?.let { getter ->
            if (Modifier.PUBLIC !in getter.modifiers)
                return@let

            accessors.add(Node.Function(
                parent,
                resolver.getJvmName(getter) ?: return@let,
                property.docString,
                isStatic,
                emptyList(),
                emptyList(),
                collectType(getter.returnType!!.resolve(), depth + 1),
            ))
        }

        property.setter?.let { setter ->
            if (Modifier.PUBLIC !in setter.modifiers)
                return@let

            accessors.add(Node.Function(
                parent,
                resolver.getJvmName(setter) ?: return@let,
                null,
                isStatic,
                emptyList(),
                listOf(collectFunctionParameter(setter.parameter, depth)),
                Node.Type.VOID
            ))
        }

        return accessors
    }

    private fun collectFunction(parent: Node, function: KSFunctionDeclaration, depth: Int): Node.Function? {
        if (
            !function.isPublic() ||
            function.findOverridee() != null ||
            function.simpleName.asString() in excludedMethods
        )
            return null

        val typeParameters = if (!function.isConstructor()) {
            function.typeParameters.map { it.name.asString() }
        } else emptyList()

        val returnType = if (!function.isConstructor()) {
            function.returnType?.resolve()?.let {
                collectType(it, depth + 1)
            } ?: Node.Type.UNKNOWN
        } else null

        return Node.Function(
            parent,
            if (function.isConstructor()) "constructor" else function.simpleName.asString(),
            function.docString,
            function.isAnnotationPresent(JvmStatic::class),
            typeParameters,
            function.parameters.map { collectFunctionParameter(it, depth) },
            returnType,
        )
    }

    private fun collectFunctionParameter(parameter: KSValueParameter, depth: Int): Node.Function.Parameter {
        var name = parameter.name!!.asString().let {
            // Setters have really weird parameter names for some reason
            if (it == "<set-?>") "value" else it
        }
        if (name in typescriptReservedWords)
            name += "_"
        var type = collectType(parameter.type.resolve(), depth + 1)
        if (parameter.hasDefault)
            type = Node.Type(type.name, type.typeArguments, true)
        return Node.Function.Parameter(name, type, parameter.isVararg)
    }

    private fun collectType(type: KSType, depth: Int): Node.Type {
        if (type.annotations.any { it.shortName.asString() == "InternalApi" })
            return Node.Type.UNKNOWN

        val (isMarkedNullable, declaration) = type.isMarkedNullable to type.declaration
        if (declaration is KSTypeParameter)
            return Node.Type(declaration.simpleName.asString(), emptyList(), isMarkedNullable)
        if (declaration is KSTypeAlias)
            return collectType(declaration.type.resolve(), depth)

        // Map some primitive/stdlib to JS types
        var (qualifiedName, typeArguments) = declaration.qualifiedName!!.asString() to type.innerArguments.map { it.type!! }
        val isStdType = mapStdType(qualifiedName, typeArguments, depth)?.also {
            qualifiedName = it.first
            typeArguments = it.second
        } != null

        // Collect the class to put it in the list of output nodes
        if (!isStdType && declaration is KSClassDeclaration && !collectClass(declaration, depth))
            return Node.Type.UNKNOWN

        return Node.Type(
            qualifiedName,
            typeArguments.map(KSTypeReference::resolve).map {
                // Guard against recursive types (i.e. those that follow the Enum pattern)
                if (it == type) {
                    Node.Type.UNKNOWN
                } else collectType(it, depth)
            },
            isMarkedNullable,
        )
    }

    private fun mapStdType(
        qualifiedName: String,
        typeArguments: List<KSTypeReference>,
        depth: Int,
    ): Pair<String, List<KSTypeReference>>? {
        if (qualifiedName.startsWith("kotlin.Function"))
            return buildFunction(typeArguments, depth) to emptyList()

        if (qualifiedName.startsWith("kotlin.")) {
            return when (qualifiedName.substringAfter('.')) {
                "Any" -> "any"
                "Nothing" -> "never"
                "Unit" -> "void"
                "Byte",
                "Char",
                "Short",
                "Int",
                "Long",
                "Float",
                "Double",
                "Number" -> "number"
                "Boolean" -> "boolean"
                "String" -> "string"
                "Array",
                "collections.MutableCollection",
                "collections.Collection",
                "collections.MutableList",
                "collections.List" -> "Array"
                "collections.MutableMap",
                "collections.Map" -> "Map"
                "collections.MutableSet",
                "collections.Set" -> "Set"
                "ByteArray",
                "CharArray",
                "ShortArray",
                "IntArray",
                "LongArray",
                "FloatArray",
                "DoubleArray" -> "Array<number>"
                else -> return null
            } to typeArguments
        }

        if (qualifiedName.startsWith("java.util.function.")) {
            return when (qualifiedName.substringAfterLast('.')) {
                "BiConsumer" -> buildFunction(null, typeArguments, depth)
                "BiFunction" -> buildFunction(typeArguments.last(), typeArguments.dropLast(1), depth)
                "BiPredicate" -> buildFunction(resolver.createKSTypeReferenceFromKSType(resolver.builtIns.booleanType), typeArguments, depth)
                "Consumer" -> buildFunction(null, typeArguments, depth)
                "Function" -> buildFunction(typeArguments.getOrNull(1), listOfNotNull(typeArguments.getOrNull(0)), depth)
                "Predicate" -> buildFunction(resolver.createKSTypeReferenceFromKSType(resolver.builtIns.booleanType), typeArguments, depth)
                "Supplier" -> buildFunction(null, typeArguments, depth)
                else -> return null
            } to emptyList()
        }

        return null
    }

    private fun buildFunction(types: List<KSTypeReference>, depth: Int): String {
        require(types.isNotEmpty())
        return buildFunction(types.first(), types.drop(1), depth)
    }

    private fun buildFunction(returnType: KSTypeReference?, parameterTypes: List<KSTypeReference>, depth: Int): String {
        return buildString {
            append('(')
            append(parameterTypes.withIndex().joinToString {
                // This is from Java because we're passing the callable to a Java method,
                // which will invoke it with Java objects
                "p${it.index}: " + collectType(it.value.resolve(), depth).toString(isFromJava = true)
            })
            append(") => ")
            append(returnType?.let { collectType(it.resolve(), depth).toString(isFromJava = false) } ?: "void")
        }
    }

    private fun getPackage(name: String): Node.Namespace = packages.getOrPut(name) {
        val lastSeparator = name.lastIndexOf('.')
        if (lastSeparator == -1) {
            Node.Namespace(null, null, name).also(topLevelPackages::add)
        } else {
            val parent = getPackage(name.substring(0, lastSeparator))
            Node.Namespace(parent, null, name.substring(lastSeparator + 1)).also {
                parent.children.add(it)
            }
        }
    }

    private fun KSDeclaration.hasInvalidName(): Boolean {
        // We can't generate a symbol with the name of a typescript reserved word, so we
        // check here to make sure that isn't the case.
        if (packageName.asString().split('.').any { it in typescriptReservedWords })
            return true

        return simpleName.asString() in typescriptReservedWords
    }

    companion object {
        private const val MAX_DEPTH = 5

        private val excludedMethods = setOf(
            "<clinit>", "equals", "hashCode", "toString", "finalize", "compareTo", "clone",
        )

        // Typescript keywords
        private val typescriptReservedWords = setOf(
            "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else",
            "enum", "export", "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof",
            "new", "null", "return", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void",
            "while", "with",
        )
    }
}
