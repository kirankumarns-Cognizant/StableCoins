package com.cognizant.stablecoins.blockchain.service;
import com.cognizant.stablecoins.blockchain.config.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real loopback HTTP/JSON transport, with synthetic chain data and no public RPC or signing. */
class ReceiptRpcCompatibilityTest {
 @Test void web3jAndSpringJacksonCoexistAndRawReceiptPreservesBaseFees() throws Exception {
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var json=new ObjectMapper();String hash="0x"+"a".repeat(64);
  server.createContext("/",exchange->{try{
   var request=json.readTree(exchange.getRequestBody());String result=request.path("method").asText().equals("eth_chainId")?"\"0x14a34\"":"{\"transactionHash\":\""+hash+"\",\"status\":\"0x1\",\"blockNumber\":\"0x64\",\"gasUsed\":\"0x5208\",\"effectiveGasPrice\":\"0x2\",\"l1Fee\":\"0x64\",\"operatorFee\":\"0x3\"}";
   byte[] response=("{\"jsonrpc\":\"2.0\",\"id\":"+request.path("id")+",\"result\":"+result+"}").getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);
  }finally{exchange.close();}});server.start();
  Web3j web3j=null;RealTestnetBlockchainAdapter adapter=null;
  try {
   String url="http://127.0.0.1:"+server.getAddress().getPort()+"/";var props=new BlockchainProperties();props.setRpcUrl(url);props.setChainId(84532L);
   web3j=Web3j.build(new HttpService(url));var signer=mock(WalletSigningService.class);adapter=new RealTestnetBlockchainAdapter(web3j,signer,props);
   assertThat(web3j.ethChainId().send().getChainId().longValueExact()).isEqualTo(84532L);
   var receipt=adapter.getReceipt(hash).orElseThrow();assertThat(receipt.isFeeComponentsComplete()).isTrue();assertThat(receipt.getAdditionalFeeWei()).isEqualTo(java.math.BigInteger.valueOf(103));assertThat(receipt.getTotalFeeWei()).isEqualTo(java.math.BigInteger.valueOf(42103));verifyNoInteractions(signer);
  }finally{if(adapter!=null)adapter.closeReceiptRpc();if(web3j!=null)web3j.shutdown();server.stop(0);}
 }
}
