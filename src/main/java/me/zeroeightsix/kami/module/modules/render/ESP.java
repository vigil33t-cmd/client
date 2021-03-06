package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.event.events.RenderEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.EntityUtil;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.opengl.GL11.*;

/**
 * Created by 086 on 14/12/2017.
 * Updated by d1gress/Qther on 27/11/2019.
 * Kurisu Makise is cute
 * Updated by dominikaaaa on 19/04/20
 */
@Module.Info(
        name = "ESP",
        category = Module.Category.RENDER,
        description = "Highlights entities"
)
public class ESP extends Module {
    private Setting<ESPMode> mode = register(Settings.e("Mode", ESPMode.RECTANGLE));
    private Setting<Integer> radiusValue = register(Settings.integerBuilder("Width").withMinimum(1).withMaximum(100).withValue(25).withVisibility(v -> mode.getValue().equals(ESPMode.GLOW)).build());
    private Setting<Boolean> players = register(Settings.b("Players", true));
    private Setting<Boolean> mobs = register(Settings.b("Mobs", true));
    private Setting<Boolean> passive = register(Settings.booleanBuilder("Passive Mobs").withValue(false).withVisibility(v -> mobs.getValue()).build());
    private Setting<Boolean> neutral = register(Settings.booleanBuilder("Neutral Mobs").withValue(true).withVisibility(v -> mobs.getValue()).build());
    private Setting<Boolean> hostile = register(Settings.booleanBuilder("Hostile Mobs").withValue(true).withVisibility(v -> mobs.getValue()).build());
    private Setting<Boolean> renderInvis = register(Settings.b("Invisible", false));

    public enum ESPMode {
        RECTANGLE, GLOW
    }

    private boolean removeGlow = false;

    @Override
    public void onWorldRender(RenderEvent event) {


        if (Wrapper.getMinecraft().getRenderManager().options == null) return;
        switch (mode.getValue()) {
            case RECTANGLE:
                boolean isThirdPersonFrontal = Wrapper.getMinecraft().getRenderManager().options.thirdPersonView == 2;
                float viewerYaw = Wrapper.getMinecraft().getRenderManager().playerViewY;

                mc.world.loadedEntityList.stream()
                        .filter(EntityUtil::isLiving)
                        .filter(entity -> {
                            if (entity.isInvisible()) {
                                return renderInvis.getValue();
                            }
                            return true;
                        })
                        .filter(entity -> mc.player != entity)
                        .map(entity -> (EntityLivingBase) entity)
                        .filter(entityLivingBase -> !entityLivingBase.isDead)
                        .filter(entity -> (players.getValue() && entity instanceof EntityPlayer) || (EntityUtil.mobTypeSettings(entity, mobs.getValue(), passive.getValue(), neutral.getValue(), hostile.getValue())))
                        .forEach(e -> {
                            GlStateManager.pushMatrix();
                            Vec3d pos = EntityUtil.getInterpolatedPos(e, event.getPartialTicks());
                            GlStateManager.translate(pos.x - mc.getRenderManager().renderPosX, pos.y - mc.getRenderManager().renderPosY, pos.z - mc.getRenderManager().renderPosZ);
                            GlStateManager.glNormal3f(0.0F, 1.0F, 0.0F);
                            GlStateManager.rotate(-viewerYaw, 0.0F, 1.0F, 0.0F);
                            GlStateManager.rotate((float) (isThirdPersonFrontal ? -1 : 1), 1.0F, 0.0F, 0.0F);
                            GlStateManager.disableLighting();
                            GlStateManager.depthMask(false);

                            GlStateManager.disableDepth();

                            GlStateManager.enableBlend();
                            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

                            if (e instanceof EntityPlayer) glColor3f(1, 1, 1);
                            else if (EntityUtil.isPassiveMob(e)) glColor3f(0.11f, 0.9f, 0.11f);
                            else glColor3f(0.9f, .1f, .1f);

                            GlStateManager.disableTexture2D();
                            glLineWidth(2f);
                            glEnable(GL_LINE_SMOOTH);
                            glBegin(GL_LINE_LOOP);
                            {
                                glVertex2d(-e.width / 2, 0);
                                glVertex2d(-e.width / 2, e.height);
                                glVertex2d(e.width / 2, e.height);
                                glVertex2d(e.width / 2, 0);
                            }
                            glEnd();

                            GlStateManager.popMatrix();
                        });
                GlStateManager.enableDepth();
                GlStateManager.depthMask(true);
                GlStateManager.disableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.disableAlpha();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                GlStateManager.shadeModel(GL11.GL_SMOOTH);
                GlStateManager.disableDepth();
                GlStateManager.enableCull();
                GlStateManager.glLineWidth(1);
                glColor3f(1, 1, 1);
                break;
            default:
                break;
        }
    }

    @Override
    public void onUpdate() {
        if (isDisabled()) return; // make sure to reset the shader stuff properly, so don't change radius while disabling
        if (mode.getValue().equals(ESPMode.GLOW)) {
            removeGlow = true;
            mc.renderGlobal.entityOutlineShader.listShaders.forEach(shader -> {
                ShaderUniform radius = shader.getShaderManager().getShaderUniform("Radius");
                if (radius != null)
                    radius.set(radiusValue.getValue() / 50f);
            });
            for (Entity e : mc.world.loadedEntityList) {
                if (e == null || e.isDead) return;
                if (e instanceof EntityPlayer && players.getValue()) {
                    e.setGlowing(true);
                } else if (e instanceof EntityPlayer && !players.getValue()) {
                    e.setGlowing(false);
                }
                e.setGlowing(EntityUtil.mobTypeSettings(e, mobs.getValue(), passive.getValue(), neutral.getValue(), hostile.getValue()));
            }
        } else if (removeGlow && !mode.getValue().equals(ESPMode.GLOW)) {
            for (Entity e : mc.world.loadedEntityList) {
                e.setGlowing(false);
            }
            removeGlow = false;
            mc.player.setGlowing(false);
        }
    }

    @Override
    public void onDisable() {
        if (mode.getValue().equals(ESPMode.GLOW)) {
            for (Entity e : mc.world.loadedEntityList) {
                e.setGlowing(false);
            }
            mc.player.setGlowing(false);
        }
        mc.renderGlobal.entityOutlineShader.listShaders.forEach(shader -> {
            ShaderUniform radius = shader.getShaderManager().getShaderUniform("Radius");
            if (radius != null)
                radius.set(2f); // default radius
        });
    }
}
