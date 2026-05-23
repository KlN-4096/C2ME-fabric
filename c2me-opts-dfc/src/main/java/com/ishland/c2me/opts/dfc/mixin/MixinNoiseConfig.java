package com.ishland.c2me.opts.dfc.mixin;

import com.google.common.base.Stopwatch;
import com.ishland.c2me.opts.dfc.common.gen.BytecodeGen;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NoiseConfig.class, priority = 900)
public class MixinNoiseConfig {

    @Mutable
    @Shadow
    @Final
    private NoiseRouter noiseRouter;

    @Mutable
    @Shadow
    @Final
    private MultiNoiseUtil.MultiNoiseSampler multiNoiseSampler;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void postCreate(CallbackInfo ci) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Reference2ReferenceMap<DensityFunction, DensityFunction> tempCache = new Reference2ReferenceOpenHashMap<>();
        this.noiseRouter = new NoiseRouter(
                BytecodeGen.compile(this.noiseRouter.barrierNoise(), "barrierNoise", tempCache),
                BytecodeGen.compile(this.noiseRouter.fluidLevelFloodednessNoise(), "fluidLevelFloodednessNoise", tempCache),
                BytecodeGen.compile(this.noiseRouter.fluidLevelSpreadNoise(), "fluidLevelSpreadNoise", tempCache),
                BytecodeGen.compile(this.noiseRouter.lavaNoise(), "lavaNoise", tempCache),
                BytecodeGen.compile(this.noiseRouter.temperature(), "temperature", tempCache),
                BytecodeGen.compile(this.noiseRouter.vegetation(), "vegetation", tempCache),
                BytecodeGen.compile(this.noiseRouter.continents(), "continents", tempCache),
                BytecodeGen.compile(this.noiseRouter.erosion(), "erosion", tempCache),
                BytecodeGen.compile(this.noiseRouter.depth(), "depth", tempCache),
                BytecodeGen.compile(this.noiseRouter.ridges(), "ridges", tempCache),
                BytecodeGen.compile(this.noiseRouter.initialDensityWithoutJaggedness(), "initialDensityWithoutJaggedness", tempCache),
                BytecodeGen.compile(this.noiseRouter.finalDensity(), "finalDensity", tempCache),
                BytecodeGen.compile(this.noiseRouter.veinToggle(), "veinToggle", tempCache),
                BytecodeGen.compile(this.noiseRouter.veinRidged(), "veinRidged", tempCache),
                BytecodeGen.compile(this.noiseRouter.veinGap(), "veinGap", tempCache)
        );
        this.multiNoiseSampler = new MultiNoiseUtil.MultiNoiseSampler(
                BytecodeGen.compile(this.multiNoiseSampler.temperature(), "temperature", tempCache),
                BytecodeGen.compile(this.multiNoiseSampler.humidity(), "humidity", tempCache),
                BytecodeGen.compile(this.multiNoiseSampler.continentalness(), "continentalness", tempCache),
                BytecodeGen.compile(this.multiNoiseSampler.erosion(), "erosion", tempCache),
                BytecodeGen.compile(this.multiNoiseSampler.depth(), "depth", tempCache),
                BytecodeGen.compile(this.multiNoiseSampler.weirdness(), "weirdness", tempCache),
                this.multiNoiseSampler.spawnTarget()
        );
        stopwatch.stop();
        System.out.println(String.format("Density function compilation finished in %s", stopwatch));
    }

}
