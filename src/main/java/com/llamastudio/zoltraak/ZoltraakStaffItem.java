package com.llamastudio.zoltraak;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * Gay Zoltraak (chuyen tu MythicMobs item "zoltraak_staff").
 *  - Chuot phai          : Zoltraak (tia tim)
 *  - Shift + chuot phai  : Zoltraak Barrage (nhin vao ke dich trong 20 block)
 */
public class ZoltraakStaffItem extends Item {

    public ZoltraakStaffItem(Properties properties) {
        super(properties);
    }

    public static Properties properties() {
        // Nguoi choi co san 1.0 damage va 4.0 attack speed -> modifier 3.0 / -3.5
        // de tooltip hien dung "4 Attack Damage" va "0.5 Attack Speed" nhu ban goc.
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 3.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -3.5, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();

        return new Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .component(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (level instanceof ServerLevel serverLevel) {
            if (player.isShiftKeyDown()) {
                if (Spells.castBarrage(serverLevel, player)) {
                    player.getCooldowns().addCooldown(this, 10);
                    player.swing(hand, true);
                }
            } else {
                Spells.castBeam(serverLevel, player);
                player.getCooldowns().addCooldown(this, 14);
                player.swing(hand, true);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.zoltraak.beam").withStyle(s -> s.withColor(0xBA00FF)));
        tooltip.add(Component.translatable("tooltip.zoltraak.barrage").withStyle(s -> s.withColor(0xBA00FF)));
    }
}
