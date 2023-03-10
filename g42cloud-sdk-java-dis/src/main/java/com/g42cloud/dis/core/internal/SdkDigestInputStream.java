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

package com.g42cloud.dis.core.internal;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import com.g42cloud.dis.core.util.IOUtils;

/**
 * Base class for Java SDK specific {@link java.security.DigestInputStream}.
 */
public class SdkDigestInputStream extends DigestInputStream implements Releasable {
    private static final int SKIP_BUF_SIZE = 2*1024;
    public SdkDigestInputStream(InputStream stream, MessageDigest digest) {
        super(stream, digest);
    }


    // https://github.com/aws/aws-sdk-java/issues/232
    /**
     * Skips over and discards <code>n</code> bytes of data from this input
     * stream, while taking the skipped bytes into account for digest
     * calculation. The <code>skip</code> method may, for a variety of reasons,
     * end up skipping over some smaller number of bytes, possibly
     * <code>0</code>. This may result from any of a number of conditions;
     * reaching end of file before <code>n</code> bytes have been skipped is
     * only one possibility. The actual number of bytes skipped is returned. If
     * <code>n</code> is negative, no bytes are skipped.
     *
     * <p>
     * The <code>skip</code> method of this class creates a byte array and then
     * repeatedly reads into it until <code>n</code> bytes have been read or the
     * end of the stream has been reached. Subclasses are encouraged to provide
     * a more efficient implementation of this method. For instance, the
     * implementation may depend on the ability to seek.
     *
     * @param n
     *            the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     * @exception java.io.IOException
     *                if the stream does not support seek, or if some other I/O
     *                error occurs.
     */
    @Override
    public final long skip(final long n) throws IOException {
        if (n <= 0)
            return n;
        byte[] b = new byte[(int)Math.min(SKIP_BUF_SIZE, n)];
        long m = n; // remaining number of bytes to read
        while (m > 0) {
            int len = read(b, 0, (int)Math.min(m, b.length));
            if (len == -1)
                return (m == n) ? -1 : (n - m);
            m -= len;
        }
        assert (m == 0);
        return n;
    }

    @Override
    public final void release() {
        // Don't call IOUtils.release(in, null) or else could lead to infinite loop
        IOUtils.closeQuietly(this, null);
        if (in instanceof Releasable) {
            // This allows any underlying stream that has the close operation
            // disabled to be truly released
            Releasable r = (Releasable)in;
            r.release();
        }
    }
}