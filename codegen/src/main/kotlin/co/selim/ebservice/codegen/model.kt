package co.selim.ebservice.codegen

import com.squareup.kotlinpoet.TypeName

data class Function(val name: String, val returnType: TypeName, val parameters: Set<Parameter>, val isSuspend: Boolean)
data class Parameter(val name: String, val type: TypeName)
