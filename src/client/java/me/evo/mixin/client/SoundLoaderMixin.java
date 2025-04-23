package me.evo.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import me.evo.OpusAudioStream;
import net.minecraft.client.sound.NonRepeatingAudioStream;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.*;
import java.nio.ByteBuffer;

@Mixin(SoundLoader.class)
public class SoundLoaderMixin {

    @Inject(
            method = "method_19747",
            at = @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/resource/ResourceFactory;open(Lnet/minecraft/util/Identifier;)Ljava/io/InputStream;",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void injectInputStreamOpen(Identifier identifier, CallbackInfoReturnable<StaticSound> cir, @Local InputStream inputStream) {
        try {
            byte[] header = new byte[8];
            inputStream = OpusAudioStream.extractHeader(header, inputStream);
            String headerString = new String(header, com.google.common.base.Charsets.UTF_8);
            System.out.println(headerString);

            NonRepeatingAudioStream stream;
            if ("OpusHead".equals(headerString)) {
                stream = new OpusAudioStream(inputStream);
            } else {
                stream = new OggAudioStream(inputStream);
            }

            ByteBuffer buffer = stream.readAll();
            StaticSound result = new StaticSound(buffer, stream.getFormat());
            stream.close();

            cir.setReturnValue(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}