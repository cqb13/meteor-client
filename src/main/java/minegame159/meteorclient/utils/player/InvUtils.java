/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2020 Meteor Development.
 */

package minegame159.meteorclient.utils.player;

import javafx.util.Pair;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listenable;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.events.world.PreTickEvent;
import minegame159.meteorclient.modules.ToggleModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.*;
import java.util.function.Predicate;

public class InvUtils implements Listenable {
    public static final int OFFHAND_SLOT = 45;
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final FindItemResult findItemResult = new FindItemResult();
    private static final Deque<Pair<Class<? extends ToggleModule>, List<Integer>>> moveQueue = new ArrayDeque<>();
    private static final Queue<Integer> currentQueue = new PriorityQueue<>();
    private static Class<? extends ToggleModule> currentModule;
    private static final Map<Class<? extends ToggleModule>, Integer> cooldown = new HashMap<>();

    public static void clickSlot(int slot, int button, SlotActionType action) {
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, button, action, mc.player);
    }

    public static Hand getHand (Item item) {
        Hand hand = Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().getItem() == item) hand = Hand.OFF_HAND;
        return hand;
    }

    public static FindItemResult findItemWithCount(Item item) {
        findItemResult.slot = -1;
        findItemResult.count = 0;

        for (int i = 0; i < mc.player.inventory.size(); i++) {
            ItemStack itemStack = mc.player.inventory.getStack(i);

            if (itemStack.getItem() == item) {
                if (!findItemResult.found()) findItemResult.slot = i;
                findItemResult.count += itemStack.getCount();
            }
        }

        return findItemResult;
    }

    public static int findItem(Item item, Predicate<ItemStack> isGood) {
        for (int i = 0; i < mc.player.inventory.size(); i++) {
            ItemStack itemStack = mc.player.inventory.getStack(i);
            if (itemStack.getItem() == item && isGood.test(itemStack)) return i;
        }

        return -1;
    }

    public static int findItemInHotbar(Item item, Predicate<ItemStack> isGood) {
        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.inventory.getStack(i);
            if (itemStack.getItem() == item && isGood.test(itemStack)) return i;
        }

        return -1;
    }

    public static int invIndexToSlotId(int invIndex) {
        if (invIndex < 9) return 44 - (8 - invIndex);
        return invIndex;
    }

    public static class FindItemResult {
        public int slot, count;

        public boolean found() {
            return slot != -1;
        }
    }

    @EventHandler
    private final Listener<PreTickEvent> onTick = new Listener<>(event -> {
        for (Class<? extends ToggleModule> klass : cooldown.keySet()){
            cooldown.replace(klass, cooldown.get(klass) - 1);
            if (cooldown.get(klass) <= 0) cooldown.remove(klass);
        }
        if (currentQueue.isEmpty() && !moveQueue.isEmpty()) {
            Pair<Class<? extends ToggleModule>, List<Integer>> pair = moveQueue.remove();
            Collections.reverse(pair.getValue());
            currentQueue.addAll(pair.getValue());
            currentModule = pair.getKey();
        } else if (!currentQueue.isEmpty()) {
            currentQueue.forEach(slot -> clickSlot(invIndexToSlotId(slot), 0, SlotActionType.PICKUP));
            currentQueue.clear();
        }
    });

    public static void addSlots(List<Integer> slots, Class<? extends ToggleModule> klass){
        Collections.reverse(slots);
        if (cooldown.containsKey(klass))return;
        if (canMove(klass)){
            moveQueue.addFirst(new Pair<>(klass, slots));
            currentModule = klass;
        } else {
            moveQueue.add(new Pair<>(klass, slots));
        }
        cooldown.put(klass, 3);
    }

    public static boolean canMove(Class<? extends ToggleModule> klass){
        return getPrio(currentModule) < getPrio(klass);
    }

    private static int getPrio(Class<? extends ToggleModule> klass){
        if (klass == null) return -1;
        return klass.getAnnotation(Priority.class).priority();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Priority{
        int priority() default -1;
    }
}