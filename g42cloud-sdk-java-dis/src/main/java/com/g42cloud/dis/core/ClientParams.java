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

package com.g42cloud.dis.core;

import com.g42cloud.dis.core.auth.credentials.Credentials;

/**
 * 提供获取云服务公共参数的方法
 */
public interface ClientParams
{
    
    public abstract Credentials getCredential();
    
    public abstract String getRegion();
    
    public abstract String getProjectId();
    
}
