package com.rex.proxy.websocket.control;

import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.List;

/**
 * Codec to convert inbound TextWebSocketFrame as ControlMessage
 * And convert outbound ControlMessage as TextWebSocketFrame
 */
public class WsProxyControlCodec extends MessageToMessageCodec<TextWebSocketFrame, ControlMessage> {

    private final Gson mCodec = new Gson();

    @Override
    protected void encode(ChannelHandlerContext ctx, ControlMessage msg, List<Object> out) throws Exception {
        out.add(new TextWebSocketFrame(mCodec.toJson(msg)));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame msg, List<Object> out) throws Exception {
        out.add(mCodec.fromJson(msg.text(), ControlMessage.class));
    }
}
