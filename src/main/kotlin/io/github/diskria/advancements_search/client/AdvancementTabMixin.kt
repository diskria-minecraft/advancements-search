package io.github.diskria.advancements_search.client

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition
import com.llamalad7.mixinextras.sugar.Local
import com.mojang.blaze3d.platform.cursor.CursorTypes
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import io.github.diskria.lapis.annotations.Env
import io.github.diskria.lapis.annotations.Extension
import io.github.diskria.lapis.annotations.KMixin
import io.github.diskria.lapis.annotations.KShadow
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.advancements.AdvancementTab
import net.minecraft.client.gui.screens.advancements.AdvancementWidget
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen
import net.minecraft.resources.Identifier
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import javax.lang.model.element.Modifier.*

@KMixin(AdvancementTab::class, Env.Client)
abstract class AdvancementTabMixin {

    @Extension
    var isSearchResults = false

    @Inject(method = ["tick"], at = [At(value = "TAIL")])
    fun tickTail(relativeMouseX: Int, relativeMouseY: Int, callback: CallbackInfo) {
        screen.hoveredSearchResultWidget = if (isSearchResults) hovered else null
    }

    @Inject(method = ["extractContents"], at = [At("HEAD")])
    fun extractContentsHead(graphics: GuiGraphicsExtractor, windowLeft: Int, windowTop: Int, callback: CallbackInfo) {
        if (isSearchResults && hovered != null) {
            graphics.requestCursor(CursorTypes.POINTING_HAND)
        }
    }

    @WrapWithCondition(
        method = ["extractContents"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
        )]
    )
    fun interceptBackgroundBlit(
        instance: GuiGraphicsExtractor,
        renderPipeline: RenderPipeline, texture: Identifier,
        x: Int, y: Int, u: Float, v: Float, width: Int, height: Int, textureWidth: Int, textureHeight: Int,
    ): Boolean = !isSearchResults

    @ModifyExpressionValue(
        method = ["tick"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementWidget;" +
                "isMouseOver(IIII)Z"
        )]
    )
    fun interceptMouseOverWidgetCheck(original: Boolean, @Local(name = ["widget"]) widget: AdvancementWidget): Boolean {
        if (!isSearchResults && original) {
            val flashingAdvancementId = screen.flashingWidget?.advancement?.id
            if (flashingAdvancementId != null) {
                if (flashingAdvancementId != widget.advancement.id) {
                    return false
                }
                screen.stopFlashing()
            }
        }
        return original
    }

    @KShadow(PRIVATE, FINAL)
    abstract val screen: AdvancementsScreen

    @KShadow(PUBLIC)
    abstract var hovered: AdvancementWidget?
}
