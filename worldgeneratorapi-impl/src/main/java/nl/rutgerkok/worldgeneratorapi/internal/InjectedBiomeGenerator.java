package nl.rutgerkok.worldgeneratorapi.internal;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.v1_16_R2.block.CraftBlock;

import com.mojang.serialization.Codec;

import net.minecraft.server.v1_16_R2.BiomeBase;
import net.minecraft.server.v1_16_R2.Biomes;
import net.minecraft.server.v1_16_R2.IRegistry;
import net.minecraft.server.v1_16_R2.RegistryGeneration;
import net.minecraft.server.v1_16_R2.WorldChunkManager;
import nl.rutgerkok.worldgeneratorapi.BiomeGenerator;
import nl.rutgerkok.worldgeneratorapi.WorldGeneratorApi;

public class InjectedBiomeGenerator extends WorldChunkManager {

    /**
     * Apparently, sometimes we need to serialize the biome generator. We cannot do
     * this, as this is not how our API is designed. (We don't register our biome
     * generators.) Instead, we serialize as if we are a biome generator that
     * generates only oceans.
     */
    private static final Codec<InjectedBiomeGenerator> DUMMY_CODEC = RegistryGeneration.WORLDGEN_BIOME.fieldOf(
            "[" + WorldGeneratorApi.class.getSimpleName()
                    + "] Custom biome generators cannot be stored in the level.dat, please ignore this error")
            .xmap(biome -> new InjectedBiomeGenerator(), biomeGenToSerialize -> {
                // Serializes as a single-biome generator
                return RegistryGeneration.WORLDGEN_BIOME.a(Biomes.OCEAN);
            })
            .stable().codec();

    private static List<BiomeBase> toBiomeBase(IRegistry<BiomeBase> biomeRegistry, Set<Biome> biomes) {
        return biomes.stream()
                .map(biome -> CraftBlock.biomeToBiomeBase(biomeRegistry, biome))
                .collect(toList());
    }

    /**
     * If this is a custom biome generator, it is wrapped to conform to Minecraft's
     * interface. If it is instead a wrapped Minecraft biome generator, it is
     * unwrapped.
     *
     * @param biomeGenerator
     *            The biome generator.
     * @return A Minecraft-compatible biome generator.
     */
    public static WorldChunkManager wrapOrUnwrap(IRegistry<BiomeBase> registry, BiomeGenerator biomeGenerator) {
        if (biomeGenerator instanceof BiomeGeneratorImpl) {
            // Already wrapping a WorldChunkManager
            BiomeGeneratorImpl biomeGeneratorImpl = (BiomeGeneratorImpl) biomeGenerator;
            if (biomeGeneratorImpl.biomeRegistry == registry) {
                // Uses the same biome registry - safe to use that instance directly
                return biomeGeneratorImpl.internal;
            }
        }
        return new InjectedBiomeGenerator(registry, biomeGenerator);
    }

    private final BiomeGenerator biomeGenerator;
    private final IRegistry<BiomeBase> biomeRegistry;

    /**
     * Constructor only used for deserialization. Just provides a dummy biome
     * generator, as the biome generator plugin should inject its own biome
     * generator.
     */
    private InjectedBiomeGenerator() {
        // Dummy constructor
        super(Arrays.asList(RegistryGeneration.WORLDGEN_BIOME.a(Biomes.OCEAN)));
        this.biomeRegistry = RegistryGeneration.WORLDGEN_BIOME;
        this.biomeGenerator = (x, y, z) -> Biome.OCEAN;
    }

    public InjectedBiomeGenerator(IRegistry<BiomeBase> biomeRegistry, BiomeGenerator biomeGenerator) {
        super(toBiomeBase(biomeRegistry, biomeGenerator.getStructureBiomes()));

        if (biomeGenerator instanceof BiomeGeneratorImpl
                && ((BiomeGeneratorImpl) biomeGenerator).biomeRegistry == biomeRegistry) {
            throw new IllegalArgumentException("Double wrapping of biome generator (that uses the same biomeRegistry)");
        }

        this.biomeRegistry = Objects.requireNonNull(biomeRegistry, "biomeRegistry");
        this.biomeGenerator = biomeGenerator; // Null check not necessary - was done in first line
    }

    @Override
    protected Codec<? extends WorldChunkManager> a() {
        return DUMMY_CODEC;
    }

    @Override
    public BiomeBase getBiome(int x, int y, int z) {
        return CraftBlock.biomeToBiomeBase(biomeRegistry,
                biomeGenerator.getZoomedOutBiome(x, y, z));
    }
}
