package com.chattriggers.ctjs.internal.launch.generation

import com.chattriggers.ctjs.internal.launch.Descriptor
import com.chattriggers.ctjs.internal.launch.Mixin
import com.chattriggers.ctjs.internal.utils.Mappings
import org.spongepowered.asm.mixin.transformer.ClassInfo

internal data class GenerationContext(val mixin: Mixin) {
    val mappedClass = Mappings.getMappedClass(mixin.target) ?: error("Unknown class name ${mixin.target}")

    fun findMethod(method: String): Pair<Mappings.MappedMethod, ClassInfo.Method> {
        val descriptor = Descriptor.Parser(method).parseMethod(full = false)
        return Utils.findMethod(mappedClass, descriptor)
    }
}
