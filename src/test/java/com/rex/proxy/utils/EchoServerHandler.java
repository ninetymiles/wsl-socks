/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.rex.proxy.utils;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler implementation for the echo server.
 */
@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger sLogger = LoggerFactory.getLogger(EchoServerHandler.class);

    private EchoServer.ChildListener mListener;

    public EchoServerHandler(EchoServer.ChildListener listener) {
        sLogger.trace("");
        mListener = listener;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        //sLogger.debug("Channel {} / {} - {} msg=<{}>", ctx.channel(), ctx.channel().localAddress(), ctx.channel().remoteAddress(), msg);
        if (mListener != null) {
            mListener.onRead(ctx.channel(), msg);
        }
        ctx.writeAndFlush(msg);
        if (mListener != null) {
            mListener.onWrite(ctx.channel(), msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        sLogger.warn("Channel caught exception - {}", cause.getMessage());
        ctx.close();
    }
}
