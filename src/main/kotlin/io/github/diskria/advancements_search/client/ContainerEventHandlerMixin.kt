package io.github.diskria.advancements_search.client

import io.github.diskria.lapis.annotations.Env
import io.github.diskria.lapis.annotations.KMixin
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.PreeditEvent
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@KMixin(ContainerEventHandler::class, Env.Client)
interface ContainerEventHandlerMixin {

    @Inject(method = ["charTyped"], at = [At(value = "HEAD")], cancellable = true)
    fun ContainerEventHandler.charTypedHead(event: CharacterEvent, callback: CallbackInfoReturnable<Boolean>) {
        if (this is AdvancementsScreen) {
            callback.returnValue = feedCharToSearch(event)
            return
        }
    }

    @Inject(method = ["preeditUpdated"], at = [At(value = "HEAD")], cancellable = true)
    fun preeditUpdatedHead(event: PreeditEvent?, callback: CallbackInfoReturnable<Boolean>) {
        if (this is AdvancementsScreen) {
            callback.returnValue = feedPreeditToSearch(event)
            return
        }
    }
}
