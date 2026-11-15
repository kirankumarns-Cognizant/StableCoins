package com.cognizant.stablecoins.blockchain.service;
import com.cognizant.stablecoins.blockchain.config.BlockchainProperties;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class SigningBoundaryTest {
    @Test void realAdapterUsesProtectedSignerInterfaceWithoutAccessingCredentials() {
        var signer=mock(WalletSigningService.class);var rpc=mock(Web3j.class);var props=new BlockchainProperties();
        byte[] signed={1,2,3};when(signer.signTransaction(any(RawTransaction.class),eq(84532L))).thenReturn(signed);
        var adapter=new RealTestnetBlockchainAdapter(rpc,signer,props);
        assertThat(adapter.signRawTransaction(1L,"0x"+"1".repeat(40),props.getTokenContract(),BigDecimal.ONE,6,
            new BigDecimal("78000"),new BigDecimal("1200000000"))).isEqualTo(signed);
        verify(signer).signTransaction(any(RawTransaction.class),eq(84532L));verifyNoInteractions(rpc);
    }
}
