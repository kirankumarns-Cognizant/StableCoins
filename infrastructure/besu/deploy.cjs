'use strict';
// Explicit one-time development token deployment. Never called by application startup.
const fs=require('node:fs');
const path=require('node:path');
const {Wallet,ContractFactory,Transaction,keccak256,getCreateAddress}=require('ethers');
const n=require('./network.cjs');
async function finish(m,rpc,state,artifact) {
  const receipt=await rpc('eth_getTransactionReceipt',[state.hash]);
  if(!receipt)return {status:'DEPLOYMENT_PENDING_OR_UNKNOWN',transactionHash:state.hash,
    next:'Inspect the recorded transaction; do not delete deployment state or submit a replacement.'};
  if(n.quantity(receipt.status)!==1n)n.fail('DEPLOYMENT_REVERTED_REVIEW_REQUIRED');
  if(n.hash(receipt.transactionHash)!==state.hash || n.address(receipt.from)!==m.treasury ||
      n.address(receipt.contractAddress)!==state.contract)n.fail('DEPLOYMENT_RECEIPT_MISMATCH');
  const block=await rpc('eth_getBlockByNumber',[receipt.blockNumber,false]);
  if(n.hash(block?.hash)!==n.hash(receipt.blockHash))n.fail('DEPLOYMENT_NOT_CANONICAL');
  const code=await rpc('eth_getCode',[state.contract,receipt.blockNumber]);
  if(keccak256(code)!==artifact.runtimeCodeHash)n.fail('DEPLOYMENT_CODE_MISMATCH');
  const token={contract:state.contract,symbol:'DEMOUSD',decimals:6,codeHash:artifact.runtimeCodeHash,
    totalSupplyUnits:n.SUPPLY.toString(),deploymentHash:state.hash,deploymentBlock:n.quantity(receipt.blockNumber).toString()};
  await n.tokenIdentity({...m,token},rpc);
  fs.writeFileSync(path.join(n.STATE,'manifest.json'),n.json({...m,token}),{mode:0o600});
  return {status:'DEMOUSD_DEPLOYED',network:'besu-local',classification:'PRIVATE_DEMO',token,
    applicationIntegration:'NOT_ENABLED'};
}
async function deploy(m,rpc,passwordFile) {
  await n.identity(m,rpc);
  if(m.token) {
    await n.tokenIdentity(m,rpc);
    return {status:'DEMOUSD_ALREADY_DEPLOYED',transactionHash:m.token.deploymentHash,transactionSubmitted:false};
  }
  const artifact=n.read(path.join(n.ROOT,'.local/besu-build/BesuDemoUSD.json'));
  // Recompile from pinned sources; refuse a changed/tampered deployment artifact.
  const compiled=require('./compile.cjs').compile();
  if(artifact.bytecode!==compiled.bytecode||artifact.runtimeCodeHash!==compiled.runtimeCodeHash||JSON.stringify(artifact.abi)!==JSON.stringify(compiled.abi))
    n.fail('BESU_BUILD_ARTIFACT_CHANGED');
  const file=path.join(n.STATE,'deployment.json');
  if(fs.existsSync(file)) {
    n.secureFile(file);
    const state=n.read(file),tx=Transaction.from(state.signedTransaction);
    if(n.hash(tx.hash)!==state.hash||tx.chainId!==BigInt(n.CHAIN_ID)||n.address(tx.from)!==m.treasury||
       state.genesisHash!==m.genesisHash||state.contract!==getCreateAddress({from:m.treasury,nonce:tx.nonce}).toLowerCase())
      n.fail('DEPLOYMENT_STATE_MISMATCH');
    // Recovery only observes the recorded hash. It does not choose another nonce or resend.
    return finish(m,rpc,state,artifact);
  }
  if(!passwordFile)n.fail('BESU_PASSWORD_FILE_REQUIRED');
  n.secureFile(passwordFile);
  const walletFile=path.join(n.WALLETS,'treasury-wallet.json');
  if(n.walletAddress(walletFile)!==m.treasury)n.fail('BESU_TREASURY_KEYSTORE_CHANGED');
  let password=fs.readFileSync(passwordFile,'utf8'),wallet;
  try { wallet=await Wallet.fromEncryptedJson(fs.readFileSync(walletFile,'utf8'),password); }
  catch { n.fail('BESU_KEYSTORE_UNLOCK_FAILED'); } finally { password=null; }
  if(wallet.address.toLowerCase()!==m.treasury)n.fail('BESU_SIGNER_MISMATCH');
  await n.identity(m,rpc); // Revalidate after potentially expensive KDF.
  const factory=new ContractFactory(artifact.abi,artifact.bytecode);
  const deployment=await factory.getDeployTransaction(m.treasury);
  const gasPrice=n.quantity(await rpc('eth_gasPrice'));
  if(gasPrice<n.GAS_PRICE)n.fail('BESU_GAS_POLICY_MISMATCH');
  const gas=n.quantity(await rpc('eth_estimateGas',[{from:m.treasury,data:deployment.data,
    gasPrice:'0x'+gasPrice.toString(16)}]));
  const gasLimit=(gas*120n+99n)/100n;
  if(n.quantity(await rpc('eth_getBalance',[m.treasury,'latest']))<gasLimit*gasPrice)n.fail('BESU_DEPLOYMENT_GAS_INSUFFICIENT');
  const nonce=n.quantity(await rpc('eth_getTransactionCount',[m.treasury,'pending']));
  if(nonce>BigInt(Number.MAX_SAFE_INTEGER))n.fail('BESU_NONCE_OUT_OF_RANGE');
  const signed=await wallet.signTransaction({type:0,chainId:n.CHAIN_ID,nonce:Number(nonce),
    gasPrice,gasLimit,value:0n,data:deployment.data});
  const state={genesisHash:m.genesisHash,hash:keccak256(signed),
    contract:getCreateAddress({from:m.treasury,nonce:Number(nonce)}).toLowerCase(),signedTransaction:signed};
  // Exclusive durable record before broadcast. Concurrent invocations cannot both submit.
  const fd=fs.openSync(file,'wx',0o600);
  try {fs.writeFileSync(fd,n.json(state));fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
  await n.identity(m,rpc);
  const returned=await rpc('eth_sendRawTransaction',[signed]);
  if(n.hash(returned)!==state.hash)n.fail('DEPLOYMENT_SUBMISSION_UNKNOWN');
  return finish(m,rpc,state,artifact);
}
module.exports={deploy,finish};
