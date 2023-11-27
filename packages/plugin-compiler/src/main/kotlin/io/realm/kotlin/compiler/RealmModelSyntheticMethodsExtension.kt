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

package io.realm.kotlin.compiler

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.types.SimpleType

/**
 * Triggers generation of synthetic methods on Realm model classes, in particular
 * `toString()`, `equals()` and `hashCode()`.
 */
@Suppress("ComplexCondition")
class RealmModelSyntheticMethodsExtension : SyntheticResolveExtension {

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        if (thisDescriptor.isRealmObject &&
            !thisDescriptor.isCompanionObject && /* Do not override companion object methods */
            !thisDescriptor.isInner && /* Do not override inner class methods */
            !isNestedInRealmModelClass(thisDescriptor) && /* do not override nested class methods */
            result.isEmpty() /* = no method has been declared in the current class */
        ) {
            when (name) {
                Names.REALM_OBJECT_TO_STRING_METHOD -> {
                    result.add(
                        createMethod(
                            classDescriptor = thisDescriptor,
                            methodName = name,
                            arguments = emptyList(),
                            returnType = thisDescriptor.builtIns.stringType
                        )
                    )
                }
                Names.REALM_OBJECT_EQUALS -> {
                    result.add(
                        createMethod(
                            classDescriptor = thisDescriptor,
                            methodName = name,
                            arguments = listOf(Pair("other", thisDescriptor.builtIns.nullableAnyType)),
                            returnType = thisDescriptor.builtIns.booleanType
                        )
                    )
                }
                Names.REALM_OBJECT_HASH_CODE -> {
                    result.add(
                        createMethod(
                            classDescriptor = thisDescriptor,
                            methodName = name,
                            arguments = emptyList(),
                            returnType = thisDescriptor.builtIns.intType
                        )
                    )
                }
            }
        }
    }

    private fun createMethod(
        classDescriptor: ClassDescriptor,
        methodName: Name,
        arguments: List<Pair<String, SimpleType>>,
        returnType: SimpleType
    ): SimpleFunctionDescriptor {
        return SimpleFunctionDescriptorImpl.create(
            classDescriptor,
            Annotations.EMPTY,
            methodName,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            classDescriptor.source
        ).apply {
            initialize(
                null,
                classDescriptor.thisAsReceiverParameter,
                emptyList(),
                emptyList(),
                arguments.map { (argumentName, argumentType) ->
                    ValueParameterDescriptorImpl(
                        containingDeclaration = this,
                        original = null,
                        index = 0,
                        annotations = Annotations.EMPTY,
                        name = Name.identifier(argumentName),
                        outType = argumentType,
                        declaresDefaultValue = false,
                        isCrossinline = false,
                        isNoinline = false,
                        varargElementType = null,
                        source = this.source
                    )
                },
                returnType,
                Modality.OPEN,
                DescriptorVisibilities.PUBLIC
            )
        }
    }

    private fun isNestedInRealmModelClass(classDescriptor: ClassDescriptor): Boolean {
        return classDescriptor.parents.firstOrNull {
            return if (it is ClassDescriptor) {
                it.isRealmObject
            } else {
                false
            }
        } != null
    }
}
