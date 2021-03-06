package io.github.mxd888.socket.plugins;

import io.github.mxd888.socket.NetMonitor;
import io.github.mxd888.socket.Packet;
import io.github.mxd888.socket.StateMachineEnum;
import io.github.mxd888.socket.buffer.VirtualBuffer;
import io.github.mxd888.socket.core.ChannelContext;

/**
 * aio-socket 插件接口
 *
 * @author MDong
 * @version 2.10.1.v20211002-RELEASE
 */
public interface Plugin extends NetMonitor {

    /**
     * 对请求消息进行预处理，并决策是否进行后续的MessageProcessor处理。
     * 若返回false，则当前消息将被忽略。
     * 若返回true，该消息会正常秩序MessageProcessor.process.
     *
     * @param channelContext 通道上下文
     * @param packet         消息包
     * @return               是否在本服务器进行处理逻辑
     */
    boolean preProcess(ChannelContext channelContext, Packet packet);

    /**
     * 解码前处理
     *
     * @param readBuffer     待解码的比特流数据
     * @param channelContext 用户通道
     * @return               解码完成的数据包
     */
    Packet preDecode(VirtualBuffer readBuffer, ChannelContext channelContext);

    /**
     * 编码前处理
     *
     * @param packet         待编码的数据包
     * @param channelContext 用户通道
     * @return               编码完成后的比特流
     */
    VirtualBuffer preEncode(Packet packet, ChannelContext channelContext);

    /**
     * 监听状态机事件
     *
     * @param stateMachineEnum 机器状态
     * @param channelContext   通道上下文
     * @param throwable        异常处理
     * @see io.github.mxd888.socket.intf.AioHandler#stateEvent(ChannelContext, StateMachineEnum, Throwable)
     */
    void stateEvent(StateMachineEnum stateMachineEnum, ChannelContext channelContext, Throwable throwable);

}
