package nl.rutgerkok.worldgeneratorapi.internal;

import java.util.Objects;

import org.bukkit.World;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;

import net.minecraft.server.v1_14_R1.ChunkGenerator;
import net.minecraft.server.v1_14_R1.ChunkGeneratorAbstract;
import net.minecraft.server.v1_14_R1.ChunkProviderDebug;
import net.minecraft.server.v1_14_R1.ChunkProviderFlat;
import net.minecraft.server.v1_14_R1.ChunkProviderGenerate;
import net.minecraft.server.v1_14_R1.ChunkProviderHell;
import net.minecraft.server.v1_14_R1.ChunkProviderTheEnd;
import net.minecraft.server.v1_14_R1.GeneratorAccess;
import net.minecraft.server.v1_14_R1.SeededRandom;
import net.minecraft.server.v1_14_R1.WorldServer;
import nl.rutgerkok.worldgeneratorapi.BaseChunkGenerator;
import nl.rutgerkok.worldgeneratorapi.internal.bukkitoverrides.ChunkDataImpl;
import nl.rutgerkok.worldgeneratorapi.internal.bukkitoverrides.InjectedChunkGenerator;

final class BaseChunkGeneratorImpl implements BaseChunkGenerator {

    /**
     * Extracts the base chunk generator from a Minecraft world using the currently
     * in use chunk generator. If the chunk generator is provided by us, we can
     * return the original {@link BaseChunkGenerator}. If the chunk generator is
     * provided by Minecraft's {@link ChunkGeneratorAbstract}, we can wrap
     * Minecraft's base chunk generator. Otherwise, we throw an exception.
     *
     * @param world
     *            The world.
     * @return The base chunk generator.
     * @throws UnsupportedOperationException
     *             If the world has a world generator not provided by us or
     *             Minecraft (so it's a custom one).
     */
    static BaseChunkGenerator fromMinecraft(World world) {
        WorldServer worldServer = ((CraftWorld) world).getHandle();
        ChunkGenerator<?> chunkGenerator = worldServer.getChunkProvider().getChunkGenerator();
        if (chunkGenerator instanceof InjectedChunkGenerator) {
            return ((InjectedChunkGenerator) chunkGenerator).getBaseChunkGenerator();
        }
        if (isSupported(chunkGenerator)) {
            return new BaseChunkGeneratorImpl(worldServer, chunkGenerator);
        }

        throw new UnsupportedOperationException(
                "Cannot extract base chunk generator from " + chunkGenerator.getClass()
                + ". \nYou can only customize worlds where the base terrain is"
                + " generated by Minecraft or using the WorldGeneratorApi methods."
                + " If you are using the WorldGeneratorApi methods, make sure that"
                + " a BaseChunkGenerator was set before other aspects of terrain"
                + " generation were modified.");
    }

    private static boolean isSupported(ChunkGenerator<?> chunkGenerator) {
        // Make sure this matches setBlocksInChunk below
        return chunkGenerator instanceof ChunkProviderDebug || chunkGenerator instanceof ChunkProviderFlat
                || chunkGenerator instanceof ChunkProviderGenerate || chunkGenerator instanceof ChunkProviderHell
                || chunkGenerator instanceof ChunkProviderTheEnd;
    }

    private final ChunkGenerator<?> internal;
    private final GeneratorAccess world;

    private BaseChunkGeneratorImpl(GeneratorAccess world, ChunkGenerator<?> chunkGenerator) {
        if (chunkGenerator instanceof InjectedChunkGenerator) {
            throw new IllegalArgumentException("Double-wrapping");
        }
        this.world = Objects.requireNonNull(world, "world");
        this.internal = Objects.requireNonNull(chunkGenerator, "internal");
    }

    @Override
    public void setBlocksInChunk(GeneratingChunk chunk) {
        ChunkDataImpl blocks = (ChunkDataImpl) chunk.getBlocksForChunk();
        SeededRandom random = new SeededRandom();
        random.a(chunk.getChunkX(), chunk.getChunkZ());

        // Make sure this matches isSupported above
        if (internal instanceof ChunkProviderGenerate) {
            ((ChunkProviderGenerate) internal).buildNoise(world, blocks.getHandle());
        } else if (internal instanceof ChunkProviderFlat) {
            ((ChunkProviderFlat) internal).buildNoise(world, blocks.getHandle());
        } else if (internal instanceof ChunkProviderHell) {
            ((ChunkProviderHell) internal).buildNoise(world, blocks.getHandle());
        } else if (internal instanceof ChunkProviderTheEnd) {
            ((ChunkProviderTheEnd) internal).buildNoise(world, blocks.getHandle());
        } else if (internal instanceof ChunkProviderDebug) {
            // Generate nothing - there is no base terrain
        } else {
            throw new UnsupportedOperationException("Didn't recognize " + internal.getClass());
        }
    }

}