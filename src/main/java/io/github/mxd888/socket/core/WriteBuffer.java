
package io.github.mxd888.socket.core;

import io.github.mxd888.socket.buffer.BufferPage;
import io.github.mxd888.socket.buffer.VirtualBuffer;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * 包装当前会话分配到的虚拟Buffer,提供流式操作方式
 * 首先将待发送的数据暂存到writeInBuf
 * 在执行发送，若没有人往这个通道写入数据则立刻发送
 * 否则存进待发送队列items
 *
 * @author MDong
 * @version 2.10.1.v20211002-RELEASE
 */

public final class WriteBuffer {

    /**
     * 存储已就绪待输出的数据
     */
    private final VirtualBuffer[] items;

    /**
     * 为当前 WriteBuffer 提供数据存放功能的缓存页 用于申请内存空间
     */
    private final BufferPage bufferPage;

    /**
     * 缓冲区数据刷新Function，执行发送的具体逻辑函数
     */
    private final Consumer<WriteBuffer> consumer;

    /**
     * 默认内存块大小 写操作所申请的空间初始大小，默认128字节
     */
    private final int chunkSize;

    /**
     * items 读索引位
     */
    private int takeIndex;

    /**
     * items 写索引位
     */
    private int putIndex;

    /**
     * items 中存放的缓冲数据数量
     */
    private int count;

    /**
     * 暂存当前业务正在输出的数据,输出完毕后会存放到items中
     */
    private VirtualBuffer writeInBuf;

    /**
     * 当前WriteBuffer是否已关闭
     */
    private boolean closed = false;

    WriteBuffer(BufferPage bufferPage, Consumer<WriteBuffer> consumer, int chunkSize, int capacity) {
        this.bufferPage = bufferPage;
        this.consumer = consumer;
        this.items = new VirtualBuffer[capacity];
        this.chunkSize = chunkSize;
    }

    /**
     * 获取一个虚拟空间用于编码操作
     *
     * @return 虚拟空间
     */
    VirtualBuffer newVirtualBuffer() {
        return bufferPage.allocate(chunkSize);

    }

    /**
     * 把暂存的buffer发送出去
     *
     * @param forceFlush 是否立刻发送  强制冲洗，若通道正在被使用则将数据存入items数组
     */
    private void flushWriteBuffer(boolean forceFlush) {
        if (!forceFlush && writeInBuf.buffer().hasRemaining()) {
            return;
        }
        consumer.accept(this);
        // 检查是否已经发送出去了
        if (writeInBuf == null || writeInBuf.buffer().position() == 0) {
            return;
        }
        // 有人在发送，这个消息进入等待队列  writeInBuf修改为读模式
        writeInBuf.buffer().flip();
        VirtualBuffer virtualBuffer = writeInBuf;
        writeInBuf = null;
        try {
            while (count == items.length) {
                this.wait();
                //防止因close诱发内存泄露
                if (closed) {
                    virtualBuffer.clean();
                    return;
                }
            }

            items[putIndex] = virtualBuffer;
            if (++putIndex == items.length) {
                putIndex = 0;
            }
            count++;
        } catch (InterruptedException e1) {
            throw new RuntimeException(e1);
        }
    }

    public void write(ByteBuffer buffer) {
        write(VirtualBuffer.wrap(buffer));
    }

    public synchronized void write(VirtualBuffer virtualBuffer) {
        if (writeInBuf != null && !virtualBuffer.buffer().isDirect() && writeInBuf.buffer().remaining() > virtualBuffer.buffer().remaining()) {
            writeInBuf.buffer().put(virtualBuffer.buffer());
            virtualBuffer.clean();
        } else {
            if (writeInBuf != null) {
                flushWriteBuffer(true);
            }
            virtualBuffer.buffer().compact();
            writeInBuf = virtualBuffer;
        }
        flushWriteBuffer(false);
    }

    /**
     * 刷新缓冲区，将数据发送出去
     */
    public void flush() {
        if (closed) {
            throw new RuntimeException("OutputStream has closed");
        }
        if (this.count > 0 || (writeInBuf != null && writeInBuf.buffer().position() > 0)) {
            consumer.accept(this);
        }
    }

    public synchronized void close() {
        if (closed) {
            return;
        }
        flush();
        closed = true;
        if (writeInBuf != null) {
            writeInBuf.clean();
            writeInBuf = null;
        }
        VirtualBuffer byteBuf;
        while ((byteBuf = poll()) != null) {
            byteBuf.clean();
        }
    }


    /**
     * 是否存在待输出的数据
     *
     * @return true:有,false:无
     */
    boolean isEmpty() {
        return count == 0 && (writeInBuf == null || writeInBuf.buffer().position() == 0);
    }

    /**
     * 从带输出队列获取一个待发送消息
     *
     * @return VirtualBuffer类型的消息
     */
    VirtualBuffer pollItem() {
        if (count == 0) {
            return null;
        }
        synchronized (this) {
            VirtualBuffer x = items[takeIndex];
            items[takeIndex] = null;
            if (++takeIndex == items.length) {
                takeIndex = 0;
            }
            if (count-- == items.length) {
                this.notifyAll();
            }
            return x;
        }
    }

    /**
     * 获取并移除当前缓冲队列中头部的VirtualBuffer
     *
     * @return 待输出的VirtualBuffer
     */
    synchronized VirtualBuffer poll() {
        VirtualBuffer item = pollItem();
        if (item != null) {
            return item;
        }
        if (writeInBuf != null && writeInBuf.buffer().position() > 0) {
            // 将暂存器里面的数据更改为读模式，一会将其读出来并发送
            writeInBuf.buffer().flip();
            VirtualBuffer buffer = writeInBuf;
            writeInBuf = null;
            return buffer;
        } else {
            return null;
        }
    }

}