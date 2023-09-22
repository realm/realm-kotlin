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

package io.realm.kotlin.compiler.k2.model

import io.realm.kotlin.compiler.Names
import io.realm.kotlin.compiler.isBaseRealmObject
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns

/**
 * Fir extension that adds `toString`, `equals` and `hashCode` to RealmObject-classes.
 */
class ModelObjectAugmenter(session: FirSession) : FirDeclarationGenerationExtension(session) {
    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        return if (classSymbol.isBaseRealmObject) {
            setOf(
                Names.REALM_OBJECT_TO_STRING_METHOD,
                Names.REALM_OBJECT_EQUALS,
                Names.REALM_OBJECT_HASH_CODE,
            )
        } else {
            emptySet()
        }
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        return when (callableId.callableName) {
            Names.REALM_OBJECT_TO_STRING_METHOD ->
                listOf(
                    createMemberFunction(
                        owner,
                        RealmApiGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.stringType.type,
                    ).symbol
                )
            Names.REALM_OBJECT_EQUALS ->
                listOf(
                    createMemberFunction(
                        owner,
                        RealmApiGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.booleanType.type,
                    ) {
                        valueParameter(Name.identifier("other"), session.builtinTypes.nullableAnyType.type)
                    }.symbol
                )
            Names.REALM_OBJECT_HASH_CODE ->
                listOf(
                    createMemberFunction(
                        owner,
                        RealmApiGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.intType.type,
                    ).symbol
                )
            else -> emptyList()
        }
    }
}
