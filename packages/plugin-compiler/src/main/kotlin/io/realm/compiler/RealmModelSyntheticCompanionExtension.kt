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

import io.realm.compiler.Names.DEFAULT_COMPANION
import io.realm.compiler.Names.SCHEMA_METHOD
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType

class RealmModelSyntheticCompanionExtension : SyntheticResolveExtension {

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? = DEFAULT_COMPANION

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        return if (thisDescriptor.isRealmObjectCompanion) {
            listOf(SCHEMA_METHOD)
        } else {
            emptyList()
        }
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        if (thisDescriptor.annotations.hasAnnotation(FqNames.REALM_OBJECT_ANNOTATION)) {
            val defaultType = thisDescriptor.module.resolveClassByFqName(FqNames.REALM_MODEL_INTERFACE_MARKER, NoLookupLocation.FROM_BACKEND)?.defaultType
                ?: throw error("Couldn't resolve `RealmModel` from ${thisDescriptor.name.identifier}")
            supertypes.add(defaultType)
        }
        super.addSyntheticSupertypes(thisDescriptor, supertypes)
    }

    override fun generateSyntheticMethods(thisDescriptor: ClassDescriptor, name: Name, bindingContext: BindingContext, fromSupertypes: List<SimpleFunctionDescriptor>, result: MutableCollection<SimpleFunctionDescriptor>) {
        if (name != SCHEMA_METHOD) return
        if (thisDescriptor.isRealmObjectCompanion) {
            val classDescriptor = thisDescriptor.containingDeclaration as ClassDescriptor
            result.add(createRealmObjectCompanionSchemaGetterDescriptor(thisDescriptor, classDescriptor))
        }
    }

    private fun createRealmObjectCompanionSchemaGetterDescriptor(
        companionClass: ClassDescriptor,
        realmObjectClass: ClassDescriptor
    ): SimpleFunctionDescriptor {

        return SimpleFunctionDescriptorImpl.create(
            companionClass,
            Annotations.EMPTY,
            SCHEMA_METHOD,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            companionClass.source
        ).apply {
            initialize(
                null,
                companionClass.thisAsReceiverParameter,
                emptyList(),
                emptyList(),
                realmObjectClass.builtIns.stringType,
                Modality.FINAL,
                DescriptorVisibilities.PUBLIC
            )
        }
    }
}
