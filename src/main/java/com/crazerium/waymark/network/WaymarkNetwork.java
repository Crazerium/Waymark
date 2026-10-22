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
        CHANNEL.registerMessage(MessageBlob.Handler.class, MessageBlob.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageTaskProgress.Handler.class, MessageTaskProgress.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageQuestCompleted.Handler.class, MessageQuestCompleted.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageQuestReset.Handler.class, MessageQuestReset.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageRewardClaimed.Handler.class, MessageRewardClaimed.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageEditApply.Handler.class, MessageEditApply.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageCanEdit.Handler.class, MessageCanEdit.class, id++, Side.CLIENT);
        CHANNEL.registerMessage(MessageImageIndex.Handler.class, MessageImageIndex.class, id++, Side.CLIENT);
        // client -> server
        CHANNEL.registerMessage(MessageSubmitTask.Handler.class, MessageSubmitTask.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageClaimReward.Handler.class, MessageClaimReward.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageEditRequest.Handler.class, MessageEditRequest.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageImageRequest.Handler.class, MessageImageRequest.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageImageUpload.Handler.class, MessageImageUpload.class, id++, Side.SERVER);
        CHANNEL.registerMessage(MessageMeUnbind.Handler.class, MessageMeUnbind.class, id++, Side.SERVER);
    }
}
