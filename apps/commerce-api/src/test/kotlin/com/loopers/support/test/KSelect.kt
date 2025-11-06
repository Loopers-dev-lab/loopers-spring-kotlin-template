package com.loopers.support.test

import org.instancio.Select
import org.instancio.TargetSelector
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

object KSelect {
    fun <T, V> field(property: KProperty1<T, V>): TargetSelector {
        val javaField = property.javaField
            ?: throw IllegalArgumentException("Cannot resolve Java field for property: ${property.name}")
        return Select.field(javaField.declaringClass, javaField.name)
    }
}
