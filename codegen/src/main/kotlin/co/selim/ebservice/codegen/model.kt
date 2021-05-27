package co.selim.ebservice.codegen

import com.squareup.kotlinpoet.TypeName

data class Service(val packageName: String, val name: String, val functions: Set<Function>)
data class Function(val name: String, val returnType: TypeName, val parameters: List<Parameter>)
data class Parameter(val name: String, val type: TypeName)
