package io.realm.compiler

import io.realm.compiler.Names.DEFAULT_COMPANION
import io.realm.compiler.Names.SCHEMA_METHOD
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.synthetics.SyntheticClassOrObjectDescriptor
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension
import org.jetbrains.kotlin.resolve.lazy.LazyClassContext
import org.jetbrains.kotlin.resolve.lazy.declarations.ClassMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.declarations.PackageMemberDeclarationProvider
import org.jetbrains.kotlin.resolve.lazy.descriptors.LazyClassDescriptor
import org.jetbrains.kotlin.types.KotlinType

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

//    override fun getSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name> {
//        logger("Nested: ${thisDescriptor.name}")
//        return if (!thisDescriptor.isCompanionObject && thisDescriptor.name != Names.MEDIATOR) listOf(Names.MEDIATOR) else emptyList()
//    }

    override fun addSyntheticSupertypes(thisDescriptor: ClassDescriptor, supertypes: MutableList<KotlinType>) {
        logger("Supertypes: ${thisDescriptor.name}")
        super.addSyntheticSupertypes(thisDescriptor, supertypes)
    }

    override fun getPossibleSyntheticNestedClassNames(thisDescriptor: ClassDescriptor): List<Name>? {
        logger("Possible nested: ${thisDescriptor.name}")
        return super.getPossibleSyntheticNestedClassNames(thisDescriptor)
    }

    override fun generateSyntheticMethods(thisDescriptor: ClassDescriptor, name: Name, bindingContext: BindingContext, fromSupertypes: List<SimpleFunctionDescriptor>, result: MutableCollection<SimpleFunctionDescriptor>) {
        logger("Method: ${thisDescriptor.name}, $name")
        if (name != SCHEMA_METHOD) return
        if (thisDescriptor.isRealmObjectCompanion) {
            val classDescriptor = thisDescriptor.containingDeclaration as ClassDescriptor
            result.add(createRealmObjectCompanionSchemaGetterDescriptor(thisDescriptor, classDescriptor))
        }
    }

//    override fun generateSyntheticClasses(thisDescriptor: ClassDescriptor, name: Name, ctx: LazyClassContext, declarationProvider: ClassMemberDeclarationProvider, result: MutableSet<ClassDescriptor>) {
//        logger("Class: ${thisDescriptor.name}, $name")
//        if (name == Names.MEDIATOR && result.none { it.name == Names.MEDIATOR}) {
//            result.add(addMediatorDescriptor(thisDescriptor, declarationProvider, ctx))
//        } else {
//            super.generateSyntheticClasses(thisDescriptor, name, ctx, declarationProvider, result)
//        }
//    }

    override fun generateSyntheticClasses(thisDescriptor: PackageFragmentDescriptor, name: Name, ctx: LazyClassContext, declarationProvider: PackageMemberDeclarationProvider, result: MutableSet<ClassDescriptor>) {
        logger("Package: ${thisDescriptor.name}, $name")

        // FIXME When and how to add package level class
        result.add(addMediatorDescriptor(thisDescriptor, declarationProvider, ctx))
        
        super.generateSyntheticClasses(thisDescriptor, name, ctx, declarationProvider, result)
    }

    // Used for the nested classes
//    fun addMediatorDescriptor(
//            interfaceDesc: ClassDescriptor,
//            declarationProvider: ClassMemberDeclarationProvider,
//            ctx: LazyClassContext
//    ): ClassDescriptor {
//        val interfaceDecl = declarationProvider.correspondingClassOrObject!!
//        val declarationScopeProvider = ctx.declarationScopeProvider
//        val ownerInfo = declarationProvider.ownerInfo
//        val scopeAnchor = ownerInfo!!.scopeAnchor
//        val scope = declarationScopeProvider.getResolutionScopeForDeclaration(scopeAnchor)
//
//        val props = interfaceDecl.primaryConstructorParameters
//        // if there are some properties, there will be a public synthetic constructor at the codegen phase
//        val primaryCtorVisibility = if (props.isEmpty()) DescriptorVisibilities.PUBLIC else DescriptorVisibilities.PRIVATE
//
//        val descriptor = SyntheticClassOrObjectDescriptor(
//                ctx,
//                interfaceDecl,
//                interfaceDesc,
//                Names.MEDIATOR,
//                SourceElement.NO_SOURCE,// interfaceDesc.source,
//                scope,
//                Modality.FINAL,
//                DescriptorVisibilities.PUBLIC,
//                Annotations.EMPTY,
//                primaryCtorVisibility,
//                ClassKind.CLASS,
//                false
//        )
//        descriptor.syntheticDeclaration
//        descriptor.initialize()
//        return descriptor
//    }

    fun addMediatorDescriptor(
            interfaceDesc: PackageFragmentDescriptor,
            declarationProvider: PackageMemberDeclarationProvider,
            ctx: LazyClassContext
    ): ClassDescriptor {
        // FIXME Generate descriptor for package level class

//        declarationProvider
//        val interfaceDecl = declarationProvider.correspondingClassOrObject!!
//        val scope = ctx.declarationScopeProvider.getResolutionScopeForDeclaration(declarationProvider.ownerInfo!!.scopeAnchor)
//
//        val createEmptyExternalPackageFragment = IrExternalPackageFragmentImpl.createEmptyExternalPackageFragment(ctx.moduleDescriptor, FqName("io.realm.test"))
//
//        LazyClassDescriptor(
//                ctx,
//                interfaceDesc,
//                Name.identifier("IMPL"),
//        )
//        interfaceDesc.module
//        val descriptor = SyntheticClassOrObjectDescriptor(
//                ctx,
//                createEmptyExternalPackageFragment,
////                interfaceDesc.module,
////                interfaceDesc.containingDeclaration,
////                interfaceDecl,
//                interfaceDesc,
//                Name.identifier("IMPL"),
//                interfaceDesc.source,
//                LexicalScopeImpl
//                interfaceDesc.getMemberScope(),
//                Modality.FINAL,
//                DescriptorVisibilities.PUBLIC,
//                Annotations.EMPTY,
//                DescriptorVisibilities.PUBLIC,
//                ClassKind.CLASS,
//                false
//        )
//        descriptor.initialize()
        return null!!
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
