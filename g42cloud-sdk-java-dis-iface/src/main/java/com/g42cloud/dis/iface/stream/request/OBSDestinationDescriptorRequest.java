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

package com.g42cloud.dis.iface.stream.request;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OBSDestinationDescriptorRequest
{
    /**
     * <p>
     * The name of the transfer task.
     * </p>
     */
    @JsonDeserialize(using = ForceStringDeserializer.class)
    @JsonProperty("task_name")
    private String transferTaskName;
    
    /**
     * <p>
     * Name of the agency created in IAM. DIS uses an agency to access your specified resources.For Example, DIS will
     * use this agency to dump data from a DIS stream to an OBS bucket.
     * </p>
     */
    @JsonDeserialize(using = ForceStringDeserializer.class)
    @JsonProperty("agency_name")
    private String agencyName;
    
    /**
     * <p>
     * Directory to hold files that will be dumped to OBS. Different directory levels are separated by a forward slash
     * (/) and cannot start with a forward slash (/). A directory name is 1 to 50 characters long. Only letters, digits,
     * and underscores (_) are allowed.
     * </p>
     */
    @JsonDeserialize(using = ForceStringDeserializer.class)
    @JsonProperty("file_prefix")
    private String filePrefix;
    
    /**
     * <p>
     * Directory structure of the Object file written into OBS. The directory structure is in the format of
     * yyyy/MM/dd/HH/mm (time at which the dump task was created).
     * </p>
     */
    @JsonDeserialize(using = ForceStringDeserializer.class)
    @JsonProperty("partition_format")
    private String partitionFormat;
    
    /**
     * <p>
     * Name of the OBS bucket used to store data from the DIS stream.
     * </p>
     */
    @JsonDeserialize(using = ForceStringDeserializer.class)
    @JsonProperty("obs_bucket_path")
    private String obsBucketPath;
    
    /**
     * <p>
     * User-defined interval at which data from the DIS stream is imported into OBS. If no data was pushed to the DIS
     * stream during the current interval, no dump file will be generated for this cycle. Value range: 60s to 900s
     * Default value: 300s
     * </p>
     */
    @JsonProperty("deliver_time_interval")
    private int deliverTimeInterval;
    
    /**
     * <p>
     * ?????????DIS?????????????????????????????????: ??????LATEST - ??????????????????????????????; TRIM_HORIZON - ??????????????????????????????
     * </p>
     */
    @JsonDeserialize(using = ForceStringDeserializer.class)
    @JsonProperty("consumer_strategy")
    private String consumerStrategy;
    
    /**
     * <p>
     * Type of the Object file written into OBS, such as text, parquet, carbon. Default value: text.
     * </p>
     */
    @JsonDeserialize(using = ForceStringDeserializer.class)
    @JsonProperty("destination_file_type")
    private String destinationFileType;
    
    /**
     * <p>
     * ????????????????????????????????????????????????????????????, gzip
     * </p>
     */
    @JsonProperty("compression_format")
    private String compressionFormat;

    /**
     * <p>
     * CarbonWriter.builder.withTableProperties(tablePropertiesMap)
     * </p>
     */
    @JsonProperty("carbon_properties")
    private Map<String, String> carbonProperties;
    
    /**
     * <p>
     * ???????????????schema??????:?????????parquet????????????timestamp??????????????????
     * </p>
     */
    @JsonProperty("processing_schema")
    private ProcessingSchema processingSchema;
    
    /**
     * <p>
     * Delimiter for the dump file, which is used to separate the user data that is written into the dump file. Possible
     * values:Comma ",", Semicolon ";", Vertical bar "|", Line break "\n", NULL. Default value: Line break "\n".
     * </p>
     */
    @JsonProperty("record_delimiter")
    private String recordDelimiter;
    
    public String getPartitionFormat()
    {
        return partitionFormat;
    }
    
    public void setPartitionFormat(String partitionFormat)
    {
        this.partitionFormat = partitionFormat;
    }
    
    public String getFilePrefix()
    {
        return filePrefix;
    }
    
    public void setFilePrefix(String filePrefix)
    {
        this.filePrefix = filePrefix;
    }
    
    public String getObsBucketPath()
    {
        return obsBucketPath;
    }
    
    public void setObsBucketPath(String obsBucketPath)
    {
        this.obsBucketPath = obsBucketPath;
    }
    
    public int getDeliverTimeInterval()
    {
        return deliverTimeInterval;
    }
    
    public void setDeliverTimeInterval(int deliverTimeInterval)
    {
        this.deliverTimeInterval = deliverTimeInterval;
    }

    public String getAgencyName()
    {
        return agencyName;
    }
    
    public void setAgencyName(String agencyName)
    {
        this.agencyName = agencyName;
    }
    
    public String getTransferTaskName()
    {
        return transferTaskName;
    }
    
    public void setTransferTaskName(String transferTaskName)
    {
        this.transferTaskName = transferTaskName;
    }
    
    public String getDestinationFileType()
    {
        return destinationFileType;
    }
    
    public void setDestinationFileType(String destinationFileType)
    {
        this.destinationFileType = destinationFileType;
    }
    
    public String getRecordDelimiter()
    {
        return recordDelimiter;
    }
    
    public void setRecordDelimiter(String recordDelimiter)
    {
        this.recordDelimiter = recordDelimiter;
    }
    
    public String getConsumerStrategy()
    {
        return consumerStrategy;
    }
    
    public void setConsumerStrategy(String consumerStrategy)
    {
        this.consumerStrategy = consumerStrategy;
    }
    
    public Map<String, String> getCarbonProperties()
    {
        return carbonProperties;
    }
    
    public void setCarbonProperties(Map<String, String> carbonProperties)
    {
        this.carbonProperties = carbonProperties;
    }
    
    public ProcessingSchema getProcessingSchema()
    {
        return processingSchema;
    }
    
    public void setProcessingSchema(ProcessingSchema processingSchema)
    {
        this.processingSchema = processingSchema;
    }
    
    public String getCompressionFormat()
    {
        return compressionFormat;
    }
    
    public void setCompressionFormat(String compressionFormat)
    {
        this.compressionFormat = compressionFormat;
    }
}
