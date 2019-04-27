package io.github.cottonmc.contentgenerator.annotations.processor

import com.google.gson.Gson
import io.github.cottonmc.modhelper.annotations.Initializer
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

internal class CottonAnnotationProcessor : AbstractProcessor() {
    private var processed = false

    override fun process(annotations: MutableSet<out TypeElement>?, roundEnv: RoundEnvironment): Boolean {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "Processing...")
        if (processed) return false

        val initializers = HashMap<String, MutableList<Any>>()

        fun addInitializer(entrypointType: String, initializer: Any) =
            initializers.getOrPut(entrypointType) { ArrayList() }.add(initializer)

        loop@ for (element in roundEnv.getElementsAnnotatedWith(Initializer::class.java)) {
            val reference: String = when (element) {
                is TypeElement -> element.qualifiedName.toString()
                is ExecutableElement -> (element.enclosingElement as TypeElement).qualifiedName.toString() + "::" +
                        element.simpleName
                is VariableElement -> (element.enclosingElement as TypeElement).qualifiedName.toString() + "::" +
                        element.simpleName

                else -> {
                    processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Unknown element type: ${element.kind}")
                    continue@loop
                }
            }

            val annotation = element.getAnnotation(Initializer::class.java)
            var entrypointType = annotation.entrypointType

            if (entrypointType.isEmpty()) {
                if (element !is TypeElement) {
                    processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Missing entrypointType on a non-type element: ${element.simpleName}")
                    continue@loop
                }

                // TODO: Check for fabric-loader initializer interfaces here
            }

            addInitializer(
                entrypointType,
                if (annotation.adapter == "") {
                    reference
                } else mapOf("value" to reference, "adapter" to annotation.adapter)
            )
        }

        val initializerOutput = processingEnv.filer.createResource(
            StandardLocation.SOURCE_OUTPUT,
            "", "build/cotton/initializers.json"
        )

        val gson = Gson()

        initializerOutput.openWriter().use {
            it.write(gson.toJson(initializers))
        }

        processed = true

        return false
    }

    override fun getSupportedAnnotationTypes() = setOf(
        Initializer::class.java.name
    )

    //only support release 8, we do not want to mess with mixins.
    override fun getSupportedSourceVersion() = SourceVersion.RELEASE_8
}