package io.realm.compiler

import io.realm.compiler.Names.DEFAULT_COMPANION
import io.realm.compiler.Names.SCHEMA_METHOD
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.PackageMemberDeclarationProvider

class RealmModelSyntheticCompanionExtension : SyntheticResolveExtension {

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name?  = DEFAULT_COMPANION

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        logger("Functions: ${thisDescriptor.name}")

        return if (thisDescriptor.isRealmObjectCompanion) {
            listOf(SCHEMA_METHOD)
        } else {
            emptyList()
        }
    }

    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> {
        logger("Nested: ${thisDescriptor.name}")
        return if (!thisDescriptor.isCompanionObject && thisDescriptor.name != Names.MEDIATOR) listOf(Names.MEDIATOR) else emptyList()
    }

    override fun generateSyntheticMethods(thisDescriptor: ClassDescriptor, name: Name, bindingContext: BindingContext, fromSupertypes: List<SimpleFunctionDescriptor>, result: MutableCollection<SimpleFunctionDescriptor>) {
        logger("Method: ${thisDescriptor.name}, $name")
        if (name != SCHEMA_METHOD) return
        if (thisDescriptor.isRealmObjectCompanion) {
            val classDescriptor = thisDescriptor.containingDeclaration as ClassDescriptor
            result.add(createRealmObjectCompanionSchemaGetterDescriptor(thisDescriptor, classDescriptor))
        }
    }

    override fun generateSyntheticClasses(thisDescriptor: ClassDescriptor, name: Name, ctx: LazyClassContext, declarationProvider: ClassMemberDeclarationProvider, result: MutableSet<ClassDescriptor>) {
        logger("Class: ${thisDescriptor.name}, $name")
        if (name == Names.MEDIATOR && result.none { it.name == Names.MEDIATOR}) {
            result.add(addMediatorDescriptor(thisDescriptor, declarationProvider, ctx))
        } else {
            super.generateSyntheticClasses(thisDescriptor, name, ctx, declarationProvider, result)
        }
    }

    override fun generateSyntheticClasses(thisDescriptor: PackageFragmentDescriptor, name: Name, ctx: LazyClassContext, declarationProvider: PackageMemberDeclarationProvider, result: MutableSet<ClassDescriptor>) {
        logger("Package: ${thisDescriptor.name}, $name")
        super.generateSyntheticClasses(thisDescriptor, name, ctx, declarationProvider, result)
    }

    fun addMediatorDescriptor(
            interfaceDesc: ClassDescriptor,
            declarationProvider: ClassMemberDeclarationProvider,
            ctx: LazyClassContext
    ): ClassDescriptor {
        val interfaceDecl = declarationProvider.correspondingClassOrObject!!
        val declarationScopeProvider = ctx.declarationScopeProvider
        val ownerInfo = declarationProvider.ownerInfo
        val scopeAnchor = ownerInfo!!.scopeAnchor
        val scope = declarationScopeProvider.getResolutionScopeForDeclaration(scopeAnchor)

        val props = interfaceDecl.primaryConstructorParameters
        // if there are some properties, there will be a public synthetic constructor at the codegen phase
        val primaryCtorVisibility = if (props.isEmpty()) DescriptorVisibilities.PUBLIC else DescriptorVisibilities.PRIVATE

        val descriptor = SyntheticClassOrObjectDescriptor(
                ctx,
                interfaceDecl,
                interfaceDesc,
                Names.MEDIATOR,
                interfaceDesc.source,
                scope,
                Modality.FINAL,
                DescriptorVisibilities.PUBLIC,
                Annotations.EMPTY,
                primaryCtorVisibility,
                ClassKind.CLASS,
                false
        )
        descriptor.initialize()
        return descriptor
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
            initialize(null,
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
