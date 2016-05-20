package com.minecolonies.network.messages;

import com.minecolonies.colony.CitizenData;
import com.minecolonies.colony.Colony;
import com.minecolonies.colony.ColonyManager;
import com.minecolonies.colony.buildings.Building;
import com.minecolonies.colony.buildings.BuildingWorker;
import com.minecolonies.util.BlockPosUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Message class which manages the messages hiring or firing of citizens
 */
public class HireFireMessage implements IMessage, IMessageHandler<HireFireMessage, IMessage>
{
    /**
     * The Colony ID;
     */
    private int colonyId;

    /**
     * The buildings position.
     */
    private BlockPos buildingId;

    /**
     * If hiring (true) else firing.
     */
    private boolean hire;

    /**
     * The citizen to hire/fire
     */
    private int citizenID;


    /**
     * Empty public constructor
     */
    public HireFireMessage(){}

    /**
     * Creates object for the player to hire or fire a citizen..
     *
     * @param building       View of the building to read data from
     * @param hire         Hire or fire the citizens
     */
    public HireFireMessage(Building.View building, boolean hire, int citizenID)
    {
        this.colonyId = building.getColony().getID();
        this.buildingId = building.getID();
        this.hire   = hire;
        this.citizenID = citizenID;
    }

    /**
     * Transfomration to a byteStream.
     * @param buf the used byteBuffer.
     */
    @Override
    public void toBytes(ByteBuf buf)
    {
        buf.writeInt(colonyId);
        BlockPosUtil.writeToByteBuf(buf, buildingId);
        buf.writeBoolean(hire);
        buf.writeInt(citizenID);
    }

    /**
     * Transformation from a byteStream to the variables.
     * @param buf the used byteBuffer.
     */
    @Override
    public void fromBytes(ByteBuf buf)
    {
        colonyId = buf.readInt();
        buildingId = BlockPosUtil.readFromByteBuf(buf);
        hire   = buf.readBoolean();
        citizenID = buf.readInt();
    }

    /**
     * Called when a message has been received.
     * @param message the message.
     * @param ctx the context.
     * @return possible response, in this case -> null.
     */
    @Override
    public IMessage onMessage(HireFireMessage message, MessageContext ctx)
    {
        Colony colony = ColonyManager.getColony(message.colonyId);
        if (colony != null)
        {
            if(message.hire)
            {
                CitizenData citizen = colony.getCitizen(message.citizenID);
                ((BuildingWorker) colony.getBuilding(message.buildingId)).setWorker(citizen);
            }
            else
            {
                ((BuildingWorker) colony.getBuilding(message.buildingId)).setWorker(null);
            }
        }
        return null;
    }
}
