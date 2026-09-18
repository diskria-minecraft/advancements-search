package io.github.diskria.advancements_search.client

import com.llamalad7.mixinextras.injector.ModifyReturnValue
import io.github.diskria.lapis.annotations.Env
import io.github.diskria.lapis.annotations.KMixin
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@KMixin(Screen::class, Env.Client)
class ScreenMixin {

    @Inject(method = ["resize"], at = [At(value = "HEAD")])
    fun Screen.resizeHead(width: Int, height: Int, callback: CallbackInfo) {
        if (this is AdvancementsScreen) resizeSearch(width, height)
    }

    @ModifyReturnValue(method = ["isInputCaptured"], at = [At("RETURN")])
    fun Screen.isInputCapturedHead(original: Boolean): Boolean =
        original || this is AdvancementsScreen && searchCapturesInput()
}
