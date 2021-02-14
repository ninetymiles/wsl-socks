package com.rex.proxy.socks.v5;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;

/**
 * https://www.ietf.org/rfc/rfc1928.txt
 * 7. Procedure for UDP-based clients
 * +----+------+------+----------+----------+----------+
 * |RSV | FRAG | ATYP | DST.ADDR | DST.PORT |   DATA   |
 * +----+------+------+----------+----------+----------+
 * | 2  |  1   |  1   | Variable |    2     | Variable |
 * +----+------+------+----------+----------+----------+
 */
public class Socks5UdpRelayMessage {

    public short rsv;
    public byte frag;
    public Socks5AddressType dstAddrType;
    public String dstAddr;
    public int dstPort;
    public ByteBuf data;

    public static class Builder {
        private final Socks5UdpRelayMessage message = new Socks5UdpRelayMessage();
        public Builder rsv(short rsv) {
            message.rsv = rsv;
            return this;
        }
        public Builder frag(byte frag) {
            message.frag = frag;
            return this;
        }
        public Builder dstAddrType(Socks5AddressType type) {
            message.dstAddrType = type;
            return this;
        }
        public Builder dstAddr(String addr) {
            message.dstAddr = addr;
            return this;
        }
        public Builder dstPort(int port) {
            message.dstPort = port;
            return this;
        }
        public Builder data(ByteBuf data) {
            message.data = data;
            return this;
        }
        public Socks5UdpRelayMessage build() {
            return message;
        }
    }
}
