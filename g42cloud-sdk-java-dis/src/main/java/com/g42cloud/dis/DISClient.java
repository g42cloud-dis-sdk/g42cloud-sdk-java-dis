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

package com.g42cloud.dis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.g42cloud.dis.core.DISCredentials;
import com.g42cloud.dis.core.DefaultRequest;
import com.g42cloud.dis.core.Request;
import com.g42cloud.dis.core.restresource.*;
import com.g42cloud.dis.iface.api.protobuf.Message;
import com.g42cloud.dis.iface.app.response.DescribeAppResult;
import com.g42cloud.dis.iface.app.response.ListAppsResult;
import com.g42cloud.dis.iface.app.response.ListStreamConsumingStateResult;
import com.g42cloud.dis.iface.data.request.*;
import com.g42cloud.dis.iface.data.response.*;
import com.g42cloud.dis.iface.stream.request.*;
import com.g42cloud.dis.iface.stream.response.*;
import com.g42cloud.dis.iface.transfertask.request.*;
import com.g42cloud.dis.iface.transfertask.response.*;
import org.apache.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.g42cloud.dis.DISConfig.BodySerializeType;
import com.g42cloud.dis.core.http.HttpMethodName;
import com.g42cloud.dis.core.restresource.*;
import com.g42cloud.dis.core.util.StringUtils;
import com.g42cloud.dis.http.AbstractDISClient;
import com.g42cloud.dis.http.exception.HttpClientErrorException;
import com.g42cloud.dis.iface.api.protobuf.ProtobufUtils;
import com.g42cloud.dis.iface.app.request.CreateAppRequest;
import com.g42cloud.dis.iface.app.request.ListAppsRequest;
import com.g42cloud.dis.iface.app.request.ListStreamConsumingStateRequest;
import com.g42cloud.dis.iface.data.request.*;
import com.g42cloud.dis.iface.data.response.*;
import com.g42cloud.dis.iface.stream.request.*;
import com.g42cloud.dis.iface.stream.response.*;
import com.g42cloud.dis.iface.transfertask.request.*;
import com.g42cloud.dis.iface.transfertask.response.*;
import com.g42cloud.dis.util.ExponentialBackOff;
import com.g42cloud.dis.util.Utils;
import com.g42cloud.dis.util.cache.CacheResenderThread;
import com.g42cloud.dis.util.cache.CacheUtils;

public class DISClient extends AbstractDISClient implements DIS {
    private static final Logger LOG = LoggerFactory.getLogger(DISClient.class);

    protected ReentrantLock recordsRetryLock = new ReentrantLock();

    private CacheResenderThread cacheResenderThread;

    public DISClient(DISConfig disConfig) {
        super(disConfig);
    }

    /**
     * @deprecated use {@link DISClientBuilder#defaultClient()}
     */
    public DISClient() {
        super();
    }

    @Override
    public PutRecordsResult putRecords(PutRecordsRequest putRecordsParam) {
        return innerPutRecordsSupportingCache(putRecordsParam);
    }

    protected PutRecordsResult innerPutRecordsSupportingCache(PutRecordsRequest putRecordsParam) {
        if (disConfig.isDataCacheEnabled()) {
            // ??????????????????
            PutRecordsResult putRecordsResult = null;
            try {
                // ???????????????????????????????????????????????????????????????
                if (this.cacheResenderThread == null) {
                    cacheResenderThread = new CacheResenderThread("DisClient", disConfig);
                    cacheResenderThread.start();
                }

                putRecordsResult = innerPutRecordsWithRetry(putRecordsParam);
                // ????????????????????????
                if (putRecordsResult.getFailedRecordCount().get() > 0) {
                    // ??????????????????????????????
                    List<PutRecordsResultEntry> putRecordsResultEntries = putRecordsResult.getRecords();
                    List<PutRecordsRequestEntry> failedPutRecordsRequestEntries = new ArrayList<>();
                    int index = 0;
                    for (PutRecordsResultEntry putRecordsResultEntry : putRecordsResultEntries) {
                        if (!StringUtils.isNullOrEmpty(putRecordsResultEntry.getErrorCode())) {
                            failedPutRecordsRequestEntries.add(putRecordsParam.getRecords().get(index));
                        }
                        index++;
                    }
                    putRecordsParam.setRecords(failedPutRecordsRequestEntries);

                    LOG.info("Local data cache is enabled, try to put failed records to local.");

                    CacheUtils.putToCache(putRecordsParam, disConfig); // ??????????????????
                }
            } catch (Exception e) {
                if (!(e.getCause() instanceof HttpClientErrorException)) {
                    // ????????????
                    LOG.info("Local data cache is enabled, try to put failed records to local.");

                    CacheUtils.putToCache(putRecordsParam, disConfig); // ??????????????????
                }
                throw e;
            }
            return putRecordsResult;
        } else {
            return innerPutRecordsWithRetry(putRecordsParam);
        }
    }

    protected PutRecordsResult innerPutRecordsWithRetry(PutRecordsRequest putRecordsParam) {
        PutRecordsResult putRecordsResult = null;
        PutRecordsResultEntry[] putRecordsResultEntryList = null;
        Integer[] retryIndex = null;
        PutRecordsRequest retryPutRecordsRequest = putRecordsParam;

        int retryCount = -1;
        int currentFailed = 0;
        int noRetryRecordsCount = 0;
        ExponentialBackOff backOff = null;
        try {
            do {
                retryCount++;
                if (retryCount > 0) {
                    // ?????????????????????????????????
                    if (backOff == null) {
                        recordsRetryLock.lock();
                        LOG.trace("Put records retry lock.");
                        backOff = new ExponentialBackOff(ExponentialBackOff.DEFAULT_INITIAL_INTERVAL,
                                ExponentialBackOff.DEFAULT_MULTIPLIER, disConfig.getBackOffMaxIntervalMs(),
                                ExponentialBackOff.DEFAULT_MAX_ELAPSED_TIME);
                    }

                    if (putRecordsResult != null && currentFailed != putRecordsResult.getRecords().size()) {
                        // ?????????????????????????????????
                        backOff.resetCurrentInterval();
                    }

                    long sleepMs = backOff.getNextBackOff();

                    if (retryPutRecordsRequest.getRecords().size() > 0) {
                        LOG.debug(
                                "Put {} records but {} failed, will re-try after backoff {} ms, current retry count is {}.",
                                putRecordsResult != null ? putRecordsResult.getRecords().size()
                                        : putRecordsParam.getRecords().size(),
                                currentFailed,
                                sleepMs,
                                retryCount);
                    }

                    backOff.backOff(sleepMs);
                }

                try {
                    putRecordsResult = innerPutRecords(retryPutRecordsRequest);
                } catch (Throwable t) {
                    if (putRecordsResultEntryList != null) {
                        LOG.error(t.getMessage(), t);
                        break;
                    }
                    throw t;
                }

                if (putRecordsResult != null) {
                    currentFailed = putRecordsResult.getFailedRecordCount().get();

                    if (putRecordsResultEntryList == null && currentFailed == 0 || disConfig.getRecordsRetries() == 0) {
                        // ????????????????????????????????????????????????????????????????????????
                        return putRecordsResult;
                    }

                    if (putRecordsResultEntryList == null) {
                        // ????????????????????????????????????????????????????????????????????????????????????????????????
                        putRecordsResultEntryList = new PutRecordsResultEntry[putRecordsParam.getRecords().size()];
                    }

                    // ???????????????????????????????????????
                    List<Integer> retryIndexTemp = new ArrayList<>(currentFailed);

                    if (currentFailed > 0) {
                        // ????????????????????????????????????
                        retryPutRecordsRequest = new PutRecordsRequest();
                        retryPutRecordsRequest.setStreamName(putRecordsParam.getStreamName());
                        retryPutRecordsRequest.setStreamId(putRecordsParam.getStreamId());
                        retryPutRecordsRequest.setRecords(new ArrayList<>(currentFailed));
                    }

                    // ??????????????????????????????????????????
                    for (int i = 0; i < putRecordsResult.getRecords().size(); i++) {
                        // ???????????????????????????????????????????????????
                        int originalIndex = retryIndex == null ? i : retryIndex[i];
                        PutRecordsResultEntry putRecordsResultEntry = putRecordsResult.getRecords().get(i);

                        if (!StringUtils.isNullOrEmpty(putRecordsResultEntry.getErrorCode())) {
                            // ??????????????????(?????????????????????????????????)????????????
                            if (isRecordsRetriableErrorCode(putRecordsResultEntry.getErrorCode())) {
                                retryIndexTemp.add(originalIndex);
                                retryPutRecordsRequest.getRecords().add(putRecordsParam.getRecords().get(originalIndex));
                            } else {
                                noRetryRecordsCount++;
                            }
                        }
                        putRecordsResultEntryList[originalIndex] = putRecordsResultEntry;
                    }
                    retryIndex = retryIndexTemp.size() > 0 ? retryIndexTemp.toArray(new Integer[retryIndexTemp.size()])
                            : new Integer[0];
                }
            } while ((retryIndex == null || retryIndex.length > 0) && retryCount < disConfig.getRecordsRetries());
        } finally {
            if (retryCount > 0) {
                recordsRetryLock.unlock();
                LOG.trace("Put records retry unlock.");
            }
        }
        putRecordsResult = new PutRecordsResult();
        if (retryIndex == null) {
            // ????????????????????????????????????????????????????????????????????????
            putRecordsResult.setFailedRecordCount(new AtomicInteger(putRecordsParam.getRecords().size()));
        } else {
            putRecordsResult.setFailedRecordCount(new AtomicInteger(retryIndex.length + noRetryRecordsCount));
            putRecordsResult.setRecords(Arrays.asList(putRecordsResultEntryList));
        }

        return putRecordsResult;
    }

    /*
     * Internal API
     */
    protected final PutRecordsResult innerPutRecords(PutRecordsRequest putRecordsParam) {
        // Decorate PutRecordsRequest if needed
        putRecordsParam = decorateRecords(putRecordsParam);

        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.POST);

        final String resourcePath =
                ResourcePathBuilder.standard()
                        .withProjectId(disConfig.getProjectId())
                        .withResource(new RecordResource(null))
                        .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());
        if (BodySerializeType.protobuf.equals(disConfig.getBodySerializeType())) {
            request.addHeader("Content-Type", "application/x-protobuf; charset=utf-8");

            Message.PutRecordsRequest protoRequest = ProtobufUtils.toProtobufPutRecordsRequest(putRecordsParam);

            Message.PutRecordsResult putRecordsResult = request(protoRequest.toByteArray(), request, Message.PutRecordsResult.class);

            PutRecordsResult result = ProtobufUtils.toPutRecordsResult(putRecordsResult);

            return result;

        } else {
            return request(putRecordsParam, request, PutRecordsResult.class);
        }
    }


    @Override
    public GetPartitionCursorResult getPartitionCursor(GetPartitionCursorRequest getPartitionCursorParam) {
        return innerGetPartitionCursor(getPartitionCursorParam);
    }

    /*
     * Internal API
     */
    protected final GetPartitionCursorResult innerGetPartitionCursor(GetPartitionCursorRequest getPartitionCursorParam) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath =
                ResourcePathBuilder.standard()
                        .withProjectId(disConfig.getProjectId())
                        .withResource(new CursorResource(null))
                        .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());

        return request(getPartitionCursorParam, request, GetPartitionCursorResult.class);
    }

    @Override
    public GetRecordsResult getRecords(GetRecordsRequest getRecordsParam) {
        return innerGetRecords(getRecordsParam);
    }

    /*
     * Internal API
     */
    protected final GetRecordsResult innerGetRecords(GetRecordsRequest getRecordsParam) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath =
                ResourcePathBuilder.standard()
                        .withProjectId(disConfig.getProjectId())
                        .withResource(new RecordResource(null))
                        .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());

        GetRecordsResult result;

        if (BodySerializeType.protobuf.equals(disConfig.getBodySerializeType())) {
            request.addHeader("Content-Type", "application/x-protobuf; charset=utf-8");

            Message.GetRecordsResult protoResult = request(getRecordsParam, request, Message.GetRecordsResult.class);
            result = ProtobufUtils.toGetRecordsResult(protoResult);
        } else {
            result = request(getRecordsParam, request, GetRecordsResult.class);
        }

        return decorateRecords(result);
    }

    // ###################### delegate IStreamService #########################
    @Override
    public CreateStreamResult createStream(CreateStreamRequest createStreamRequest) {
        return innerCreateStream(createStreamRequest);
    }

    protected final CreateStreamResult innerCreateStream(CreateStreamRequest createStreamRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.POST);

        final String resourcePath =
                ResourcePathBuilder.standard()
                        .withProjectId(disConfig.getProjectId())
                        .withResource(new StreamResource(null, null))
                        .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        CreateStreamResult result = request(createStreamRequest, request, CreateStreamResult.class);

        return result;
    }

    @Override
    public DescribeStreamResult describeStream(DescribeStreamRequest describeStreamRequest) {
        return innerDescribeStream(describeStreamRequest);
    }

    /*
     * Internal API
     */
    protected final DescribeStreamResult innerDescribeStream(DescribeStreamRequest describeStreamRequest) {
        // change to shardId format
        describeStreamRequest.setStartPartitionId(Utils.getShardIdFromPartitionId(describeStreamRequest.getStartPartitionId()));

        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath =
                ResourcePathBuilder.standard()
                        .withProjectId(disConfig.getProjectId())
                        .withResource(new StreamResource(null, describeStreamRequest.getStreamName()))
                        .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        DescribeStreamResult result = request(describeStreamRequest, request, DescribeStreamResult.class);

        return result;
    }

    @Override
    public UpdatePartitionCountResult updatePartitionCount(UpdatePartitionCountRequest updatePartitionCountRequest) {
        return innerUpdatePartitionCount(updatePartitionCountRequest);
    }

    protected final UpdatePartitionCountResult innerUpdatePartitionCount(
            UpdatePartitionCountRequest updatePartitionCountRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.PUT);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(null, updatePartitionCountRequest.getStreamName()))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());
        return request(updatePartitionCountRequest, request, UpdatePartitionCountResult.class);
    }

    @Override
    public DeleteStreamResult deleteStream(DeleteStreamRequest deleteStreamRequest) {
        return innerDeleteStream(deleteStreamRequest);
    }

    protected final DeleteStreamResult innerDeleteStream(DeleteStreamRequest deleteStreamRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.DELETE);

        final String resourcePath =
                ResourcePathBuilder.standard()
                        .withProjectId(disConfig.getProjectId())
                        .withResource(new StreamResource(null, deleteStreamRequest.getStreamName()))
                        .build();

        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        DeleteStreamResult result = request(deleteStreamRequest, request, DeleteStreamResult.class);
        return result;
    }

    @Override
    public ListStreamsResult listStreams(ListStreamsRequest listStreamsRequest) {
        return innerListStreams(listStreamsRequest);
    }

    protected final ListStreamsResult innerListStreams(ListStreamsRequest listStreamsRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath =
                ResourcePathBuilder.standard()
                        .withProjectId(disConfig.getProjectId())
                        .withResource(new StreamResource(null, null))
                        .build();

        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        ListStreamsResult result = request(listStreamsRequest, request, ListStreamsResult.class);
        return result;
    }


    //##################### extended Method ######################
    @Override
    public PutRecordResult putRecord(PutRecordRequest putRecordParam) {
        return toPutRecordResult(putRecords(toPutRecordsRequest(putRecordParam)));
    }

    /*
     * Internal API
     */
    protected final FileUploadResult innerGetFileUploadResult(QueryFileState queryFileState) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new FileResource(queryFileState.getDeliverDataId()))
                .withResource(new StateResource(null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());

        return request(queryFileState, request, FileUploadResult.class);
    }

    public void createApp(String appName) {
        innerCreateApp(appName);
    }

    public final void innerCreateApp(String appName) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.POST);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new AppsResource(null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());
        CreateAppRequest createAppIdRequest = new CreateAppRequest();
        createAppIdRequest.setAppName(appName);
        setEndpoint(request, disConfig.getManagerEndpoint());
        request(createAppIdRequest, request, null);
    }

    public void deleteApp(String appName) {
        innerDeleteApp(appName);
    }

    public final void innerDeleteApp(String appName) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.DELETE);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new AppsResource(appName))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());
        request(null, request, null);
    }

    @Override
    public DescribeAppResult describeApp(String appName) {
        return innerDescribeApp(appName);
    }

    public final DescribeAppResult innerDescribeApp(String appName) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new AppsResource(appName))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());
        return request(null, request, DescribeAppResult.class);
    }

    @Override
    public ListAppsResult listApps(ListAppsRequest listAppsRequest) {
        return innerListApps(listAppsRequest);
    }

    public final ListAppsResult innerListApps(ListAppsRequest listAppsRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new AppsResource(null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());
        return request(listAppsRequest, request, ListAppsResult.class);
    }


    public CommitCheckpointResult commitCheckpoint(CommitCheckpointRequest commitCheckpointParam) {
        return innerCommitCheckpoint(commitCheckpointParam);
    }

    protected final CommitCheckpointResult innerCommitCheckpoint(CommitCheckpointRequest commitCheckpointParam) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.POST);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new CheckPointResource(null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());
        return request(commitCheckpointParam, request, CommitCheckpointResult.class);
    }

    public GetCheckpointResult getCheckpoint(GetCheckpointRequest getCheckpointRequest) {
        return innerGetCheckpoint(getCheckpointRequest);
    }

    protected final GetCheckpointResult innerGetCheckpoint(GetCheckpointRequest getCheckpointRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new CheckPointResource(null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());
        return request(getCheckpointRequest, request, GetCheckpointResult.class);
    }


    public DeleteCheckpointResult deleteCheckpoint(DeleteCheckpointRequest deleteCheckpointRequest) {
        return innerDeleteCheckpoint(deleteCheckpointRequest);
    }

    protected final DeleteCheckpointResult innerDeleteCheckpoint(DeleteCheckpointRequest deleteCheckpointRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.DELETE);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new CheckPointResource(null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getEndpoint());
        return request(deleteCheckpointRequest, request, DeleteCheckpointResult.class);
    }

    @Override
    public ListStreamConsumingStateResult listStreamConsumingState(ListStreamConsumingStateRequest listStreamConsumingStateRequest) {
        return innerListStreamConsumingState(listStreamConsumingStateRequest);
    }

    protected ListStreamConsumingStateResult innerListStreamConsumingState(ListStreamConsumingStateRequest listStreamConsumingStateRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new AppsResource(listStreamConsumingStateRequest.getAppName()))
                .withResource(new StreamResource(listStreamConsumingStateRequest.getStreamName()))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());
        return request(listStreamConsumingStateRequest, request, ListStreamConsumingStateResult.class);
    }

    @Override
    public UpdateStreamResult updateStream(UpdateStreamRequest updateStreamRequest) {
        return innerUpdateStream(updateStreamRequest);
    }

    protected UpdateStreamResult innerUpdateStream(UpdateStreamRequest updateStreamRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.PUT);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(StreamResource.DEFAULT_RESOURCE_NAME, updateStreamRequest.getStreamName(), "update"))
                .build();

        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());
        return request(updateStreamRequest, request, UpdateStreamResult.class);
    }

    @Override
    public void updateCredentials(DISCredentials credentials) {
        super.innerUpdateCredentials(credentials);
    }

    //@Override
    public void updateAuthToken(String authToken) {
        super.innerUpdateAuthToken(authToken);
    }

    // ------------ITransferTaskService------------
    public CreateTransferTaskResult createTransferTask(CreateTransferTaskRequest createTransferTaskRequest) {
        return innerCreateTransferTask(createTransferTaskRequest);
    }

    public final CreateTransferTaskResult innerCreateTransferTask(CreateTransferTaskRequest createTransferTaskRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.POST);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(null, createTransferTaskRequest.getStreamName()))
                .withResource(new TransferTaskResource(null, null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        CreateTransferTaskResult result = request(createTransferTaskRequest, request, CreateTransferTaskResult.class);

        return result;
    }

    public UpdateTransferTaskResult updateTransferTask(UpdateTransferTaskRequest updateTransferTaskRequest) {
        return innerUpdateTransferTask(updateTransferTaskRequest);
    }

    public final UpdateTransferTaskResult innerUpdateTransferTask(UpdateTransferTaskRequest updateTransferTaskRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.PUT);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(null, updateTransferTaskRequest.getStreamName()))
                .withResource(new TransferTaskResource(null, null))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        UpdateTransferTaskResult result = request(updateTransferTaskRequest, request, UpdateTransferTaskResult.class);

        return result;
    }

    public DeleteTransferTaskResult deleteTransferTask(DeleteTransferTaskRequest deleteTransferTaskRequest) {
        return innerDeleteTransferTask(deleteTransferTaskRequest);
    }

    public final DeleteTransferTaskResult innerDeleteTransferTask(DeleteTransferTaskRequest deleteTransferTaskRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.DELETE);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(null, deleteTransferTaskRequest.getStreamName()))
                .withResource(new TransferTaskResource(null, deleteTransferTaskRequest.getTransferTaskName()))
                .build();

        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        DeleteTransferTaskResult result = request(deleteTransferTaskRequest, request, DeleteTransferTaskResult.class);
        return result;
    }

    @Override
    public DescribeTransferTaskResult describeTransferTask(DescribeTransferTaskRequest describeTransferTaskRequest) {
        return innerDescribeTransferTask(describeTransferTaskRequest);
    }

    public final DescribeTransferTaskResult innerDescribeTransferTask(
            DescribeTransferTaskRequest describeTransferTaskRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(null, describeTransferTaskRequest.getStreamName()))
                .withResource(new TransferTaskResource(null, describeTransferTaskRequest.getTransferTaskName()))
                .build();
        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        DescribeTransferTaskResult result =
                request(describeTransferTaskRequest, request, DescribeTransferTaskResult.class);

        return result;
    }

    @Override
    public ListTransferTasksResult listTransferTasks(ListTransferTasksRquest listTransferTasksRquest) {
        return innerListTransferTasks(listTransferTasksRquest);
    }

    public final ListTransferTasksResult innerListTransferTasks(ListTransferTasksRquest listTransferTasksRquest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.GET);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(null, listTransferTasksRquest.getStreamName()))
                .withResource(new TransferTaskResource(null, null))
                .build();

        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        ListTransferTasksResult result = request(listTransferTasksRquest, request, ListTransferTasksResult.class);
        return result;
    }

    @Override
    public BatchTransferTaskResult batchTransferTask(BatchTransferTaskRequest batchTransferTaskRequest) {
        return innerBatchTransferTask(batchTransferTaskRequest);
    }

    public final BatchTransferTaskResult innerBatchTransferTask(BatchTransferTaskRequest batchTransferTaskRequest) {
        Request<HttpRequest> request = new DefaultRequest<>(Constants.SERVICENAME);
        request.setHttpMethod(HttpMethodName.POST);

        final String resourcePath = ResourcePathBuilder.standard()
                .withProjectId(disConfig.getProjectId())
                .withResource(new StreamResource(null, batchTransferTaskRequest.getStreamName()))
                .withResource(new TransferTaskResource(TransferTaskResource.DEFAULT_RESOURCE_NAME, null, "action"))
                .build();

        request.setResourcePath(resourcePath);
        setEndpoint(request, disConfig.getManagerEndpoint());

        BatchTransferTaskResult result = request(batchTransferTaskRequest, request, BatchTransferTaskResult.class);
        return result;
    }
}
