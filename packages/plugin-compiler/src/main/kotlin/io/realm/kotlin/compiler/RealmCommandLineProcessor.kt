/*
 * Copyright 2023 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

const val FEATURE_LIST_PATH_KEY = "featureListPath"
val featureListPathConfigurationKey: CompilerConfigurationKey<String> = CompilerConfigurationKey<String>("io.realm.kotlin.featureListPath")

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CommandLineProcessor::class)
class RealmCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = "io.realm.kotlin"
    override val pluginOptions: Collection<CliOption> = listOf(
        CliOption(
            optionName = "featureListPath",
            description = "Feature List Path",
            valueDescription = "Feature List Path",
            required = false,
            allowMultipleOccurrences = false
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration
    ) {
        when (option.optionName) {
            FEATURE_LIST_PATH_KEY ->
                configuration.put(featureListPathConfigurationKey, value)
            else -> super.processOption(option, value, configuration)
        }
    }
}
