/*
 * Copyright 2020 Realm Inc.
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

package io.realm.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

/**
 * Registrar for the Realm compiler plugin.
 *
 * The overall concepts of the compiler plugin is that it:
 * - Adds [RealmModelInternal] interface to all classes marked with [RealmObject] interface
 * - Rewire accessors to the actual Realm for managed objects
 * - Adds [RealmObjectCompanion] interface to the companion object of classes marked with
 * [RealmObject] interface
 * - Modify [RealmConfiguration] constructor calls to capture the companion objects of supplied
 * schema classes.
 *
 * The [RealmModelInternal] holds internal attributes like Realm and objects native pointer, type
 * information, etc. This information is used to indicate if an object is managed or not and direct
 * the accessors to the Realm if so.
 *
 * The [RealmObjectCompanion] holds static information about the schema (members, primary key, etc.)
 * and utility methods for constructing objects, etc.
 */
@AutoService(ComponentRegistrar::class)
class Registrar : ComponentRegistrar {
    override fun registerProjectComponents(
        project: MockProject,
        configuration: CompilerConfiguration
    ) {
        messageCollector =
            configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        SchemaCollector.properties.clear()

        // Trigger generation of companion objects and addition of the RealmObjectCompanion to it
        SyntheticResolveExtension.registerExtension(
            project,
            RealmModelSyntheticCompanionExtension()
        )

        // Adds RealmModelInternal properties, rewires accessors and adds static companion
        // properties and methods
        IrGenerationExtension.registerExtension(project, RealmModelLoweringExtension())

        // Modifies RealmConfiguration constructor calls to capture the companions objects of the
        // supplied schema
        IrGenerationExtension.registerExtension(project, RealmSchemaLoweringExtension())
    }
}
