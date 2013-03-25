package com.senseidb.zu.finagle.client;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.thrift.protocol.TBinaryProtocol;

import com.senseidb.zu.finagle.rpc.ZuThriftService;
import com.senseidb.zu.finagle.rpc.ZuTransport;
import com.senseidb.zu.finagle.serialize.ZuSerializer;
import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.thrift.ThriftClientFramedCodec;
import com.twitter.finagle.thrift.ThriftClientRequest;
import com.twitter.util.Duration;
import com.twitter.util.Future;


public final class ZuClientService{
  
  private Map<String, ZuSerializer<?,?>> serializerMap = new HashMap<String, ZuSerializer<?,?>>();
  
  private final ZuThriftService.ServiceIface svc;
  
  public ZuClientService(InetSocketAddress addr, long timeoutInMillis, int numThreads) {
    Service<ThriftClientRequest, byte[]> client = ClientBuilder.safeBuild(ClientBuilder.get()
        .hosts(addr)
    .codec(ThriftClientFramedCodec.get())
    .requestTimeout(Duration.apply(timeoutInMillis, TimeUnit.MILLISECONDS))
    .hostConnectionLimit(numThreads));
    svc = new ZuThriftService.ServiceToClient(client, new TBinaryProtocol.Factory());
  }
  
  public <Req, Res> void registerSerializer(String name, ZuSerializer<Req, Res> serializer){
    serializerMap.put(name, serializer);
  }
  
  @SuppressWarnings("unchecked")
  public <Req, Res> Future<Res> service(String name, Req req){
    ZuSerializer<Req, Res> serializer = (ZuSerializer<Req, Res>)serializerMap.get(name);
    
    if (serializer == null) {
      return Future.exception(new IllegalArgumentException("unrecognized serializer: "+name));
    }
    
    try {
      ZuTransport reqTransport = new ZuTransport();
      reqTransport.setName(name);
      reqTransport.setData(serializer.serializeRequest(req));
      Future<ZuTransport> future = svc.send(reqTransport);
    
      ZuTransport resTransport = future.apply();
    
      Res resp = serializer.deserializeResponse(resTransport.data);
    
      return Future.value(resp);
    }
    catch(Exception e) {
      return Future.exception(e);
    }
  }
}