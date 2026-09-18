package io.github.diskria.advancements_search.client

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.diskria.lapis.annotations.Env
import io.github.diskria.lapis.annotations.Extension
import io.github.diskria.lapis.annotations.KMixin
import io.github.diskria.lapis.annotations.KShadow
import net.minecraft.advancements.AdvancementNode
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.advancements.AdvancementWidget
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC

@KMixin(AdvancementWidget::class, Env.Client)
abstract class AdvancementWidgetMixin {

    @Extension
    var isSearchResult = false

    @Extension
    var isFrameBlink = false

    @Inject(method = ["extractConnectivity"], at = [At(value = "HEAD")], cancellable = true)
    fun extractConnectivityHead(
        graphics: GuiGraphicsExtractor, xo: Int, yo: Int, background: Boolean, callback: CallbackInfo
    ) {
        if (isSearchResult) {
            callback.cancel()
            return
        }
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
        )]
    )
    fun interceptFrameBlit(
        instance: GuiGraphicsExtractor,
        renderPipeline: RenderPipeline, location: Identifier, x: Int, y: Int, width: Int, height: Int,
        original: Operation<Void>
    ) {
        if (isSearchResult || !isFrameBlink) {
            original.call(instance, renderPipeline, location, x, y, width, height)
        }
    }

    @KShadow(PUBLIC, FINAL)
    abstract var advancementNode: AdvancementNode?
}
