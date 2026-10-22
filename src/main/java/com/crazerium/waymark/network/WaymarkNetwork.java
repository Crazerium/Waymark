package com.crazerium.waymark.network;

import com.crazerium.waymark.Waymark;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class WaymarkNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Waymark.MODID);

    private WaymarkNetwork() {}

    public static void init() {
        int id = 0;
        // server -> client
        CHANNEL.registerMessage(MessageSyncBook.Handler.class, MessageSyncBook.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageSyncProgress.Handler.class, MessageSyncProgress.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageTaskProgress.Handler.class, MessageTaskProgress.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageQuestCompleted.Handler.class, MessageQuestCompleted.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageRewardClaimed.Handler.class, MessageRewardClaimed.class, id++, Side.CLIENT);
        // client -> server
        CHANNEL.registerMessage(MessageSubmitTask.Handler.class, MessageSubmitTask.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageClaimReward.Handler.class, MessageClaimReward.class, id++, Side.SERVER);
    }
}
