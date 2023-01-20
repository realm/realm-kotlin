package io.realm.kotlin.test.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.realm.kotlin.test.util.Compiler
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
fun createFileAndCompile(fileName: String, code: String): KotlinCompilation.Result =
    Compiler.compileFromSource(SourceFile.kotlin(fileName, code))
