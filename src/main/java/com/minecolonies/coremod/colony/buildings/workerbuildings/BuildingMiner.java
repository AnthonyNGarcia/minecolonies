package com.minecolonies.coremod.colony.buildings.workerbuildings;

import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.management.Structures;
import com.ldtteam.structurize.util.PlacementSettings;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.LoadOnlyStructureHandler;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.ToolType;
import com.minecolonies.coremod.client.gui.huts.WindowHutWorkerModulePlaceholder;
import com.minecolonies.coremod.colony.buildings.AbstractBuilding;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.coremod.colony.buildings.modules.BuildingResourcesModule;
import com.minecolonies.coremod.colony.buildings.modules.QuarryModule;
import com.minecolonies.coremod.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.coremod.colony.buildings.modules.settings.BlockSetting;
import com.minecolonies.coremod.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.coremod.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.coremod.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.coremod.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.coremod.colony.buildings.views.AbstractBuildingBuilderView;
import com.minecolonies.coremod.colony.jobs.JobMiner;
import com.minecolonies.coremod.colony.workorders.WorkOrderBuildMiner;
import com.minecolonies.coremod.entity.ai.citizen.miner.Node;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.BuildingConstants.*;
import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.util.constant.ToolLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;

/**
 * The miners building.
 */
public class BuildingMiner extends AbstractBuildingStructureBuilder
{
    /**
     * Setting for solid filling block.
     */
    public static final ISettingKey<BlockSetting> FILL_BLOCK = new SettingKey<>(BlockSetting.class, new ResourceLocation(Constants.MOD_ID, "fillblock"));

    /**
     * Max depth the miner is going for.
     */
    public static final ISettingKey<IntSetting> MAX_DEPTH = new SettingKey<>(IntSetting.class, new ResourceLocation(Constants.MOD_ID, "maxdepth"));

    /**
     * The job description.
     */
    private static final String MINER = "miner";

    /**
     * The location of the topmost cobblestone the ladder starts at.
     */
    private BlockPos cobbleLocation;

    /**
     * The location of the topmost ladder in the shaft.
     */
    private BlockPos ladderLocation;

    /**
     * Required constructor.
     *
     * @param c colony containing the building.
     * @param l location of the building.
     */
    public BuildingMiner(final IColony c, final BlockPos l)
    {
        super(c, l);

        final ItemStack stackLadder = new ItemStack(Blocks.LADDER);
        final ItemStack stackTorch = new ItemStack(Blocks.TORCH);
        final ItemStack stackCobble = new ItemStack(Blocks.COBBLESTONE);

        keepX.put(stackLadder::sameItem, new Tuple<>(STACKSIZE, true));
        keepX.put(stackTorch::sameItem, new Tuple<>(STACKSIZE, true));
        keepX.put(stackCobble::sameItem, new Tuple<>(STACKSIZE, true));

        keepX.put(itemStack -> ItemStackUtils.hasToolLevel(itemStack, ToolType.PICKAXE, TOOL_LEVEL_WOOD_OR_GOLD, getMaxToolLevel()), new Tuple<>(1, true));
        keepX.put(itemStack -> ItemStackUtils.hasToolLevel(itemStack, ToolType.SHOVEL, TOOL_LEVEL_WOOD_OR_GOLD, getMaxToolLevel()), new Tuple<>(1, true));
        keepX.put(itemStack -> ItemStackUtils.hasToolLevel(itemStack, ToolType.AXE, TOOL_LEVEL_WOOD_OR_GOLD, getMaxToolLevel()), new Tuple<>(1, true));

        keepX.put(itemStack -> ItemStackUtils.compareItemStacksIgnoreStackSize(itemStack, new ItemStack(getSetting(FILL_BLOCK).getValue())), new Tuple<>(STACKSIZE, true));
    }

    /**
     * Getter of the structure name.
     *
     * @return the structure name.
     */
    @NotNull
    @Override
    public String getSchematicName()
    {
        return MINER;
    }

    /**
     * Getter of the max building level.
     *
     * @return the integer.
     */
    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_BUILDING_LEVEL;
    }

    /**
     * The Miner wants to get multiple nodes/levels worth of stuff when requesting.
     */
    @Override
    public int getResourceBatchMultiplier() 
    {
        if (getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == ModJobs.quarrier).getAssignedCitizen().isEmpty())
        {
            //Ask for 10x the resources if possible
            return 10;
        }
        return 1;
    }

    @Override
    public void deserializeNBT(final CompoundTag compound)
    {
        super.deserializeNBT(compound);

        ladderLocation = BlockPosUtil.readOrNull(compound, TAG_LLOCATION);
        cobbleLocation = BlockPosUtil.readOrNull(compound, TAG_CLOCATION);
    }

    @Override
    public CompoundTag serializeNBT()
    {
        final CompoundTag compound = super.serializeNBT();

        BlockPosUtil.writeOptional(compound, TAG_CLOCATION, cobbleLocation);
        BlockPosUtil.writeOptional(compound, TAG_LLOCATION, ladderLocation);

        return compound;
    }

    /**
     * Returns the depth limit. Limitted by building level.
     * <pre>
     * - Level 1: 50
     * - Level 2: 20
     * - Level 3: 0
     * </pre>
     *
     * @return Depth limit.
     */
    public int getDepthLimit(final Level level)
    {
        if (this.getBuildingLevel() == 1)
        {
            return normalizeMaxDepth(MAX_DEPTH_LEVEL_1, level);
        }
        else if (this.getBuildingLevel() == 2)
        {
            return normalizeMaxDepth(MAX_DEPTH_LEVEL_2, level);
        }
        else if (this.getBuildingLevel() == 3)
        {
            return normalizeMaxDepth(MAX_DEPTH_MAX, level);
        }
        else
        {
            return normalizeMaxDepth(-100, level);
        }
    }

    /**
     * Normalize the maximum depth.
     * Make sure that the returned depth respects the world limits and follows the building setting..
     * @param max the max depth of the given building level.
     * @param level the world.
     * @return the max.
     */
    public int normalizeMaxDepth(final int max, final Level level)
    {
        final int worldMaxDepth = level.getMinBuildHeight() + 5;
        final IntSetting maxDepth = getSetting(MAX_DEPTH);
        if (maxDepth.getValue() == maxDepth.getDefault())
        {
            return Math.max(worldMaxDepth, max);
        }
        return Math.max(worldMaxDepth, Math.max(max, maxDepth.getValue()));
    }

    /**
     * Getter of the ladderLocation.
     *
     * @return the ladder location.
     */
    public BlockPos getLadderLocation()
    {
        if (ladderLocation == null)
        {
            loadLadderPos();
        }

        return ladderLocation;
    }

    /**
     * Getter of the cobbleLocation.
     *
     * @return the location.
     */
    public BlockPos getCobbleLocation()
    {
        if (cobbleLocation == null)
        {
            loadLadderPos();
        }

        return cobbleLocation;
    }

    private void loadLadderPos()
    {
        final Map<String, Set<BlockPos>> map = tileEntity.getWorldTagNamePosMap();
        final Set<BlockPos> cobblePos = map.getOrDefault("cobble", new HashSet<>());
        final Set<BlockPos> ladderPos = map.getOrDefault("ladder", new HashSet<>());
        if (cobblePos.isEmpty() || ladderPos.isEmpty())
        {
            return;
        }
        cobbleLocation = cobblePos.iterator().next();
        ladderLocation = ladderPos.iterator().next();
    }

    @Override
    public void searchWorkOrder()
    {
        final ICitizenData citizen = getFirstModuleOccurance(WorkerBuildingModule.class).getFirstCitizen();
        if (citizen == null)
        {
            return;
        }

        final List<WorkOrderBuildMiner> list = getColony().getWorkManager().getOrderedList(WorkOrderBuildMiner.class, getPosition());

        for (final WorkOrderBuildMiner wo : list)
        {
            if (this.getID().equals(wo.getMinerBuilding()))
            {
                citizen.getJob(JobMiner.class).setWorkOrder(wo);
                wo.setClaimedBy(citizen);
                return;
            }
        }
    }

    /**
     * Initiates structure loading.
     *
     * @param mineNode     the node to load it for.
     * @param rotateTimes  The amount of time to rotate the structure.
     * @param structurePos The position of the structure.
     */
    public static void initStructure(final Node mineNode, final int rotateTimes, final BlockPos structurePos, final BuildingMiner buildingMiner, final Level world, final JobMiner job)
    {
        final String style = buildingMiner.getStyle();
        String requiredName = null;
        int rotateCount = 0;

        if (mineNode == null)
        {
            rotateCount = getRotationFromVector(buildingMiner);
            requiredName = getCorrectStyleLocation(style, Node.NodeType.SHAFT.getSchematicName(), world, buildingMiner);
        }

        else
        {
            rotateCount = rotateTimes;
            requiredName = getCorrectStyleLocation(style, mineNode.getStyle().getSchematicName(), world, buildingMiner);
        }

        if (requiredName != null &&  (job == null || job.getWorkOrder() == null))
        {
            final WorkOrderBuildMiner wo = new WorkOrderBuildMiner(requiredName, requiredName, rotateCount, structurePos, false, buildingMiner.getPosition());
            wo.setClaimedBy(buildingMiner.getPosition());
            buildingMiner.getColony().getWorkManager().addWorkOrder(wo, false);
            if (job != null)
            {
                job.setWorkOrder(wo);
            }
            else
            {
                wo.setClaimedBy(buildingMiner.getPosition());
            }
        }
        buildingMiner.markDirty();
    }

    /**
     * Return number of rotation for our building, for the main shaft.
     *
     * @return the rotation.
     */
    private static int getRotationFromVector(final BuildingMiner buildingMiner)
    {
        final BlockPos vector = buildingMiner.getLadderLocation().subtract(buildingMiner.getCobbleLocation());

        if (vector.getX() == 1)
        {
            return 1;
        }
        else if (vector.getZ() == 1)
        {
            return 2;
        }
        else if (vector.getX() == -1)
        {
            return 3;
        }
        else if (vector.getZ() == -1)
        {
            return 4;
        }
        return 0;
    }

    /**
     * Get the correct style for the shaft. Return default back.
     *
     * @param style the style to check.
     * @param shaft the shaft.
     * @return the correct location.
     */
    private static String getCorrectStyleLocation(final String style, final String shaft, final Level world, final BuildingMiner buildingMiner)
    {
        final LoadOnlyStructureHandler
          wrapper = new LoadOnlyStructureHandler(world, buildingMiner.getPosition(), Structures.SCHEMATICS_PREFIX + "/" + style + shaft, new PlacementSettings(), true);
        if (wrapper.hasBluePrint())
        {
            return Structures.SCHEMATICS_PREFIX + "/" + style + shaft;
        }
        else
        {
            return Structures.SCHEMATICS_PREFIX + shaft;
        }
    }
}
