package com.zxs.jin.https;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.zxs.jin.init.JinContext;
import com.zxs.jin.init.JinMethod;
import com.zxs.jin.util.PrintLog;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.MixedAttribute;
import io.netty.util.CharsetUtil;

public class HttpsSeverHandler extends ChannelInboundHandlerAdapter {
	
	private HttpsEngine https;
	private static ScheduledExecutorService executor;
	private static ScheduledExecutorService requestExeecutor;
	
	public HttpsSeverHandler(HttpsEngine https) {
		this.https = https;
		executor = Executors.newScheduledThreadPool(20);
		requestExeecutor = Executors.newScheduledThreadPool(20);
	}
	
	@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
        	DoRequest d = new DoRequest(ctx, msg);
        	requestExeecutor.schedule(d, 0, TimeUnit.MILLISECONDS);
        }
    }
	
	private class DoRequest implements Runnable{
		private ChannelHandlerContext ctx;
		private Object msg;
		
		public DoRequest(ChannelHandlerContext ctx, Object msg) {
			this.ctx = ctx;
			this.msg = msg;
		}

		@Override
		public void run() {
			try {
				FullHttpRequest request = (FullHttpRequest) msg;
	            boolean keepaLive = HttpUtil.isKeepAlive(request);
	            String url = request.uri();
	            String method = request.method().name();
	            String addr = ((InetSocketAddress)ctx.channel().remoteAddress()).getAddress().getHostAddress();
	            PrintLog.log("===> from %s , method = %s ,ask for %s",addr,method,url);
	            JinContext c = new JinContext(request);
	            
	            if (url.indexOf("?") != -1) {
	            	c.addUrlParams(url);
					url = url.split("[?]")[0];
				}
	            
	            FullHttpResponse httpResponse;
	            String result = "";
	            JinMethod jm = https.getMethod(method,url.replaceFirst("/", ""),c);
	            RunExec exec = new RunExec(c, jm);
	            executor.schedule(exec, 1, TimeUnit.MILLISECONDS);
	            httpResponse = exec.getResult();
	            result = c.getResult();
	            PrintLog.log("<=== result:%s",result);
	            httpResponse.content().writeBytes(result.getBytes());
	            httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, c.getResultType());
	            httpResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
	            if (keepaLive) {
	                httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
	                ctx.writeAndFlush(httpResponse);
	            } else {
	                ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
	            }
			} catch (Exception e) {
				e.printStackTrace();
			}finally {
				ctx.close();
			}
		}
	}
	 
	 private class RunExec implements Runnable {
		private JinContext rc;
		private JinMethod method;
		private FullHttpResponse response;
		private boolean ok = false;
		public RunExec(JinContext rc,JinMethod method) {
			 this.rc = rc;
			 this.method = method;
		}
		 
		public FullHttpResponse getResult() throws InterruptedException {
			while (!ok) {
				Thread.sleep(1);
			}
			return response;
		} 

		public void run() {
			if (null == method) {
				rc.json(404, "404 bad request");
			}else {
				method.exec(rc);
				response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(rc.getState() == 0?200:rc.getState()));
			}
			ok = true;
		}
	}
	 
}
