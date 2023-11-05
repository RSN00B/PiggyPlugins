package com.piggyplugins.ItemCombiner;

import com.example.EthanApiPlugin.Collections.Bank;
import com.example.EthanApiPlugin.Collections.BankInventory;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.InteractionApi.BankInteraction;
import com.example.InteractionApi.TileObjectInteraction;
import com.example.Packets.MousePackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.piggyplugins.PiggyUtils.API.InventoryUtil;
import com.piggyplugins.PiggyUtils.BreakHandler.ReflectBreakHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

import java.util.Arrays;

@PluginDescriptor(
        name = "<html><font color=\"#FF9DF9\">[PP]</font> Item Combiner</html>",
        description = "Automatically banks & combines items for you",
        enabledByDefault = false,
        tags = {"ethan", "piggy"}
)
@Slf4j
public class ItemCombinerPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private ItemCombinerConfig config;
    @Inject
    private KeyManager keyManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ItemCombinerOverlay overlay;
    @Inject
    private ReflectBreakHandler breakHandler;
    @Getter
    private boolean started;
    private int afkTicks;
    private boolean deposit;
    private boolean isMaking;
    private boolean debug = true;

    @Provides
    private ItemCombinerConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(ItemCombinerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        isMaking = false;
        keyManager.registerKeyListener(toggle);
        overlayManager.add(overlay);
        breakHandler.registerPlugin(this);
    }

    @Override
    protected void shutDown() throws Exception {
        isMaking = false;
        keyManager.unregisterKeyListener(toggle);
        overlayManager.remove(overlay);
        breakHandler.unregisterPlugin(this);
    }


    @Subscribe
    private void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN
                || !started
                || EthanApiPlugin.isMoving()
                || client.getLocalPlayer().getAnimation() != -1
                || breakHandler.isBreakActive(this)) {
            afkTicks = 0;
            return;
        }

        if (!hasItems(Bank.isOpen())) {
            isMaking = false;
        }

        if (isMaking) {
            return;
        }

        if (breakHandler.shouldBreak(this)) {
            breakHandler.startBreak(this);
            return;
        }

        Widget potionWidget = client.getWidget(17694734);
        if (potionWidget != null && !potionWidget.isHidden()) {
            MousePackets.queueClickPacket();
            WidgetPackets.queueResumePause(17694734, config.itemTwoAmt());
            isMaking = true;
            return;
        }

        if (hasItems(Bank.isOpen())) {
            useItems();
        } else {
            doBanking();
        }
    }

    private void findBank() {
        TileObjects.search()
                .filter(tileObject -> {
                    ObjectComposition objectComposition = TileObjectQuery.getObjectComposition(tileObject);
                    return objectComposition.getName().toLowerCase().contains("bank") ||
                            Arrays.stream(objectComposition.getActions()).anyMatch(action -> action != null && action.toLowerCase().contains("bank"));
                })
                .nearestToPlayer()
                .ifPresent(tileObject -> {
                    TileObjectInteraction.interact(tileObject, "Use", "Bank");
                });

        if (!deposit) {
            deposit = true;
        }
    }

    private boolean hasItems(boolean bank) {
        return bank
                ? !BankInventory.search().withName(config.itemOneName()).empty() && !BankInventory.search().withName(config.itemTwoName()).empty()
                : InventoryUtil.hasItem(config.itemOneName()) && InventoryUtil.hasItems(config.itemTwoName());
    }

    private void doBanking() {
        if (!Bank.isOpen()) {
            findBank();
            return;
        }

        Widget depositInventory = client.getWidget(WidgetInfo.BANK_DEPOSIT_INVENTORY);

        if (depositInventory != null) {
            MousePackets.queueClickPacket();
            WidgetPackets.queueWidgetAction(depositInventory, "Deposit inventory");
        }

        Bank.search().withName(config.itemOneName()).first().ifPresentOrElse(item -> {
            BankInteraction.withdrawX(item, config.itemOneAmt());
        }, () -> {
            EthanApiPlugin.stopPlugin(this);
        });

        Bank.search().withName(config.itemTwoName()).first().ifPresentOrElse(item -> {
            BankInteraction.withdrawX(item, config.itemTwoAmt());
        }, () -> {
            EthanApiPlugin.stopPlugin(this);
        });
    }

    private void useItems() {
        Widget itemOne = Inventory.search().filter(item -> item.getName().contains(config.itemOneName())).first().get();
        Widget itemTwo = Inventory.search().filter(item -> item.getName().contains(config.itemTwoName())).first().get();

        MousePackets.queueClickPacket();
        MousePackets.queueClickPacket();
        WidgetPackets.queueWidgetOnWidget(itemOne, itemTwo);
    }

    private final HotkeyListener toggle = new HotkeyListener(() -> config.toggle()) {
        @Override
        public void hotkeyPressed() {
            toggle();
        }
    };

    public void toggle() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        started = !started;

        if (started) {
            breakHandler.startPlugin(this);
        } else {
            breakHandler.stopPlugin(this);
        }
    }
}