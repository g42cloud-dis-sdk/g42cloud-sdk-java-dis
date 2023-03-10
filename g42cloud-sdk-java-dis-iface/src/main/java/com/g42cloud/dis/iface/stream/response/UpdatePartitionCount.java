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

package com.g42cloud.dis.iface.stream.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdatePartitionCount
{
    /**
     * <p>
     * 扩缩容操作执行时间戳。
     * </p>
     */
    @JsonProperty("create_timestamp")
    private long createTimestamp;

    /**
     * <p>
     * 扩缩容操作前分区数量。
     * </p>
     */
    @JsonProperty("src_partition_count")
    private int srcPartitionCount;
    
    /**
     * <p>
     * 扩缩容操作后分区数量。
     * </p>
     */
    @JsonProperty("target_partition_count")
    private int targetPartitionCount;

    /**
     * <p>
     * 扩缩容操作响应码。
     * </p>
     */
    @JsonProperty("result_code")
    private int resultCode;

    /**
     * <p>
     * 扩缩容操作响应信息。
     * </p>
     */
    @JsonProperty("result_msg")
    private String resultMsg;

    /**
     * <p>
     * 本次扩缩容操作是否为自动扩缩容。
     * </p>
     */
    @JsonProperty("auto_scale")
    private boolean autoScale;
    
    public boolean isAutoScale()
    {
        return autoScale;
    }
    public void setAutoScale(boolean autoScale)
    {
        this.autoScale = autoScale;
    }
    public long getCreateTimestamp()
    {
        return createTimestamp;
    }
    public void setCreateTimestamp(long createTimestamp)
    {
        this.createTimestamp = createTimestamp;
    }
    public int getSrcPartitionCount()
    {
        return srcPartitionCount;
    }
    public void setSrcPartitionCount(int srcPartitionCount)
    {
        this.srcPartitionCount = srcPartitionCount;
    }
    public int getTargetPartitionCount()
    {
        return targetPartitionCount;
    }
    public void setTargetPartitionCount(int targetPartitionCount)
    {
        this.targetPartitionCount = targetPartitionCount;
    }
    public int getResultCode()
    {
        return resultCode;
    }
    public void setResultCode(int resultCode)
    {
        this.resultCode = resultCode;
    }

    public String getResultMsg()
    {
        return resultMsg;
    }
    public void setResultMsg(String resultMsg)
    {
        this.resultMsg = resultMsg;
    }
    @Override
    public String toString()
    {
        return "UpdatePartitionCount [createTimestamp=" + createTimestamp + ", srcPartitionCount=" + srcPartitionCount
            + ", targetPartitionCount=" + targetPartitionCount + ", resultCode=" + resultCode + ", resultMsg="
            + resultMsg + ", autoScale=" + autoScale + "]";
    }

    
    
}
