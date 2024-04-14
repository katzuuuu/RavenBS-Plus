package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.minigames.BedWars;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;

public class BedAura extends Module {
    private DescriptionSetting description;
    private SliderSetting range;
    private SliderSetting mode;
    private SliderSetting fov;
    public ButtonSetting allowAutoBlock;
    public ButtonSetting allowKillAura;
    private ButtonSetting breakBlockAbove;
    public ButtonSetting groundSpoof;
    private ButtonSetting renderOutline;
    private ButtonSetting silentSwing;
    private String[] modes = new String[]{"Legit", "Instant", "Swap"};
    private BlockPos[] bedPos;
    public float breakProgress;
    private int currentSlot = -1;
    private int lastSlot = -1;
    private boolean rotate;
    public BlockPos currentBlock;
    private int outlineColor = new Color(226, 65, 65).getRGB();

    public BedAura() {
        super("BedAura", category.player, 0);
        this.registerSetting(mode = new SliderSetting("Break mode", modes, 0));
        this.registerSetting(fov = new SliderSetting("FOV", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(range = new SliderSetting("Range", 4.5, 1.0, 8.0, 0.5));
        this.registerSetting(allowAutoBlock = new ButtonSetting("Allow autoblock", false));
        this.registerSetting(allowKillAura = new ButtonSetting("Allow killaura", true));
        this.registerSetting(breakBlockAbove = new ButtonSetting("Break block above", false));
        this.registerSetting(groundSpoof = new ButtonSetting("Ground spoof", false));
        this.registerSetting(renderOutline = new ButtonSetting("Render block outline", true));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
    }

    @Override
    public String getInfo() {
        return modes[(int) mode.getInput()];
    }

    @Override
    public void onDisable() {
        resetVariables();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (BedWars.whitelistOwnBed.isToggled() && BedWars.nearSpawn) {
            return;
        }
        if (!mc.thePlayer.capabilities.allowEdit || mc.thePlayer.isSpectator()) {
            return;
        }
        if (bedPos == null) {
            bedPos = getBedPos();
            if (bedPos == null) {
                return;
            }
        }
        else {
            if (!(BlockUtils.getBlock(bedPos[0]) instanceof BlockBed) || (currentBlock != null && replaceable(currentBlock))) {
                resetVariables();
                return;
            }
        }
        if (breakBlockAbove.isToggled() && !replaceable(bedPos[0].up())) {
            breakBlock(bedPos[0].up());
        }
        else {
            resetSlot();
            breakBlock(bedPos[0]);
        }
    }

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if ((rotate || breakProgress >= 1) && currentBlock != null) {
            float[] rotations = RotationUtils.getRotations(currentBlock, e.getYaw(), e.getPitch());
            e.setYaw(rotations[0]);
            e.setPitch(rotations[1]);
            rotate = false;
            if (groundSpoof.isToggled()) {
                e.setOnGround(true);
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent renderWorldLastEvent) {
        if (!renderOutline.isToggled() || currentBlock == null || !Utils.nullCheck()) {
            return;
        }
        RenderUtils.renderBlock(currentBlock, outlineColor, true, false);
    }

    private void resetSlot() {
        if (currentSlot != -1 && currentSlot != mc.thePlayer.inventory.currentItem && mode.getInput() == 2) {
            setPacketSlot(mc.thePlayer.inventory.currentItem);
        }
        else if (lastSlot != -1) {
            lastSlot = mc.thePlayer.inventory.currentItem = lastSlot;
        }
    }

    private BlockPos[] getBedPos() {
        int range;
        priority:
        for (int n = range = (int) this.range.getInput(); range >= -n; --range) {
            for (int j = -n; j <= n; ++j) {
                for (int k = -n; k <= n; ++k) {
                    final BlockPos blockPos = new BlockPos(mc.thePlayer.posX + j, mc.thePlayer.posY + range, mc.thePlayer.posZ + k);
                    final IBlockState getBlockState = mc.theWorld.getBlockState(blockPos);
                    if (getBlockState.getBlock() == Blocks.bed && getBlockState.getValue((IProperty) BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                        float fov = (float) this.fov.getInput();
                        if (fov != 360 && !Utils.inFov(n, blockPos)) {
                            continue priority;
                        }
                        return new BlockPos[]{blockPos, blockPos.offset((EnumFacing) getBlockState.getValue((IProperty) BlockBed.FACING))};
                    }
                }
            }
        }
        return null;
    }

    private void resetVariables() {
        if (currentBlock != null) {
            abortBreak(currentBlock);
        }
        resetSlot();
        bedPos = null;
        breakProgress = 0;
        currentSlot = -1;
        rotate = false;
        currentBlock = null;
        lastSlot = -1;
    }

    private boolean replaceable(BlockPos blockPos) {
        if (!Utils.nullCheck()) {
            return true;
        }
        return BlockUtils.getBlock(blockPos).isReplaceable(mc.theWorld, blockPos);
    }

    public void setPacketSlot(int slot) {
        if (slot == currentSlot || slot == -1) {
            return;
        }
        Utils.sendMessage(String.valueOf(slot));
        mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(slot));
        currentSlot = slot;
    }

    private void startBreak(BlockPos blockPos) {
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, blockPos, EnumFacing.UP));
    }

    private void stopBreak(BlockPos blockPos) {
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockPos, EnumFacing.UP));
    }

    private void abortBreak(BlockPos blockPos) {
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, blockPos, EnumFacing.UP));
    }

    private void swing() {
        if (!silentSwing.isToggled()) {
            mc.thePlayer.swingItem();
        }
        else {
            mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        }
    }

    private void breakBlock(BlockPos blockPos) {
        if (!RotationUtils.inRange(blockPos, range.getInput())) {
            return;
        }

        if (replaceable(currentBlock == null ? blockPos : currentBlock)) {
            resetVariables();
            return;
        }

        currentBlock = blockPos;
        Block block = BlockUtils.getBlock(blockPos);
        if (mode.getInput() == 2 || mode.getInput() == 0) {
            if (breakProgress == 0) {
                rotate = true;
                if (mode.getInput() == 0) {
                    setSlot(Utils.getTool(block));
                }
                swing();
                startBreak(blockPos);
            }
            else if (breakProgress >= 1) {
                if (mode.getInput() == 2) {
                    setPacketSlot(Utils.getTool(block));
                }
                swing();
                stopBreak(blockPos);
                mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.UP);
                resetVariables();
                return;
            }
            else {
                if (mode.getInput() == 0) {
                    rotate = true;
                    swing();
                }
            }
            breakProgress += BlockUtils.getBlockHardness(block, (mode.getInput() == 2 && Utils.getTool(block) != -1) ? mc.thePlayer.inventory.getStackInSlot(Utils.getTool(block)) : mc.thePlayer.getHeldItem(), false);
        }
        else if (mode.getInput() == 1) {
            rotate = true;
            swing();
            startBreak(blockPos);
            int slot = Utils.getTool(block);
            setSlot(slot);
            swing();
            stopBreak(blockPos);
            mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.UP);
        }
    }

    private void setSlot(int slot) {
        if (slot == -1 || slot == mc.thePlayer.inventory.currentItem) {
            return;
        }
        if (lastSlot == -1) {
            lastSlot = mc.thePlayer.inventory.currentItem;
        }
        mc.thePlayer.inventory.currentItem = slot;
    }
}
