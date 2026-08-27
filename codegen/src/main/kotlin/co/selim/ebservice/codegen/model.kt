package co.selim.ebservice.codegen

data class Function(val name: String, val returnType: String, val parameters: Set<Parameter>, val isSuspend: Boolean)
data class Parameter(val name: String, val type: String)
