package me.evo.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import me.evo.OpusAudioStream;
import net.minecraft.client.sound.*;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

@Mixin(SoundLoader.class)
public class SoundLoaderMixin {

    @Inject(method = "loadStreamed", at = @At("RETURN"))
    private void loaderr(Identifier id, boolean repeatInstantly, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        cir.getReturnValue().whenComplete((staticSound, throwable) -> {
            if (throwable != null) {
                throwable.printStackTrace();
            }
        });
    }

    @ModifyExpressionValue(
            method = "method_19747",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resource/ResourceFactory;open(Lnet/minecraft/util/Identifier;)Ljava/io/InputStream;"
            )
    )
    private InputStream checkHeader$static(InputStream stream, @Share("opus") LocalBooleanRef opus) {
        return checkHeader(stream, opus);
    }

    @Unique
    @NotNull
    private InputStream checkHeader(InputStream stream, @Share("opus") LocalBooleanRef opus) {
        byte[] buffer = new byte[8];
        InputStream restored = OpusAudioStream.extractHeader(buffer, stream);
        opus.set(new String(buffer).equals("OpusHead"));
        return restored;
    }

    @WrapOperation(
            method = "method_19747",
            at = @At(
                    value = "NEW",
                    target = "(Ljava/io/InputStream;)Lnet/minecraft/client/sound/OggAudioStream;"
            )
    )
    private OggAudioStream skipCall(InputStream stream, Operation<OggAudioStream> original) {
        return null;
    }

    @ModifyVariable(
            method = "method_19747",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            )
    )
    private NonRepeatingAudioStream replace(NonRepeatingAudioStream value, @Local InputStream stream, @Share("opus") LocalBooleanRef opus) {
        return opus.get() ? new OpusAudioStream(stream) : value;
    }

    @ModifyExpressionValue(
            method = "method_19745",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resource/ResourceFactory;open(Lnet/minecraft/util/Identifier;)Ljava/io/InputStream;"
            )
    )
    private InputStream checkHeader$streamed(InputStream stream, @Share("opus") LocalBooleanRef opus) {
        return checkHeader(stream, opus);
    }

    @Inject(
            method = "method_19745",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/resource/ResourceFactory;open(Lnet/minecraft/util/Identifier;)Ljava/io/InputStream;",
                    shift = At.Shift.BY,
                    by = 2
            ),
            cancellable = true
    )
    private void replace(
            Identifier identifier,
            boolean bl,
            CallbackInfoReturnable<AudioStream> cir,
            @Local InputStream stream,
            @Local(argsOnly = true) boolean repeatInstantly,
            @Share("opus") LocalBooleanRef opus
    ) throws IOException {
        if (opus.get()) {
            cir.setReturnValue(repeatInstantly ? new RepeatingAudioStream(OpusAudioStream::new, stream) : new OpusAudioStream(stream));
        }
    }
}