/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.fluss.compression;

import com.alibaba.fluss.shaded.arrow.org.apache.arrow.memory.ArrowBuf;
import com.alibaba.fluss.shaded.arrow.org.apache.arrow.memory.BufferAllocator;
import com.alibaba.fluss.shaded.arrow.org.apache.arrow.vector.compression.AbstractCompressionCodec;
import com.alibaba.fluss.shaded.arrow.org.apache.arrow.vector.compression.CompressionUtil;

import com.github.luben.zstd.Zstd;

/* This file is based on source code of Apache Arrow-java Project (https://github.com/apache/arrow-java), licensed by
 * the Apache Software Foundation (ASF) under the Apache License, Version 2.0. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership. */

/** Arrow Compression codec for the Zstd algorithm. */
public class ZstdArrowCompressionCodec extends AbstractCompressionCodec {
    private static final int DEFAULT_COMPRESSION_LEVEL = 3;
    private final int compressionLevel;

    public ZstdArrowCompressionCodec() {
        this.compressionLevel = DEFAULT_COMPRESSION_LEVEL;
    }

    public ZstdArrowCompressionCodec(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    @Override
    protected ArrowBuf doCompress(BufferAllocator allocator, ArrowBuf uncompressedBuffer) {
        long maxSize = Zstd.compressBound(uncompressedBuffer.writerIndex());
        long dstSize = CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH + maxSize;
        ArrowBuf compressedBuffer = allocator.buffer(dstSize);
        long bytesWritten =
                Zstd.compressUnsafe(
                        compressedBuffer.memoryAddress()
                                + CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH,
                        dstSize,
                        /*src*/ uncompressedBuffer.memoryAddress(),
                        /*srcSize=*/ uncompressedBuffer.writerIndex(),
                        /*level=*/ this.compressionLevel);
        if (Zstd.isError(bytesWritten)) {
            compressedBuffer.close();
            throw new RuntimeException("Error compressing: " + Zstd.getErrorName(bytesWritten));
        }
        compressedBuffer.writerIndex(CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH + bytesWritten);
        return compressedBuffer;
    }

    @Override
    protected ArrowBuf doDecompress(BufferAllocator allocator, ArrowBuf compressedBuffer) {
        long decompressedLength = readUncompressedLength(compressedBuffer);
        ArrowBuf uncompressedBuffer = allocator.buffer(decompressedLength);
        long decompressedSize =
                Zstd.decompressUnsafe(
                        uncompressedBuffer.memoryAddress(),
                        decompressedLength,
                        /*src=*/ compressedBuffer.memoryAddress()
                                + CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH,
                        compressedBuffer.writerIndex()
                                - CompressionUtil.SIZE_OF_UNCOMPRESSED_LENGTH);
        if (Zstd.isError(decompressedSize)) {
            uncompressedBuffer.close();
            throw new RuntimeException(
                    "Error decompressing: " + Zstd.getErrorName(decompressedLength));
        }
        if (decompressedLength != decompressedSize) {
            uncompressedBuffer.close();
            throw new RuntimeException(
                    "Expected != actual decompressed length: "
                            + decompressedLength
                            + " != "
                            + decompressedSize);
        }
        uncompressedBuffer.writerIndex(decompressedLength);
        return uncompressedBuffer;
    }

    @Override
    public CompressionUtil.CodecType getCodecType() {
        return CompressionUtil.CodecType.ZSTD;
    }
}
