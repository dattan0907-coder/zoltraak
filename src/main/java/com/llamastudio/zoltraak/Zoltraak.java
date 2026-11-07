package com.llamastudio.zoltraak;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Zoltraak.MODID)
public class Zoltraak {
    public static final String MODID = "zoltraak";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<ZoltraakStaffItem> ZOLTRAAK_STAFF =
            ITEMS.registerItem("zoltraak_staff", ZoltraakStaffItem::new, ZoltraakStaffItem.properties());

    public Zoltraak(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
        Scheduler.init();
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ZOLTRAAK_STAFF);
        }
    }
}
