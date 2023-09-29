import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.emeraldpay.dshackle.config.ChainsConfig
import io.emeraldpay.dshackle.config.ChainsConfigReader
import io.emeraldpay.dshackle.foundation.ChainOptionsReader

open class CodeGen(private val config: ChainsConfig) {
    companion object {
        fun generateFromChains(path: File) {
            val chainConfigReader = ChainsConfigReader(ChainOptionsReader())
            val config = chainConfigReader.read(null)
            CodeGen(config).generateChainsFile().writeTo(path)
        }
    }

    private fun addEnumProperties(builder: TypeSpec.Builder): TypeSpec.Builder {
        builder.addEnumConstant(
            "UNSPECIFIED",
            TypeSpec.anonymousClassBuilder()
                .addSuperclassConstructorParameter("%L, %S, %S, %S, %L, %L", 0, "UNSPECIFIED", "Unknown", "0x0", 0, "emptyList()")
                .build(),
        )
        for (chain in config) {
            builder.addEnumConstant(
                chain.blockchain.uppercase().replace('-', '_') + "__" + chain.id.uppercase().replace('-', '_'),
                TypeSpec.anonymousClassBuilder()
                    .addSuperclassConstructorParameter(
                        "%L, %S, %S, %S, %L, %L",
                        chain.grpcId,
                        chain.code,
                        chain.blockchain.replaceFirstChar { it.uppercase() } + " " + chain.id.replaceFirstChar { it.uppercase() },
                        chain.chainId,
                        chain.netVersion,
                        "listOf(" + chain.shortNames.map { "\"${it}\"" }.joinToString() + ")",
                    )
                    .build(),
            )
        }
        return builder
    }

    fun generateChainsFile(): FileSpec {
        val byIdFun = FunSpec.builder("byId")
            .addParameter("id", Int::class)
            .returns(ClassName("", "Chain"))
            .beginControlFlow("for (chain in values())")
            .beginControlFlow("if (chain.id == id)")
            .addStatement("return chain")
            .endControlFlow()
            .endControlFlow()
            .addStatement("return UNSPECIFIED")
            .build()

        val chainType = addEnumProperties(
            TypeSpec.enumBuilder("Chain")
                .addType(TypeSpec.companionObjectBuilder().addFunction(byIdFun).build())
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("id", Int::class)
                        .addParameter("chainCode", String::class)
                        .addParameter("chainName", String::class)
                        .addParameter("chainId", String::class)
                        .addParameter("netVersion", Long::class)
                        .addParameter("shortNames", List::class.asClassName().parameterizedBy(String::class.asClassName()))
                        .build(),
                )
                .addProperty(
                    PropertySpec.builder("id", Int::class)
                        .initializer("id")
                        .build(),
                )
                .addProperty(
                    PropertySpec.builder("chainCode", String::class)
                        .initializer("chainCode")
                        .build(),
                )
                .addProperty(
                    PropertySpec.builder("netVersion", Long::class)
                        .initializer("netVersion")
                        .build(),
                )
                .addProperty(
                    PropertySpec.builder("chainId", String::class)
                        .initializer("chainId")
                        .build(),
                )
                .addProperty(
                    PropertySpec.builder("chainName", String::class)
                        .initializer("chainName")
                        .build(),
                )
                .addProperty(
                    PropertySpec.builder("shortNames", List::class.asClassName().parameterizedBy(String::class.asClassName()))
                        .initializer("shortNames")
                        .build(),
                ),
        ).build()
        return FileSpec.builder("io.emeraldpay.dshackle", "Chain")
            .addType(chainType)
            .build()
    }
}

open class ChainsCodeGenTask : DefaultTask() {
    init {
        group = "custom"
        description = "Generate chains config"
    }

    @TaskAction
    fun chainscodegenClass() {
        val output = project.layout.buildDirectory.dir("generated/kotlin").get().asFile
        output.mkdirs()
        CodeGen.generateFromChains(output)
    }
}

tasks.register<ChainsCodeGenTask>("chainscodegen")