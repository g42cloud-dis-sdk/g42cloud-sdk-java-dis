/*
 * Copyright 2002-2010 the original author or authors.
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

package com.g42cloud.dis.core.restresource;

import com.g42cloud.dis.core.util.StringUtils;

public class AppsResource extends RestResource {
    private static final String DEFAULT_RESOURCE_NAME = "apps";

    private String resourceName;

    private String resourceId;

    private String action;

    public AppsResource(String resourceId)
    {
        this(null, resourceId);
    }

    public AppsResource(String resourceName, String resourceId)
    {
        this(resourceName, resourceId, null);
    }

    public AppsResource(String resourceName, String resourceId, String action)
    {
        this.resourceName = resourceName;
        this.resourceId = resourceId;
        this.action = action;
    }

    @Override
    public String getResourceName()
    {
        if (StringUtils.isNullOrEmpty(resourceName))
        {
            return DEFAULT_RESOURCE_NAME;
        }
        return resourceName;
    }

    @Override
    public String getResourceId()
    {
        return resourceId;
    }

    @Override
    public String getAction() { return action; }
}
