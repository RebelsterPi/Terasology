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
package org.terasology.rendering.dag;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.assets.ResourceUrn;
import org.terasology.registry.In;
import org.terasology.rendering.opengl.BaseFBM;
import org.terasology.rendering.opengl.DefaultDynamicFBOs;
import org.terasology.rendering.opengl.FBOConfig;
import org.terasology.rendering.opengl.fbms.DynamicFBM;
import org.terasology.rendering.opengl.fbms.StaticFBM;

/**
 * TODO: Add javadocs
 */
public abstract class AbstractNode implements Node {
    protected static final Logger logger = LoggerFactory.getLogger(AbstractNode.class);

    @In
    private StaticFBM staticFBM;

    @In
    private DynamicFBM dynamicFBM;

    private Set<StateChange> desiredStateChanges;
    private Map<ResourceUrn, BaseFBM> fboUsages;

    private NodeTask task;
    private RenderTaskListGenerator taskListGenerator;

    protected AbstractNode() {
        desiredStateChanges = Sets.newLinkedHashSet();
        fboUsages = Maps.newHashMap();
    }

    protected void requireStaticFBO(FBOConfig fboConfig) {
        requireFBO(fboConfig, staticFBM);
    }

    protected void requireDynamicFBO(FBOConfig fboConfig) {
        requireFBO(fboConfig, dynamicFBM);
    }

    protected void requireFBO(FBOConfig fboConfig, BaseFBM frameBuffersManager) {
        ResourceUrn fboName = fboConfig.getName();

        if (!fboUsages.containsKey(fboName)) {
            fboUsages.put(fboName, frameBuffersManager);
        } else {
            logger.warn("FBO " + fboName + " is already requested.");
            return;
        }

        frameBuffersManager.request(fboConfig);
    }

    protected void requireFBO(DefaultDynamicFBOs defaultDynamicFBO) {
        requireFBO(defaultDynamicFBO.getFboConfig(), dynamicFBM);
    }

    @Override
    public void dispose() {
        for (Map.Entry<ResourceUrn, BaseFBM> entry : fboUsages.entrySet()) {
            ResourceUrn fboName = entry.getKey();
            BaseFBM baseFBM = entry.getValue();
            baseFBM.release(fboName);
        }

        fboUsages.clear();
    }

    protected boolean addDesiredStateChange(StateChange stateChange) {
        return desiredStateChanges.add(stateChange);
    }

    protected boolean removeDesiredStateChange(StateChange stateChange) {
        return desiredStateChanges.remove(stateChange);
    }

    protected void refreshTaskList() {
        taskListGenerator.refresh();
    }

    public Set<StateChange> getDesiredStateChanges() {
        return desiredStateChanges;
    }

    public RenderPipelineTask generateTask() {
        if (task == null) {
            task = new NodeTask(this);
        }
        return task;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    public void setTaskListGenerator(RenderTaskListGenerator taskListGenerator) {
        this.taskListGenerator = taskListGenerator;
    }
}
