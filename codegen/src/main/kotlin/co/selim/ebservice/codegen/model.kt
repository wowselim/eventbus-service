package co.selim.ebservice.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

data class Service(val name: ClassName, val functions: Set<Function>)
data class Function(val name: String, val returnType: TypeName, val parameters: Set<Parameter>)
data class Parameter(val name: String, val type: TypeName)
