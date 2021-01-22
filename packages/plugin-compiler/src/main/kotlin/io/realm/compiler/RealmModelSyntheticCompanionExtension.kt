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

import io.realm.compiler.Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.realm.compiler.Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD
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
import org.jetbrains.kotlin.name.SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.KotlinType

class RealmModelSyntheticCompanionExtension : SyntheticResolveExtension {

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? = DEFAULT_NAME_FOR_COMPANION_OBJECT

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        return when {
            thisDescriptor.isRealmObjectCompanion -> {
                listOf(
                    REALM_OBJECT_COMPANION_SCHEMA_METHOD,
                    REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD
                )
            }
            else -> {
                emptyList()
            }
        }
    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {

        if (thisDescriptor.annotations.hasAnnotation(FqNames.REALM_OBJECT_ANNOTATION)) {
            val defaultType = thisDescriptor.module.resolveClassByFqName(FqNames.REALM_MODEL_INTERFACE_MARKER, NoLookupLocation.FROM_BACKEND)?.defaultType
                ?: error("Couldn't resolve `RealmModel` from ${thisDescriptor.name.identifier}")
            supertypes.add(defaultType)
        }
        super.addSyntheticSupertypes(thisDescriptor, supertypes)
    }

    override fun generateSyntheticMethods(thisDescriptor: ClassDescriptor, name: Name, bindingContext: BindingContext, fromSupertypes: List<SimpleFunctionDescriptor>, result: MutableCollection<SimpleFunctionDescriptor>) {
        when {
            thisDescriptor.isRealmObjectCompanion -> {
                val classDescriptor = thisDescriptor.containingDeclaration as ClassDescriptor

                when (name) {
                    REALM_OBJECT_COMPANION_SCHEMA_METHOD -> result.add(createRealmObjectCompanionSchemaGetterFunctionDescriptor(thisDescriptor, classDescriptor))
                    REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD -> result.add(createRealmObjectCompanionNewInstanceFunctionDescriptor(thisDescriptor, classDescriptor))
                }
            }
        }
    }

    private fun createRealmObjectCompanionSchemaGetterFunctionDescriptor(
        companionClass: ClassDescriptor,
        realmObjectClass: ClassDescriptor
    ): SimpleFunctionDescriptor {

        return SimpleFunctionDescriptorImpl.create(
            companionClass,
            Annotations.EMPTY,
            REALM_OBJECT_COMPANION_SCHEMA_METHOD,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            companionClass.source
        ).apply {
            initialize(
                null,
                companionClass.thisAsReceiverParameter,
                emptyList(),
                emptyList(),
                // FIXME Howto resolve types from "runtime" module. Should be
                //  `io.realm.internal.Table`, but doesn't seem to break as long as the actual
                //  implementation return type can be cast to this return type
                realmObjectClass.builtIns.anyType,
                Modality.OPEN,
                DescriptorVisibilities.PUBLIC
            )
        }
    }

    private fun createRealmObjectCompanionNewInstanceFunctionDescriptor(
        companionClass: ClassDescriptor,
        realmObjectClass: ClassDescriptor
    ): SimpleFunctionDescriptor {

        return SimpleFunctionDescriptorImpl.create(
            companionClass,
            Annotations.EMPTY,
            REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            companionClass.source
        ).apply {
            initialize(
                null,
                companionClass.thisAsReceiverParameter,
                emptyList(),
                emptyList(),
                realmObjectClass.builtIns.anyType,
                Modality.OPEN,
                DescriptorVisibilities.PUBLIC
            )
        }
    }
}
