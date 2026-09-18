package io.github.diskria.advancements_search;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.resources.Identifier;

public class AdvancementsSearchMod implements ClientModInitializer {

    public static final Identifier ADVANCEMENTS_SEARCH_ID =
        Identifier.fromNamespaceAndPath(BuildConfig.MOD_ID, BuildConfig.MOD_ID + "/root");

    public static boolean isSearch(AdvancementNode root) {
        return root != null && ADVANCEMENTS_SEARCH_ID.equals(root.holder().id());
    }

    @Override
    public void onInitializeClient() {
    }
}
