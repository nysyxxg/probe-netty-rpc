package com.felix.rpc.framework.client.netty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.felix.rpc.framework.common.dto.RpcRequest;
import com.felix.rpc.framework.common.dto.RpcResponse;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

public class RpcClient extends SimpleChannelInboundHandler<RpcResponse> {

	private Logger logger = LoggerFactory.getLogger(RpcClient.class);

	// RPC服务端的地址
	private String host;
	// RPC服务端的端口号
	private int port;
	// RPCResponse响应对象
	private RpcResponse rpcResponse;

	public RpcClient(String host, int port) {
		this.host = host;
		this.port = port;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, RpcResponse rpcResponse) throws Exception {
		logger.info("从Rpc服务端接收到响应,相应数据为:{}", rpcResponse);
		this.rpcResponse = rpcResponse;
		// 关闭与服务端的连接，这样就可以执行f.channel().closeFuture().sync();之后的代码，即优雅退出
		// 相当于是主动关闭连接
		ctx.close();
	}

	/**
	 * 向RPC服务端发送请求方法
	 *
	 * @param request RPC客户端向RPC服务端发送的request对象
	 * @return
	 */
	public RpcResponse sendRequest(RpcRequest rpcRequest) throws Exception {
		// 配置客户端NIO线程组
		EventLoopGroup group = new NioEventLoopGroup();
		try {
			Bootstrap b = new Bootstrap();
			b.group(group).channel(NioSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1024)
					.option(ChannelOption.TCP_NODELAY, true)
					// 设置TCP连接超时时间
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
					.handler(new ClientChannlInitializer(host, port));
			// 发起异步连接操作（注意服务端是bind，客户端则需要connect）
			logger.info("准备发起异步连接操作[{}:{}]", host, port);
			ChannelFuture f = b.connect(host, port).sync();
			// 向RPC服务端发起请求
			logger.info("准备向RPC服务端发起请求");
			f.channel().writeAndFlush(rpcRequest);

			// 需要注意的是，如果没有接收到服务端返回数据，那么会一直停在这里等待
			// 等待客户端链路关闭
			logger.info("准备等待客户端链路关闭");
			f.channel().closeFuture().sync();
		} finally {
			// 优雅退出，释放NIO线程组
			logger.info("优雅退出，释放NIO线程组");
			group.shutdownGracefully();
		}
		return rpcResponse;
	}

}
