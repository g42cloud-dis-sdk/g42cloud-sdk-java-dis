package com.g42cloud.dis;

import com.g42cloud.dis.core.DISCredentials;
import com.g42cloud.dis.iface.app.IAppService;
import com.g42cloud.dis.iface.data.IDataService;
import com.g42cloud.dis.iface.data.request.PutRecordRequest;
import com.g42cloud.dis.iface.data.response.PutRecordResult;
import com.g42cloud.dis.iface.stream.IStreamService;
import com.g42cloud.dis.iface.transfertask.ITransferTaskService;

/**
 * SDK定义的API，可以理解为对RESTAPI的高层次封装
 * 
 * 1、注册用户ak和sk
 * 2、上传数据和下载数据功能
 * 
 * 
 */
public interface DIS extends IDataService, IStreamService, IAppService, ITransferTaskService
{
    
	/**
	 * 上传单条记录到通道
	 * 
	 * @param putRecordParam
	 *            Represents the input for <code>PutRecord</code>.
	 * @return Result of the PutRecord operation returned by the service.
	 */
    PutRecordResult putRecord(PutRecordRequest putRecordParam);

	/**
	 * 更新认证信息
	 * @param credentials 新的认证信息
	 */
	void updateCredentials(DISCredentials credentials);
}
