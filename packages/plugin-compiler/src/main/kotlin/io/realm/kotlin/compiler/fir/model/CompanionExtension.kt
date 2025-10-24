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

package io.realm.kotlin.compiler.fir.model

import io.realm.kotlin.compiler.Names
import io.realm.kotlin.compiler.fir.RealmPluginGeneratorKey
import io.realm.kotlin.compiler.isBaseRealmObject
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.plugin.createDefaultPrivateConstructor
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class CompanionExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {
    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        val isRealmObject = classSymbol.isBaseRealmObject
        return if (isRealmObject) {
            setOf(SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT)
        } else {
            emptySet()
        }
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        // Only generate new companion if class does not have one already
        val companion = (owner as? FirRegularClassSymbol)?.companionObjectSymbol
        return if (companion == null && owner.isBaseRealmObject) {
            createCompanionObject(owner, RealmPluginGeneratorKey).symbol
        } else { null }
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if (classSymbol.isCompanion && (classSymbol.getContainingClassSymbol() as? FirClassSymbol<*>)?.isBaseRealmObject == true) {
            return setOf(
                Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD,
                Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD,
                SpecialNames.INIT, // If from our own plugin remember to generate a default constructor
            )
        }
        return emptySet()
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        return when (callableId.callableName) {
            Names.REALM_OBJECT_COMPANION_SCHEMA_METHOD ->
                listOf(
                    createMemberFunction(
                        owner,
                        RealmPluginGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.anyType.coneType,
                    ).symbol
                )

            Names.REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD ->
                listOf(
                    createMemberFunction(
                        owner,
                        RealmPluginGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.anyType.coneType
                    ).symbol
                )

            else -> emptyList()
        }
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val constructor = createDefaultPrivateConstructor(context.owner, RealmPluginGeneratorKey)
        return listOf(constructor.symbol)
    }
}
