package org.etwas.streamtweaks.mixin;

import org.etwas.streamtweaks.client.chat.TwitchChatOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void streamTweaks$renderOverlay(DrawContext context, int ticks, int mouseX, int mouseY,
            boolean focused, CallbackInfo ci) {
        TwitchChatOverlay.getInstance().render((ChatHud) (Object) this, context, ticks, focused);
    }
}
