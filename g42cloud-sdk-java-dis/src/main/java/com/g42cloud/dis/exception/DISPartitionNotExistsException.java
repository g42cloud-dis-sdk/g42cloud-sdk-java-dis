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

package com.g42cloud.dis.exception;

import com.g42cloud.dis.Constants;

/**
 * 分区不存在异常
 * @see Constants#HTTP_CODE_BAD_REQUEST
 * @see Constants#ERROR_CODE_PARTITION_NOT_EXISTS
 */
public class DISPartitionNotExistsException extends DISClientException
{
    public DISPartitionNotExistsException(String message)
    {
        super(message);
    }

    public DISPartitionNotExistsException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public DISPartitionNotExistsException(Throwable cause)
    {
        super(cause);
    }
}
