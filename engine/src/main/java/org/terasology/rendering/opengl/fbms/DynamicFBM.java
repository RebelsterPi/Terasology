/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.opengl.fbms;

import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.terasology.assets.ResourceUrn;
import org.terasology.config.Config;
import org.terasology.config.RenderingConfig;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.oculusVr.OculusVrHelper;
import org.terasology.rendering.opengl.AbstractFBM;
import org.terasology.rendering.opengl.DefaultDynamicFBOs;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.Final;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.ReadOnlyGBuffer;
import static org.terasology.rendering.opengl.DefaultDynamicFBOs.WriteOnlyGBuffer;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.FBOConfig;
import org.terasology.rendering.opengl.PostProcessor;

/**
 * TODO: Add javadocs
 * TODO: Better naming
 */
public class DynamicFBM extends AbstractFBM {
    // I could have named them fullResolution, halfResolution and so on. But halfScale is actually
    // -both- fullScale's dimensions halved, leading to -a quarter- of its resolution. Following
    // this logic one32thScale would have to be named one1024thResolution and the otherwise
    // straightforward connection between variable names and dimensions would have been lost. -- manu3d
    private FBO.Dimensions fullScale;

    private Config config = CoreRegistry.get(Config.class);
    private RenderingConfig renderingConfig = config.getRendering();
    private PostProcessor postProcessor;

    public DynamicFBM() {
        fullScale = new FBO.Dimensions(Display.getWidth(), Display.getHeight());
        generateDefaultFBOs();
    }

    @Override
    public void request(FBOConfig fboConfig) {
        ResourceUrn fboName = fboConfig.getName();
        if (fboConfigs.containsKey(fboName)) {
            if (!fboConfig.equals(fboConfigs.get(fboName))) {
                throw new IllegalArgumentException("Requested FBO is already available with different configuration");
            }
        } else {
            generate(fboConfig, fullScale.multiplyBy(fboConfig.getScale()));
        }
        retain(fboName);
    }

    private void generateDefaultFBOs() {
        generateDefaultFBO(ReadOnlyGBuffer);
        generateDefaultFBO(WriteOnlyGBuffer);
        generateDefaultFBO(Final);
    }

    private void generateDefaultFBO(DefaultDynamicFBOs defaultDynamicFBO) {
        FBOConfig fboConfig = defaultDynamicFBO.getFboConfig();
        generate(fboConfig, fullScale.multiplyBy(fboConfig.getScale()));
    }

    /**
     * Invoked before real-rendering starts
     * TODO: how about completely removing this, and make Display observable and this FBM as an observer
     */
    public void update() {
        updateFullScale();
        if (getFBO(ReadOnlyGBuffer.getResourceUrn()).dimensions().areDifferentFrom(fullScale)) {
            disposeAllFBOs();
            createFBOs();
        }
    }

    private void disposeAllFBOs() {
        for (ResourceUrn urn : fboConfigs.keySet()) {
            FBO fbo = fboLookup.get(urn);
            fbo.dispose();
            fboLookup.remove(urn);
        }
        fboLookup.clear();
    }

    private void createFBOs() {
        for (FBOConfig fboConfig : fboConfigs.values()) {
            generate(fboConfig, fullScale.multiplyBy(fboConfig.getScale()));
        }

        notifySubscribers();
    }

    /**
     * Returns the content of the color buffer of the FBO "sceneFinal", from GPU memory as a ByteBuffer.
     * If the FBO "sceneFinal" is unavailable, returns null.
     *
     * @return a ByteBuffer or null
     */
    public ByteBuffer getSceneFinalRawData() {
        FBO fboSceneFinal = getFBO(ReadOnlyGBuffer.getResourceUrn());
        if (fboSceneFinal == null) {
            logger.error("FBO sceneFinal is unavailable: cannot return data from it.");
            return null;
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(fboSceneFinal.width() * fboSceneFinal.height() * 4);

        fboSceneFinal.bindTexture();
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        FBO.unbindTexture();

        return buffer;
    }

    private void updateFullScale() {
        if (postProcessor.isNotTakingScreenshot()) {
            fullScale = new FBO.Dimensions(Display.getWidth(), Display.getHeight());
            if (renderingConfig.isOculusVrSupport()) {
                fullScale.multiplySelfBy(OculusVrHelper.getScaleFactor());
            }
        } else {
            fullScale = new FBO.Dimensions(
                    renderingConfig.getScreenshotSize().getWidth(Display.getWidth()),
                    renderingConfig.getScreenshotSize().getHeight(Display.getHeight())
            );
        }

        fullScale.multiplySelfBy(renderingConfig.getFboScale() / 100f);
    }

    /**
     * Sets an internal reference to the PostProcessor instance. This reference is used to inform the PostProcessor
     * instance that changes have occurred and that it should refresh its references to the FBOs it uses.
     *
     * @param postProcessor a PostProcessor instance
     */
    public void setPostProcessor(PostProcessor postProcessor) {
        this.postProcessor = postProcessor;
    }


    /**
     * TODO: add javadocs
     *
     * @param resourceUrn
     * @param resourceUrn1
     */
    public void swap(ResourceUrn resourceUrn, ResourceUrn resourceUrn1) {
        FBO fbo = fboLookup.get(resourceUrn1);
        fboLookup.put(resourceUrn1, fboLookup.get(resourceUrn));
        fboLookup.put(resourceUrn, fbo);
    }
}
