package io.github.diskria.advancements_search.client

import com.llamalad7.mixinextras.injector.ModifyExpressionValue
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import com.mojang.blaze3d.platform.InputConstants
import io.github.diskria.lapis.annotations.*
import net.minecraft.advancements.*
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.advancements.AdvancementTab
import net.minecraft.client.gui.screens.advancements.AdvancementTabType
import net.minecraft.client.gui.screens.advancements.AdvancementWidget
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.client.multiplayer.ClientAdvancements
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Util
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.Items
import org.objectweb.asm.Opcodes
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.awt.Point
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@KMixin(AdvancementsScreen::class, Env.Client)
abstract class AdvancementsScreenMixin(@Origin private val screen: AdvancementsScreen) {

    private val searchBox: EditBox by lazy {
        EditBox(
            /* font = */ screen.font,
            /* x = */ 0,
            /* y = */ 0,
            /* width = */ SEARCH_FIELD_WIDTH - SEARCH_FIELD_TEXT_LEFT_OFFSET - 8,
            /* height = */ screen.font.lineHeight,
            /* narration = */ SEARCH_TAB_TITLE,
        ).apply {
            @Suppress("UsePropertyAccessSyntax") setMaxLength(50)
            isBordered = false
            setTextColor(-1)
            setInvertHighlightedTextColor(false)
        }
    }

    private val searchResultsRootNode: AdvancementNode by lazy {
        val fakeHolder = buildFakeHolder(SEARCH_RESULTS_ROOT_ADVANCEMENT_ID) {
            rootDisplay(
                /* icon = */ Items.STONE,
                /* title = */ SEARCH_TAB_TITLE,
                /* description = */ Component.empty(),
                /* background = */ Identifier.withDefaultNamespace("gui/advancements/backgrounds/stone"),
                /* frame = */ AdvancementType.TASK,
                /* showToast = */ false,
                /* announceChat = */ false,
                /* hidden = */ true,
            )
        }
        AdvancementNode(fakeHolder, null)
    }

    private val searchResultsRootWidget by lazy {
        val display = searchResultsRootNode.holder().value.display.get()
        AdvancementWidget(screen.minecraft, searchResultsRootNode, display).apply {
            isSearchResult = true
        }
    }

    private val searchResultsTab: AdvancementTab by lazy {
        val display = searchResultsRootNode.holder().value.display.get()
        AdvancementTab(
            /* minecraft = */ screen.minecraft,
            /* screen = */ screen,
            /* type = */ AdvancementTabType.ABOVE,
            /* index = */ 0,
            /* root = */ searchResultsRootWidget,
            /* icon = */ display.icon(),
            /* title = */ display.title(),
            /* background = */ display.background.get().texturePath,
        ).apply {
            isSearchResults = true
        }
    }

    private val searchResults = mutableListOf<SearchResult>()

    private var searchResultsMaxColumns = 0
    private var searchResultsOriginX = 0

    private var isSearchActive = false
    private var treeWidth = 0
    private var treeHeight = 0

    @Extension
    fun feedCharToSearch(event: CharacterEvent): Boolean {
        val oldText = searchBox.value
        val result = searchBox.charTyped(event)
        if (result && oldText != searchBox.value) {
            processSearch()
        }
        return result
    }

    private fun feedKeyToSearch(event: KeyEvent): Boolean {
        val oldText = searchBox.value
        val result = searchBox.keyPressed(event)
        if (result && oldText != searchBox.value) {
            processSearch()
        }
        return result
    }

    @Extension
    fun feedPreeditToSearch(event: PreeditEvent?): Boolean =
        searchBox.preeditUpdated(event)

    @Extension
    fun searchCapturesInput(): Boolean =
        searchBox.capturesInput()

    @Extension
    fun resizeSearch(width: Int, height: Int) {
        val oldText = searchBox.value
        screen.init(width, height)
        searchBox.value = oldText
        if (searchBox.value.isNotEmpty()) {
            refreshSearchResults()
        }
    }

    private fun attachSearchToScreen() {
        screen.addWidget(searchBox)
        screen.setInitialFocus(searchBox)
    }

    private var flashingTickCounter = 0

    @Extension
    var hoveredSearchResultWidget: AdvancementWidget? = null

    private var clickedSearchResultWidget: AdvancementWidget? = null

    @Extension
    var flashingWidget: AdvancementWidget? = null
        private set

    private var centeredToFlashing = false

    @Extension
    fun stopFlashing() {
        flashingTickCounter = 0
        flashingWidget?.isFrameBlink = false
        flashingWidget = null
        centeredToFlashing = false
    }

    private fun startFlashing(widget: AdvancementWidget?) {
        stopFlashing()
        if (widget != null) {
            flashingWidget = widget
            flashingTickCounter = FLASH_BLINKS_COUNT * 2 * FLASH_BLINKS_INTERVAL
        }
    }

    private fun processSearch() {
        val searchTerm = searchBox.value
        isSearchActive = searchTerm.isNotEmpty()
        searchResults.clear()
        if (!isSearchActive) return
        searchResults.addAll(
            advancements.progress.entries.asSequence().mapNotNull { (holder, progress) ->
                val advancement = holder.value
                val display = advancement.display.getOrNull() ?: return@mapNotNull null
                if (display.hidden && !progress.isDone) return@mapNotNull null

                val customData = display.icon.get(DataComponents.CUSTOM_DATA)
                if (customData != null) {
                    when (customData.copyTag().getIntOr(HIDE_MODE_NBT, 0)) {
                        HIDE_UNTIL_DONE if !progress.isDone -> return@mapNotNull null
                        HIDE_ALWAYS -> return@mapNotNull null
                    }
                }

                val matches = display.title.string.contains(searchTerm, ignoreCase = true) ||
                    display.description.string.contains(searchTerm, ignoreCase = true) ||
                    display.icon.rawHoverName?.contains(searchTerm, ignoreCase = true) == true
                if (!matches) return@mapNotNull null

                SearchResult(holder, display, progress)
            }.sortedWith(
                compareBy<SearchResult> { it.progress.isDone }
                    .thenByDescending { (it.progress.percent * 10).toInt() }
                    .thenBy { result ->
                        if (result.holder.value.isRoot) Int.MIN_VALUE
                        else TYPE_ORDER.indexOf(result.display.type).takeIf { it != -1 } ?: Int.MAX_VALUE
                    }
                    .thenBy { it.holder.id }
            )
        )
        refreshSearchResults()
    }

    private fun refreshSearchResults() {
        searchResultsTab.apply {
            widgets.values.forEach { widget ->
                widget.parent = null
                widget.children.clear()
            }
            widgets.clear()
            hovered = null
            fade = 0.0f

            scrollX = searchResultsOriginX.toDouble()
            scrollY = 0.0
            minX = Int.MAX_VALUE
            minY = Int.MAX_VALUE
            maxX = Int.MIN_VALUE
            maxY = Int.MIN_VALUE
            centered = true
        }
        if (searchResults.isEmpty()) return

        searchResultsTab.addWidget(searchResultsRootWidget)
        var parentNode = searchResultsRootNode
        var x = 0
        var y = 0
        searchResults.forEach { result ->
            val originalAdvancement = result.holder.value
            val childHolder = buildFakeHolder(result.holder.id) {
                parent(parentNode.holder())
                display(result.display)
                rewards(originalAdvancement.rewards)
                originalAdvancement.criteria.forEach { (name, criterion) -> addCriterion(name, criterion) }
                requirements(originalAdvancement.requirements)
            }
            val childNode = AdvancementNode(childHolder, parentNode).apply {
                setLocation(x.toFloat(), y.toFloat())
            }
            val childWidget = AdvancementWidget(screen.minecraft, childNode, result.display).apply {
                setProgress(result.progress)
                isSearchResult = true
            }
            searchResultsTab.addWidget(childWidget)
            if (x == searchResultsMaxColumns - 1) {
                parentNode = searchResultsRootNode
                x = 0
                y++
            } else {
                parentNode = AdvancementNode(childHolder, childNode)
                x++
            }
        }
    }

    @Inject(method = ["init"], at = [At(value = "TAIL")])
    fun initTail(callbackInfo: CallbackInfo) {
        attachSearchToScreen()
    }

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementsScreen;" +
                "extractTooltips(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"
        )]
    )
    fun extractRenderStateBeforeTooltips(
        instance: AdvancementsScreen,
        graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int,
        original: Operation<Void>,
        @Local(name = ["a"], argsOnly = true) a: Float,
    ) {
        val frameOffset = 1
        val frameContainerWidth = frameOffset + WIDGET_SIZE + frameOffset
        val columnsCount = treeWidth / frameContainerWidth
        val rowWidth = frameContainerWidth * columnsCount
        val horizontalOffset = treeWidth - rowWidth - TREE_X_OFFSET
        val originX = horizontalOffset / 2
        if (searchResultsMaxColumns != columnsCount || searchResultsOriginX != originX) {
            searchResultsMaxColumns = columnsCount
            searchResultsOriginX = originX
            if (isSearchActive) {
                refreshSearchResults()
            }
        }
        val fieldX = leftPos + treeWidth + WINDOW_BORDER_SIZE - SEARCH_FIELD_WIDTH + 1
        val fieldY = topPos + 4
        graphics.blit(
            /* renderPipeline = */ RenderPipelines.GUI_TEXTURED,
            /* texture = */ SEARCH_TAB_TEXTURE_ID,
            /* x = */ fieldX,
            /* y = */ fieldY,
            /* u = */ SEARCH_FIELD_UV.x.toFloat(),
            /* v = */ SEARCH_FIELD_UV.y.toFloat(),
            /* width = */ SEARCH_FIELD_WIDTH,
            /* height = */ SEARCH_FIELD_HEIGHT,
            /* textureWidth = */ 256,
            /* textureHeight = */ 256,
        )
        searchBox.setX(fieldX + SEARCH_FIELD_TEXT_LEFT_OFFSET)
        searchBox.setY(fieldY + SEARCH_FIELD_TEXT_LEFT_OFFSET)
        searchBox.extractRenderState(graphics, mouseX, mouseY, a)
        original.call(instance, graphics, mouseX, mouseY)
    }

    @Inject(method = ["mouseClicked"], at = [At(value = "HEAD")], cancellable = true)
    fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean, callback: CallbackInfoReturnable<Boolean>) {
        if (searchBox.mouseClicked(event, doubleClick)) {
            isSearchActive = searchBox.value.isNotEmpty()
            if (isSearchActive) stopFlashing()
            callback.returnValue = true
            return
        }
        if (hoveredSearchResultWidget != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            clickedSearchResultWidget = hoveredSearchResultWidget
        }
    }

    @Inject(method = ["mouseScrolled"], at = [At(value = "HEAD")])
    fun onMouseScrolled(
        x: Double, y: Double, scrollX: Double, scrollY: Double,
        callback: CallbackInfoReturnable<Boolean>
    ) {
        clickedSearchResultWidget = null
    }

    @Inject(method = ["mouseDragged"], at = [At(value = "HEAD")])
    fun onMouseDragged(event: MouseButtonEvent, dx: Double, dy: Double, callback: CallbackInfoReturnable<Boolean>) {
        clickedSearchResultWidget = null
    }

    @Inject(method = ["mouseReleased"], at = [At(value = "HEAD")])
    fun onMouseReleased(event: MouseButtonEvent, callback: CallbackInfoReturnable<Boolean>) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return
        }
        val hovered = hoveredSearchResultWidget ?: return
        val clicked = clickedSearchResultWidget ?: return
        if (clicked.advancement.id != hovered.advancement.id) return
        val rootToNavigate = advancements.tree().get(clicked.advancement.id)?.root() ?: return

        isSearchActive = false
        hoveredSearchResultWidget = null
        clickedSearchResultWidget = null
        searchResultsTab.hovered = null
        searchResultsTab.fade = 0.0f

        advancements.setSelectedTab(rootToNavigate.holder(), true)
        startFlashing(selectedTab?.widgets?.values?.find { it.advancement.id == clicked.advancement.id })
    }

    @Inject(method = ["extractInside"], at = [At("TAIL")])
    fun extractInsideTail(graphics: GuiGraphicsExtractor, callback: CallbackInfo) {
        if (centeredToFlashing) {
            return
        }
        val flashingWidget = flashingWidget ?: return
        val selectedTab = selectedTab ?: return
        val centerX = (WIDGET_SIZE - treeWidth) / 2
        val centerY = (WIDGET_SIZE - treeHeight) / 2
        selectedTab.scroll(
            -(selectedTab.scrollX + flashingWidget.x + TREE_X_OFFSET + centerX),
            -(selectedTab.scrollY + flashingWidget.y + centerY),
        )
        centeredToFlashing = true
    }

    @WrapOperation(
        method = ["mouseClicked"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientAdvancements;" +
                "setSelectedTab(Lnet/minecraft/advancements/AdvancementHolder;Z)V"
        )]
    )
    fun beforeSetSelectedTab(
        instance: ClientAdvancements,
        selectedTab: AdvancementHolder, tellServer: Boolean,
        original: Operation<Void>,
    ) {
        isSearchActive = false
        stopFlashing()
        original.call(instance, selectedTab, tellServer)
    }

    @WrapOperation(
        method = ["extractWindow"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;" +
                "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
        )]
    )
    fun interceptExtractTitle(
        instance: GuiGraphicsExtractor,
        font: Font, str: Component, x: Int, y: Int, color: Int, dropShadow: Boolean,
        original: Operation<Void>
    ) {
        val lineWidth = font.width(str)
        val right = x + treeWidth - SEARCH_FIELD_WIDTH - 3
        val availableMessageWidth = right - x
        if (lineWidth > availableMessageWidth) {
            val maxPosition = lineWidth - availableMessageWidth
            val time = Util.getMillis() / 1000.0
            val period = max(maxPosition.toDouble() * 0.5, 3.0)
            val alpha = sin(Math.PI / 2 * cos(Math.PI * 2 * time / period)) / 2 + 0.5
            val pos = Mth.lerp(alpha, 0.0, maxPosition.toDouble())
            instance.enableScissor(x, y, right, y + font.lineHeight)
            original.call(instance, font, str, x - pos.toInt(), y, color, dropShadow)
            instance.disableScissor()
        } else {
            original.call(instance, font, str, x, y, color, dropShadow)
        }
    }

    @ModifyExpressionValue(
        method = [
            "extractRenderState",
            "extractInside",
            "extractWindow",
            "extractTooltips",
            "tick",
            "mouseScrolled",
            "mouseDragged",
        ],
        at = [At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementsScreen;" +
                "selectedTab:Lnet/minecraft/client/gui/screens/advancements/AdvancementTab;",
            opcode = Opcodes.GETFIELD
        )]
    )
    fun interceptSelectedTab(original: AdvancementTab?): AdvancementTab? =
        if (isSearchActive) searchResultsTab else original

    @WrapOperation(
        method = ["extractRenderState"],
        at = [At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/advancements/AdvancementsScreen;" +
                "extractInside(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"
        )]
    )
    fun beforeExtractInside(instance: AdvancementsScreen, graphics: GuiGraphicsExtractor, original: Operation<Void>) {
        treeWidth = abs(leftPos * 2 - screen.width) - WINDOW_BORDER_SIZE - WINDOW_BORDER_SIZE
        treeHeight = abs(topPos * 2 - screen.height) - WINDOW_HEADER_HEIGHT - WINDOW_BORDER_SIZE
        original.call(instance, graphics)
    }

    @Inject(method = ["keyPressed"], at = [At(value = "HEAD")], cancellable = true)
    fun keyPressedHead(event: KeyEvent, callback: CallbackInfoReturnable<Boolean>) {
        if (feedKeyToSearch(event)) {
            callback.returnValue = true
            return
        }
        if (searchBox.capturesInput() && !event.isEscape) {
            callback.returnValue = true
            return
        }
    }

    @Inject(method = ["tick"], at = [At(value = "TAIL")])
    fun tickTail(callbackInfo: CallbackInfo) {
        if (flashingTickCounter > 0) {
            flashingTickCounter--
            if (flashingTickCounter == 0) {
                stopFlashing()
            } else {
                flashingWidget?.isFrameBlink = (flashingTickCounter / FLASH_BLINKS_INTERVAL) % 2 == 0
            }
        }
    }

    @KShadow(PRIVATE, FINAL)
    abstract val advancements: ClientAdvancements

    @KShadow(PRIVATE)
    abstract val selectedTab: AdvancementTab?

    @KShadow(PRIVATE)
    abstract val leftPos: Int

    @KShadow(PRIVATE)
    abstract val topPos: Int

    private class SearchResult(
        val holder: AdvancementHolder,
        val display: DisplayInfo,
        val progress: AdvancementProgress,
    )

    companion object {
        val SEARCH_RESULTS_ROOT_ADVANCEMENT_ID =
            Identifier.fromNamespaceAndPath("advancements_search", "advancements_search/root")
        private val SEARCH_TAB_TEXTURE_ID =
            Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_item_search.png")
        private val SEARCH_FIELD_UV = Point(80, 4)
        private val SEARCH_TAB_TITLE = Component.translatable("gui.recipebook.search_hint")

        private val HIDE_MODE_NBT = Identifier.fromNamespaceAndPath("advancements_search", "hide_mode").toString()
        private const val HIDE_UNTIL_DONE = 1
        private const val HIDE_ALWAYS = 2

        private const val SEARCH_FIELD_WIDTH = 90
        private const val SEARCH_FIELD_HEIGHT = 12
        private const val WINDOW_BORDER_SIZE = 9
        private const val WINDOW_HEADER_HEIGHT = 18

        private const val FLASH_BLINKS_COUNT = 5
        private const val FLASH_BLINKS_INTERVAL = 4

        private const val WIDGET_SIZE = 26
        private const val TREE_X_OFFSET = 3
        private const val SEARCH_FIELD_TEXT_LEFT_OFFSET = 2

        private val TYPE_ORDER = listOf(AdvancementType.TASK, AdvancementType.GOAL, AdvancementType.CHALLENGE)
    }
}

private fun buildFakeHolder(id: Identifier, block: Advancement.Builder.() -> Unit): AdvancementHolder =
    Advancement.Builder.recipeAdvancement().apply(block).build(id)

private val ItemStackTemplate.rawHoverName: String?
    get() = get(DataComponents.CUSTOM_NAME)?.string
        ?: get(DataComponents.WRITTEN_BOOK_CONTENT)?.title?.raw?.takeIf { it.isNotBlank() }
        ?: get(DataComponents.ITEM_NAME)?.string
