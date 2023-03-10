/*
 * Copyright 2002-2012 the original author or authors.
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

package com.g42cloud.dis.http.exception;

import java.io.IOException;

/**
 * Exception thrown when an I/O error occurs.
 *
 * @since 1.3.0
 */
public class ResourceAccessException extends RestClientException {


    private static final long serialVersionUID = 8192872158769215902L;

    /**
	 * Construct a new {@code ResourceAccessException} with the given message.
	 * @param msg the message
	 */
	public ResourceAccessException(String msg) {
		super(msg);
	}

	/**
	 * Construct a new {@code ResourceAccessException} with the given message and {@link IOException}.
	 * @param msg the message
	 * @param ex the {@code IOException}
	 */
	public ResourceAccessException(String msg, IOException ex) {
		super(msg, ex);
	}

}
